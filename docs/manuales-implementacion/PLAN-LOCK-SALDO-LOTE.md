# Plan: serializar por sucursal la asignación de saldo por lote (#185)

Rama `fix/lotes-fefo-concurrencia` desde `develop`. Pieza: **filial** sola.

## Qué pasa

`VentaLoteService.registrarSalidaVenta` (llamado desde `VentaItemService.save` / `saveAndSend`) lee el
saldo por lote en `LoteFefoService.asignar*` y escribe el reparto con `reemplazarDesglose`. Entre la
lectura y la escritura no hay bloqueo: dos cajas que venden a la vez el mismo producto con lote leen el
mismo saldo y reparten las mismas unidades. El lote queda negativo.

Es la carrera del central #463, ya arreglada ahí (PR central #469). Este plan porta ese arreglo.

## Cambio

1. **`service/financiero/BloqueoTransaccionalService`** (nuevo, copia del central reducida a lo que se
   usa): `tomar(clave, esperaMaxima)`.
   - `select cast(pg_advisory_xact_lock(hashtext(:clave)) as varchar)` (el cast porque la función
     devuelve `void`). **`hashtext` y no `hashtextextended`** como el central: `hashtextextended` es de
     PG 11+ y no está verificada la versión de las 24 bases filiales. `hashtext` existe en todas; una
     colisión entre claves solo hace esperar, no falla.
   - Espera acotada con `set_config('lock_timeout', …, true)` solo durante el pedido; después se restaura
     el valor previo.
   - Una clave ya tomada en la transacción no se vuelve a pedir (registro colgado de una
     `TransactionSynchronization`), igual que el central: cada pedido son 4 consultas nativas que vuelcan
     la sesión, y una venta pide el saldo por cada ítem con lote.
   - `Propagation.MANDATORY`. El `tomar(clave)` sin espera del central no se porta: no tiene llamador.
2. **`LoteFefoService.bloquearSaldo(sucursalId)`**: clave `lote-saldo:<sucursalId>`, espera `15s`. Si
   vence (SQLSTATE `55P03`), `GraphQLException` «Hay otra operación con lotes en curso en la sucursal N.
   Reintentá en unos segundos.». Se llama en `asignar(…, lotesExcluidos)` y `asignarConPreferencia`
   **antes de leer** el saldo, después de validar los datos (datos inválidos no toman nada).
3. **`VentaItemGraphQL.saveVentaItem` pasa a `@Transactional`** (Spring) *(auditoría A1/B1)*. Es la
   mutation con la que el desktop agrega un ítem a un delivery (`venta-touch.component.ts:914`). Hoy no
   tiene transacción propia: los overrides de `VentaItemService` no heredan el `@javax.transaction.
   Transactional` de `CrudService` (Spring 5.1 no busca la anotación en el método padre), así que el
   `venta_item`, el `movimiento_stock` y el desglose commitean cada uno por su lado. Si la espera del lock
   vence, quedaría el ítem y la salida agregada sin desglose (`SUM(hijas) ≠ padre`), y el cajero, que no
   ve el ítem en la lista, lo carga de nuevo. Con la transacción, se revierte todo junto. Dentro de
   `saveVenta` no cambia nada: `saveVentaItemList` lo llama sin pasar por el proxy, ya dentro de la
   transacción de la venta.
4. **`DeliveryGraphQL.saveDeliveryAndVenta`** deja pasar la `GraphQLException` tal cual en vez de
   cambiarla por «Algo salió mal. Comuniquese con el soporte» *(auditoría A3/B-minor2)*: así el cajero
   lee «reintentá» y no «llamá a soporte». El rollback ya ocurría.

Clave por sucursal y no por producto: una venta con varios ítems con lote toma una sola clave, así que
dos ventas no pueden cruzarse por el orden de los ítems.

### Por qué alcanza con esos puntos (dentro del filial)

- `stockPorLote` lo leen solo `LoteFefoService`, la query GraphQL `stockPorLote` y
  `stockPorLoteEnPresentacion` (selector del POS): las dos últimas solo muestran.
- `LoteFefoService` lo llama solo `VentaLoteService.registrarSalidaVenta` (`@Transactional`). Llegan a
  él `saveVenta` (incluye crédito), `saveDeliveryAndVenta` y `saveVentaItem`. Ningún `REQUIRES_NEW`,
  scheduler ni apply worker llega a ese camino (verificado por los dos auditores).
- `registrarSalidaVenta` borra el desglose viejo **antes** de pedir el lock. No abre un ciclo: esas filas
  son del propio movimiento.
- Sin deadlock con los `synchronized` de `CrudService`: el monitor se suelta antes de
  `registrarSalidaVenta`. La venta que espera solo tiene filas que insertó ella.
- El fix depende de que la transacción de afuera sea READ COMMITTED (la lectura después del lock ve lo
  commiteado). `saveVenta` lo es; el `SERIALIZABLE` de `VentaService.save` se ignora al sumarse a una
  transacción abierta. Va al gotcha.

### Cuánto se sostiene el lock

`saveVenta` es **una sola transacción** (cobro, ítems, factura con armado y firma del DE, impresión, y en
crédito el pagaré con `closeYEsperarEnvio(5000)`). El lock se toma en el primer ítem con lote y se suelta
en el commit: la otra caja espera el resto de la primera venta, y una tercera espera las dos.
Si pasan 15 s, la venta que espera se revierte entera (cobro incluido) y el cajero reintenta; la reserva de
`VentaDuplicadaCacheService` se libera en el `finally` porque la excepción sale antes de `confirmar`.

