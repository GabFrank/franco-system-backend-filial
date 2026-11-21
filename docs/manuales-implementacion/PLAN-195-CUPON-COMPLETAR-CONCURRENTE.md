# Plan #195 — el mismo cupón completado dos veces en la misma venta

Rama `fix/venta-tarjeta-cupon-completar-concurrente`, desde `origin/develop` 3065062. Solo filial.

## Problema

Venta 81509 de Itaipú (25/10): `venta_tarjeta` 1670 y 1672 COMPLETADAS con el mismo `qr_crudo`.
El PDV (`venta-touch.component.ts#registrarPagosConTarjeta`) manda los `completarVentaTarjeta` de
cada tarjeta a la vez (`forkJoin`). `completar()` chequea el cupón contra lo ya commiteado
(`motivoCuponNoUsable`), así que las dos transacciones pasan sin ver la escritura de la otra.

En serie el segundo ya se rechaza hoy: el chequeo por `qrCrudo` excluye solo el propio id, y el de
código de autorización mira todos los COMPLETADOS de la terminal en la ventana, sin excluir la misma
venta. El hueco es solo la concurrencia.

## Arreglo

**Fase 1 — serializar el chequeo** (`VentaTarjetaService.completar`)

- Con `BloqueoTransaccionalService.tomar(clave, "15s")` (el servicio que ya usan caja, FEFO y
  factura legal), **siempre en este orden**:
  0. `venta-tarjeta:id:<sucursalId>:<id>`, **antes** de leer el registro. Así dos `completar` del
     mismo cobro (reintento, o el PDV y Conciliación a la vez) no pasan los dos el chequeo de
     PENDIENTE y el último no pisa al primero. Tiene que ir antes de la lectura: releer después
     devolvería la instancia del contexto de persistencia, con el estado viejo.
  Después de validar que es PENDIENTE y antes de `validarCuponNoUsado`, uno por cada dato del cupón
  que venga:
  1. `venta-tarjeta-cupon:qr:<qrCrudo.trim()>`
  2. `venta-tarjeta-cupon:ref:<identificadorTransaccion.trim()>`
  3. `venta-tarjeta-cupon:aut:<sucursalId>:<upper(trim(codigoAutorizacion))>`. **Sin la terminal**:
     `buscarPorCodigoAutorizacion` trata la terminal nula como comodín, y con la terminal en la clave
     un PENDIENTE sin terminal no esperaría al de la terminal real. Serializa un poco de más (mismo
     código en otra terminal de la sucursal), a cambio de no dejar ese hueco.
- El lock va en `completar`, **no** dentro de `motivoCuponNoUsable`: el pre-chequeo
  (`VentaTarjetaGraphQL.java:119`) lo llama sin transacción y `tomar` es `MANDATORY`.
- El orden fijo evita el interbloqueo: cada transacción toma a lo sumo una clave de cada tipo y
  siempre en la misma secuencia. Una colisión de `hashtext` solo hace esperar, y la espera corta a 15 s.
- **`qrCrudo` se guarda recortado** (`completar`, hoy `vt.setQrCrudo(qrCrudo)` crudo). El chequeo
  busca `qrCrudo.trim()`: si el lector agrega `\r`/`\n` al final, lo guardado nunca coincide con lo
  buscado y el duplicado pasa aunque esté serializado, incluso en serie.
- Con el lock, el segundo `completar` espera el commit del primero; READ COMMITTED le da una foto
  nueva en cada consulta, así que `motivoCuponNoUsable` lo ve y rechaza con el texto que ya existe
  («Ese cupon ya fue registrado en la venta con tarjeta N (venta V)…»).
- Si la espera vence (`55P03`, `LoteFefoService.esEsperaVencida`): `GraphQLException` «Otro cobro con
  ese cupón se está registrando ahora mismo. Reintentá en unos segundos.». El cobro queda PENDIENTE y
  se completa después desde Conciliación de cupones.
- `private final BloqueoTransaccionalService bloqueo;` se declara junto a las otras dependencias
  `final` (después de `capturas`): pasa a ser el 6.º argumento del `@AllArgsConstructor`, y el
  `setUp` de `VentaTarjetaServiceTest` pasa un mock ahí (un `null` daría NPE en los tests de
  `completar` que ya existen).

Tests (`VentaTarjetaServiceTest`, con el lock mockeado):
- toma la clave del id **antes** de leer el registro, y las de qr, referencia y código, en ese
  orden, **antes** de la primera consulta de duplicado (`InOrder`);
- la clave de código no lleva terminal (la misma clave con y sin terminal);
- sin datos de cupón, solo toma la clave del id;
- un registro no PENDIENTE toma la del id y ninguna de cupón;
- espera vencida → `GraphQLException` con el texto de reintentar, sin guardar nada;
- `qrCrudo` con `\n` final se guarda recortado.
- Revertir el fix y comprobar que fallan.

La carrera real no se prueba con mocks: la cubre la prueba de runtime.

## Lo que NO se hace, y por qué

- **Punto 2 de la issue (validar dentro de la misma venta).** Con el chequeo serializado, el `qrCrudo`
  y el código de autorización repetidos ya se rechazan aunque sean de la misma venta. La referencia
  (`identificadorTransaccion`) excluye **a propósito** las líneas de la propia venta, porque el PDV ya
  la escribió en el `saveVenta`. Como `venta_tarjeta` no guarda la referencia, no hay con qué compararla
  sin cambiar ese contrato. En la práctica la referencia sale del QR, que trae `qrCrudo`, así que el
  caso real (Itaipú) queda cubierto. El hueco que queda es la misma referencia tipeada a mano en dos
  líneas de una venta: se anota, no se arregla acá.
