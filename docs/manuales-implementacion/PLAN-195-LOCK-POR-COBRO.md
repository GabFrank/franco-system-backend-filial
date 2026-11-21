# Plan — lock por cobro en todos los caminos que escriben venta_tarjeta (sigue a #195)

Rama `fix/venta-tarjeta-lock-por-cobro`, sale de `fix/venta-tarjeta-cupon-completar-concurrente`
(PR #278, todavía sin mergear): reusa su `tomarBloqueo`. PR hacia `develop`; hasta que #278 se
mergee muestra también sus commits. Solo filial.

## Problema

#278 serializa `completar` contra `completar`. Los otros caminos que escriben un cobro leen y
guardan sin lock, y `VentaTarjeta` no tiene `@Version`: cruzados con un `completar` del mismo cobro,
gana el último que escribe.

| Camino (resolver) | Cruce con `completar` del mismo cobro | Resultado hoy |
|---|---|---|
| `marcarNoCompletada` (Conciliación → Dejar sin conciliar) | los dos leen PENDIENTE | NO_COMPLETADO pisa un COMPLETADO (se pierde el cupón) o al revés |
| `marcarNoCompletadas` (cierre de caja con pendientes) | idem, por caja | idem |
| `cancelarPorVenta` (venta cancelada) | `completar` lee PENDIENTE antes del commit del cancelar | un cobro de venta cancelada queda COMPLETADO |
| `reabrir` | solo desde NO_COMPLETADO; con otro `reabrir` o con `marcarNoCompletada` | dos reaperturas pisan `reabierto_por` |

`saveVentaTarjeta` solo da altas (rechaza un id existente desde #147): no compite.

## Arreglo (una fase)

En `VentaTarjetaService`:

- `leerBloqueado(id, sucursalId, mensaje)`: toma `venta-tarjeta:id:<suc>:<id>` (la misma clave de
  #278), lee y hace `entityManager.refresh(vt)`. El refresh no es decorativo: el resolver de
  `reabrir` carga el cobro **antes** (para `exigirRolParaReabrir`) y, con
  `OpenEntityManagerInViewFilter`, la relectura devolvería esa instancia con el estado viejo.
  Lo usan `completar` (reemplaza la toma + lectura de #278), `marcarNoCompletada` y `reabrir`.
- `cancelarPorVenta` y `marcarNoCompletadas` (con y sin motivo): leen la lista como hoy, toman la
  clave de cada id **en orden ascendente**, hacen `refresh` de cada uno y recién ahí deciden.
  El cierre de caja vuelve a filtrar por PENDIENTE después del refresh: uno que se completó
  mientras esperaba ya no se marca, y el conteo devuelto es el de los marcados (el desktop no lo
  usa: `adicionar-caja-dialog` solo hace `irAlCierre()`).
  - En `cancelarPorVenta` los locks y los refresh van **antes** del `try`: el `catch` de hoy
    reemplaza cualquier `RuntimeException` por «no se canceló ninguno» y taparía el aviso propio.
  - La clave se arma con `vt.getSucursalId()`, no con el `sucId` que manda el cliente
    (`cancelarVentaTarjetaPorVentaId` no pasa por `exigirSucursalPropia`).
- `EntityManager` por `@PersistenceContext`, como `PedidoService`. Cambia el constructor: el
  `setUp` del test pasa un mock.

**Interbloqueo:** los que toman varios ids (cancelar, cierre) los toman ascendentes y nunca toman
claves de cupón; `completar` toma un solo id y después sus claves de cupón. No hay ciclo posible
salvo colisión de `hashtext` (PostgreSQL aborta uno con `40P01`).

**Mensajes si la espera vence (15 s):** el del cobro de #278 para los de un id; «Hay un cobro con
tarjeta de esta venta/caja registrándose ahora mismo. Reintentá en unos segundos.» para los de
varios.

## Tests (`VentaTarjetaServiceTest`, lock y `EntityManager` mockeados)

- `marcarNoCompletada` y `reabrir`: lock del id antes de leer, y `refresh` después (`InOrder`).
- `cancelarPorVenta`: locks en orden ascendente aunque la lista venga desordenada, antes del primer
  `save`; `refresh` de cada uno.
- `marcarNoCompletadas`: un cobro que después del `refresh` ya no está PENDIENTE no se marca ni
  cuenta (con y sin motivo). El mock del `refresh` le cambia el estado (`doAnswer`): con un mock
  mudo el test pasaría sin probar nada.
- `completar`: el `refresh` entra en el `InOrder` del test de #278.
- Espera vencida en cada uno → `GraphQLException` con su texto, sin guardar.
- Revertir el fix y comprobar que fallan.

## Datos nuevos

Ninguno: sin migración, sin cambio de schema GraphQL.

## Compatibilidad y despliegue

Mismo contrato. Rollback = revertir el commit. Al mergear a `develop` sale a las filiales alpha en
≤15 min y reinicia su servicio.

## Prueba de runtime (filial propio 8082, sucursal 24, SIFEN apagado; jar de control sin el fix)

1. `completar` y `marcarVentaTarjetaNoCompletada` del mismo PENDIENTE, a la vez, varias vueltas:
   con el fix uno gana y el otro recibe «…esta COMPLETADO/NO_COMPLETADO»; el control muestra el
   pisado.
2. `completar` y `marcarVentasTarjetaNoCompletadas` de su caja, a la vez: con el fix el COMPLETADO
   no se pisa y el conteo lo excluye.
3. `completar` y `cancelarVentaTarjetaPorVentaId`, a la vez: con el fix nunca queda un COMPLETADO
   en una venta con sus cobros cancelados.
4. Dos `reabrirVentaTarjeta` del mismo cobro a la vez: uno reabre, el otro recibe «…esta PENDIENTE».
5. Por pantalla (Conciliación de cupones en el PDV): dejar un cobro sin conciliar y reabrirlo;
   cancelar una venta con tarjeta. Sin concurrencia: comprobar que los botones siguen andando.
6. Restaurar los datos de prueba.

## Auditoría del plan (paso 5)

Eje A y eje B, los dos «aceptar con reservas», sin críticos. Aplicado arriba: locks antes del `try`
de `cancelarPorVenta`, clave con la sucursal del registro, constructor y `setUp`, test de refiltrado
con `doAnswer`, `refresh` en el `InOrder` de `completar`. Verificado sin hallazgo: el schema no
cambia; mobile y PWA no llaman estas mutations; las cuatro son `@Transactional` y solo las llama su
resolver; no hay otro camino que escriba `venta_tarjeta` en el filial (sin `@Modifying` ni SQL
nativo); `refresh` con OSIV opera sobre una instancia managed; sin cascade en las relaciones; sin
ciclo con `caja-saldo` ni con el savepoint del emisor de eventos.

## Sin verificar / fuera de este PR

- **La cancelación de venta se traga el error.** El desktop llama `onCancelarPorVentaId` con
  `silentLoad=true` y en error solo hace `console.error` (`list-venta`, `generic-list-venta`,
  `ultimas-ventas-dialog`). Si la espera vence (un `completar` trabado 15 s), la venta queda
  CANCELADA y su cobro no, sin aviso. Ya pasa hoy con cualquier otro error; esto suma un camino raro.
- **`venta_tarjeta` también vuelve del central** (`central_bodega_filial24_pub` la incluye en la
  copia local). La escribe en el central `updateVentaTarjeta` (setter de la app móvil vieja) y el
  apply worker no toma advisory locks. Camino heredado, fuera de este fix.
- El cierre no ve un cobro que se reabre mientras espera (la lista se lee antes de los locks).
  Carrera ya existente.
- Una colisión de `hashtext` daría `40P01`, que `tomarBloqueo` no traduce. Improbable.
- El cierre de caja por pantalla: la caja 1185 local no cierra por datos propios (memoria
  caja-1185); se prueba por API.
- Canal alpha = una máquina (mauro).