15 s es el valor del central (debajo de los 60 s con que el desktop corta una mutation). Se mide en la
prueba (paso 6). En local SIFEN va apagado (el perfil `dev` apunta a PROD), así que el armado y firma del
DE no entra en la medición: si lo medido supera ~5 s, se sube el valor antes del PR.

**Uso de lotes en producción** (central bodega, solo lectura, 2026-10-21): 0 de 9142 productos con
`lote = true` y `movimiento_stock_lote` vacía. En bodega hoy el lock no frena ninguna venta. Farmacia no se
consultó. Si un día casi todo el catálogo de una sucursal tiene lote, en hora pico las ventas con lote se
serializan: revisar el valor entonces.

## Fases

**Fase 1 — código + tests** (un commit `fix(lotes): …`).

Tests (unitarios, sin contexto, igual que el resto del repo):

- `BloqueoTransaccionalServiceTest`: misma clave en la misma transacción → 4 consultas; otra clave → 8;
  transacción nueva → 8; sin sincronización activa → siempre.
- `LoteFefoServiceBloqueoTest`: `asignar` y `asignarConPreferencia` (con y sin preferencias) toman el lock
  **antes** de leer (`InOrder`); datos inválidos no tocan nada; si vence la espera (55P03 en la cadena de
  causas), `GraphQLException` y no lee; otro error se propaga tal cual.
- Revertir el `bloquearSaldo` en `LoteFefoService` y comprobar que `LoteFefoServiceBloqueoTest` falla.
- La transacción de `saveVentaItem` y el mensaje de delivery se prueban en runtime (no hay tests con
  contexto en este repo).

**Fase 2 — documentación**: `CLAUDE.md` del filial, gotcha corto: no pedir el lock desde un
`REQUIRES_NEW` ni teniendo filas ajenas tomadas; la transacción de afuera tiene que ser READ COMMITTED;
diagnóstico `select pid, objid from pg_locks where locktype='advisory'` contra
`hashtext('lote-saldo:N')`. El plan se borra en el último commit.

Sin migraciones, sin cambios de GraphQL, sin tablas nuevas. **Datos nuevos: ninguno.**

## Prueba de runtime (paso 9)

Filial propio en el 8082 (sucursal 24, SIFEN apagado), desktop `ng serve -c web`, usuario `MR`, caja 1185.

1. Sembrar por SQL: un producto con `lote = true`, un lote LIBERADO y 30 unidades en
   `movimiento_stock_lote` para la sucursal 24. Línea base de ids para restaurar.
2. **Carrera** por API: dos `saveVenta` en paralelo, con **cantidades distintas** (20 y 21) para que no
   las frene el control de ventas duplicadas. **Sin** el fix → saldo negativo (reproducción); **con** el
   fix → la segunda espera, toma 10 del lote y el resto SIN LOTE, saldo del lote 0.
3. **Espera vencida** (lock retenido desde `psql` con `pg_advisory_xact_lock(hashtext('lote-saldo:24'))`
   en una transacción abierta):
   - venta por pantalla → a los 15 s el mensaje de reintentar; no queda venta, ni cobro, ni movimiento;
   - agregar un ítem con lote a un delivery → mismo mensaje; no queda `venta_item` ni `movimiento_stock`
     nuevo;
   - crear un delivery con ítem con lote (`saveDeliveryAndVenta`) → el mensaje de reintentar, no «soporte»;
   - «Finalizar con factura»: anotar qué pasa con la factura emitida antes de `saveVenta` (riesgo previo
     al fix, `venta-touch.component.ts:1689`).
   Soltar el lock y reintentar → entra.
4. **Venta por pantalla** con el producto con lote, con y sin elegir lote a mano; y re-guardar por API un
   `saveVentaItem` con id existente (la edición no tiene pantalla): el desglose se rehace bien.
5. **Venta sin lote**: no pide el lock (`pg_locks` sin advisory durante la venta).
6. Medir el tiempo de una venta con lote (ítem → commit), con ticket y con factura.
7. Restaurar los datos y comparar con la línea base.

## Qué queda sin verificar

- Contención real con varias cajas y muchos productos con lote (ver «Pendiente de Franco»).
- El armado y firma del DE dentro del lock (SIFEN apagado en local).
- **Salidas por lote que escribe el central** sobre la sucursal de un filial (ajustes `ajustarStockLote`,
  salida de transferencias) y bajan por replicación: su lock vive en la base del central, así que esa
  carrera (venta del filial contra salida del central) sigue abierta. No la cierra este diseño.

## Despliegue y vuelta atrás

- Merge a `develop` → la filial alpha (mauro `:8080`) en ≤15 min, con reinicio del servicio. El bug ya
  está en beta (6 farmacias) y master (18 bodegas): el arreglo llega ahí al promover, por el camino de
  siempre.
- Vuelta atrás: **revertir el PR en `develop`** (y en la rama del canal si ya se promovió) para que salga
  un release nuevo. Copiar el jar anterior a mano no sirve: `check-update.sh` vuelve a instalar el último
  release del canal a los 15 min. En la base no hay nada que deshacer: el lock muere con la transacción y
  no hay migración.
