# Plan — control de acceso al detalle de la caja (filial)

Issue #199. Rama: `fix/caja-acceso-detalle` (desde `develop`, con #198 adentro). Pieza: **filial**.
Sin migración.

## Regla

La misma de #198: el detalle de una caja lo ve **su dueño** (`pdv_caja.usuario` = usuario del
token) o quien tenga **`ANALISIS DE CAJA`**. ADMIN pasa siempre (`RolSesionService`).

| Punto | Control |
|---|---|
| `retiroListPorCajaSalidaId`, `gastosPorCajaId`, `ventasTarjetaPorCaja`, `deliveryPorCajaIdAndEstados` | dueño o rol de la caja pedida |
| `ventasPorCajaId` | dueño o rol; con `idVenta`, el de la caja de esa venta |
| `conteo(id)` | dueño o rol de la caja que tiene ese conteo (apertura o cierre); conteo sin caja, solo el rol |
| `conteoMonedasPorConteoId`, `conteoMoneda(id)` | el de su conteo |
| `retiro(id)`, `gasto(id)`, `movimientoCaja(id)` | dueño o rol de su caja; sin caja, solo el rol |
| `retiros`, `gastos`, `gastosPorFecha`, `conteos`, `conteoMonedas`, `movimientoCajas` | rol |
| campos `conteoApertura` / `conteoCierre` de `PdvCaja` (`PdvCajaResolver`, `CajaResolver`) | dueño o rol; si no, `null` (no error, para que el proxy del central no descarte la caja) |
| `savePdvCaja`: `verificado`, `verificadoPorId`, `estado`, `conteoCierreId`, `conteoAperturaId` | cambiar cualquiera respecto de lo guardado exige el rol. Se compara valor contra valor, `null` incluido (el input pisa la entidad entera: mandar `null` sobre un valor guardado es un cambio). Caja nueva: respecto de `null` |

Una caja que no existe sigue como antes (lista vacía); una venta sin caja (`idVenta`), solo el
rol. Los controles van en la capa GraphQL; para no repetir la búsqueda en seis resolvers,
`PdvCajaService` suma `exigirAccesoACaja(cajaId)` y reemplaza al privado que #198 dejó en
`PdvCajaGraphQL`. Los campos de conteo llevan el control en los dos resolvers de `PdvCaja`
(cuál resuelve el campo depende del orden de los beans) y dejan un `log.warn` al devolver `null`.

## Clientes (mapeo contra `develop` y `master` de desktop, y `develop` de central, PWA y mobile, 2026-10-28)

- **Desktop PDV** (contra el filial): retiro, gasto, últimas ventas, delivery, conteo de cierre,
  `cajaAbiertoPorUsuarioId` y `pdvCaja` piden siempre la caja del usuario logueado.
- **Conciliación de cupones** (`VENTA_TARJETA_COMPLETAR`) abre `VentasTarjetaCajaDialog` sobre
  cajas ajenas y pide `conteoApertura` / `conteoCierre` al filial. Con este cambio llegan `null`;
  el diálogo solo mira `activo`, así que se comporta igual. Su lista sale de
  `filtrarVentasTarjetaPorCaja`, que no cambia.
- **Mobile** pide la caja propia al filial por el central, que reenvía el token del usuario
  (`pdvCajaDesdeFilial`, `cajaAbiertoPorUsuarioId`). La **PWA** lee la caja del central y solo
  pasa por el filial al abrir (`abrirCajaDesdeServidor`): el central manda `savePdvCaja` sin id,
  saca `estado` y deja `verificado`, `verificadoPorId`, `conteoCierreId` en `null`: no cambia
  nada, pasa.
- La verificación de cajas (`verificarCaja`), la lista de cajas y el análisis de diferencias van al
  central. El cierre del PDV usa `saveConteo`, no `savePdvCaja`.
- `conteo(id)`, `ventasTarjetaPorCaja` y las consultas generales de la tabla (listas y por id) no
  tienen llamador hoy en el filial. Ningún código del filial las llama por dentro.