- **Punto 3 (índice único sobre `qr_crudo`).** Requiere pasar a CANCELADO las `venta_tarjeta` de una
  venta cancelada y limpiar duplicados en las 24 filiales. Es otra migración, sobre una tabla
  BRANCH_TO_MAIN; queda fuera de este fix.
- **Datos de 1670/1672.** Necesitan el cupón físico: se corrigen a mano, no por código.
- **La pantalla** (`pago-touch`, el mismo cupón en dos líneas de un cobro): issue desktop #625.

## Datos nuevos

Ninguno: sin columnas, sin migración, sin cambio de schema GraphQL.

## Compatibilidad y despliegue

- Sin cambio de contrato: el mismo `completarVentaTarjeta`, los mismos argumentos. Ningún desktop
  cambia.
- Sin migración. Rollback = revertir el commit.
- Al mergear a `develop` sale solo a las filiales alpha en ≤15 min y reinicia su servicio.

## Prueba de runtime (filial propio, 8082, sucursal 24, SIFEN apagado)

1. Por SQL: una venta de la 24 con dos `venta_tarjeta` PENDIENTE de la misma terminal.
2. Dos `completarVentaTarjeta` **a la vez** (dos curl en paralelo) con el mismo `qrCrudo`, uno de
   ellos con `\n` final: uno COMPLETADO y el otro rechazado con «Ese cupon ya fue registrado…»; en
   base, un solo COMPLETADO y el `qr_crudo` sin el salto.
2b. Dos `completar` a la vez del **mismo** id con cupones distintos: uno completa, el otro recibe
   «…esta en estado COMPLETADO».
3. Lo mismo sin `qrCrudo`, con el mismo código de autorización y monto (carga manual).
4. Dos cupones distintos a la vez: los dos COMPLETADOS (el lock no frena cobros buenos).
5. Control: con el jar de `develop` el paso 2 deja los dos COMPLETADOS (reproduce la issue).
6. Por pantalla: PDV web contra el filial propio, venta con dos tarjetas y el mismo cupón en las dos
   líneas → una completa y la otra queda PENDIENTE con el aviso. El rechazo imprime la seña
   (`imprimirSenaCupon`): se avisa antes qué cola se usa, y se cierra imprimiendo en `frc-ticket`.
7. Restaurar los datos de prueba.

## Auditoría del plan (paso 5)

Dos auditores, eje A (contrato) y eje B (estado), los dos «aceptar con reservas». Aplicado:
- clave de código sin terminal (los dos);
- lock por id antes de leer el registro (los dos; preexistente, se cierra porque es barato);
- `qrCrudo` guardado recortado (eje B);
- el lock no va en `motivoCuponNoUsable` (eje A);
- posición del campo en el constructor y `setUp` (eje A);
- la prueba por pantalla imprime la seña (eje A).
Verificado sin hallazgo: el resolver no abre otra transacción, así que el lock se libera con el
commit del save; READ COMMITTED (no hay `isolation` configurado); el savepoint del emisor de eventos
va después del lock y no lo suelta; Apollo corta a 60 s; mobile y PWA no llaman a la mutation.

## Auditoría del diff (paso 8)

Ejes fijos 1, 2 y 3; los condicionales A y B no aplican (el diff no toca migraciones, workflows ni
replicación). Ninguno bloquea. Aplicado:
- el lock del id avisa con su propio texto («Este cobro con tarjeta se está registrando desde otra
  pantalla…»): puede vencer sin ningún cupón en juego;
- textos con tilde, como el resto de los avisos de espera («Reintentá»);
- `validarCuponNoUsado` recupera su javadoc (había quedado arriba de `bloquearCupon`);
- el comentario del recorte ya no dice que el `\r\n` fue la causa: el desktop manda el QR recortado
  desde `qr-pos-parser.ts:51`; el recorte es para cualquier otro cliente.

Verificado sin hallazgo: el resolver pasa por `exigirSucursalPropia` antes de llegar al servicio, así
que el lock se toma con la sucursal propia; la clave va como parámetro; nadie más usa las claves
`venta-tarjeta*`; ningún cliente reintenta solo un `completar` fallido; ningún consumidor de
`qr_crudo` (filial, central, desktop) compara el valor crudo.

Pendiente, fuera de esta issue:
- `marcarNoCompletada`, `reabrir` y `cancelarPorVenta` no toman el lock del id: cruzados con un
  `completar` del mismo cobro, gana el último que escribe. Se cerraría tomando
  `venta-tarjeta:id:<suc>:<id>` antes de leer (en `cancelarPorVenta`, por id ascendente).
- `tomarBloqueo` es la tercera copia del try/catch de `esEsperaVencida` (con `PdvCajaService` y
  `LoteFefoService`): candidato a un método de `BloqueoTransaccionalService`.
- Un interbloqueo por colisión cruzada de `hashtext` sale como `40P01`, no como espera vencida, y
  llega con el texto crudo. Probabilidad despreciable.

## Sin verificar

- Interbloqueo real con colisión de `hashtext`: improbable; si pasa, PostgreSQL aborta uno y el
  cobro queda PENDIENTE.