- `retiro(id)`, `conteoMoneda(id)` y `movimientoCaja(id)` corren en una transacción de lectura
  para leer la caja o el conteo (LAZY).
- En el PDV, `conteoApertura == null` significa «caja sin apertura». Para el dueño no cambia; si
  el token no fuera del dueño de la caja, el PDV ofrecería abrirla y `saveConteo` lo rechazaría
  (ya desde #198). Aceptado: se prueba por pantalla y se busca «No autorizado» en el log de alpha.

## Fases

1. `PdvCajaService.exigirAccesoACaja`; controles en los resolvers de la tabla, los campos de conteo
   y los campos de verificación de `savePdvCaja`. Repositorio: buscar la caja de un conteo.

Tests (`CajaDetalleAccesoTest` y casos nuevos en `PdvCajaAccesoTest`): caja propia pasa, ajena se
rechaza sin consultar, `idVenta` de otra caja y de una venta sin caja, conteo sin caja, campos de
conteo `null` y no error **en los dos resolvers**, verificación cambiada (o puesta en `null`) sin
rol se rechaza sin guardar, el dueño edita su caja sin tocar la verificación, caja nueva con el
input que arma el central pasa. Revertir el fix y ver que fallan.

## Pruebas por pantalla

Filial de la rama en el 8082, desktop en 127.0.0.1:4200, cajero de prueba con `VENTA TOUCH`:
abrir caja, venta, retiro, gasto, últimas ventas (reimprimir), delivery, cerrar caja y
«Últimas cajas» funcionan igual. Las consultas sobre la caja de otro usuario se rechazan (por API
con el token del cajero) y los campos de conteo llegan `null`; con `ANALISIS DE CAJA` pasan.
Conciliación de cupones sobre una caja ajena con un usuario con solo `VENTA_TARJETA_COMPLETAR`:
el diálogo abre igual. Transferencia rechazada: el desktop queda con la sesión del destino sobre la
caja anterior (viene de #198); se sale volviendo a iniciar sesión.

## Despliegue y vuelta atrás

Sin migración ni datos. `develop` → filial alpha (mauro); `release/beta` → 6 de farmacia;
`master` → 18 de bodega, solos en 15 min. #198 todavía está solo en `develop`: los dos llegan
juntos a farmacia. Antes de pasar a beta, buscar «No autorizado» en el log del filial alpha.

Vuelta atrás: en stable, pasar la release a prerelease (`check-update.sh` vuelve a la anterior);
en alpha y beta, revert + release nueva. Si un cajero queda trabado, el usuario ADMIN pasa siempre
y puede cerrar la caja.

## Resultado de las pruebas (2026-10-28, filial local, sucursal 24)

- Cajero con solo `VENTA TOUCH`, por pantalla (desktop de `develop` servido como web contra el
  filial de la rama): abrir caja, venta, lista de retiros, lista de gastos, reimpresión de
  ticket (lista de la caja), delivery, cerrar caja (apertura y cierre a la vista) y «Últimas
  cajas». Sin rechazos.
- El mismo cajero sobre la caja de otro usuario, por API: las consultas de la tabla responden
  «No autorizado», `ventasPorCajaId` con una venta de otra caja también, y los campos de conteo
  llegan `null`. `savePdvCaja` marcando su propia caja como verificada: rechazado, la caja no
  cambia. Con `ANALISIS DE CAJA` todo pasa (incluidas las consultas por id en transacción).
- Sesión de un usuario sobre la caja abierta de otro (por pantalla): el PDV pide el conteo de
  apertura, `saveConteo` responde «No autorizado» y la caja no cambia; el log deja el aviso.
- Tests: 646 sin fallas (`./mvnw clean verify`); los tests nuevos fallan sin los controles.
- Conciliación de cupones sobre caja ajena: probado por API (la consulta del diálogo devuelve la
  caja con conteos `null`, sin error) y por lectura (el diálogo solo mira `activo`). Por pantalla
  no: la lista sale del central y el rol no está en la base local.

## Sin verificar

- La apertura desde PWA/mobile pasando por el central se cubre por test con el input que arma el
  central; por pantalla, si se puede levantar la PWA local contra un central local.
