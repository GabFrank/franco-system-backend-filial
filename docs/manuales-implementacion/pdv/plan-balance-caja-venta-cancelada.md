# Plan — balance de caja con ventas canceladas (filial)

Rama: `fix/caja-balance-venta-cancelada` (desde `develop`). Pieza: **filial**. Sin migración.

## Problema

Caso real: caja 7440 de la sucursal 1 (bodega), 25/10/2026. El cajero canceló 3 ventas en
efectivo (896642, 896732, 896799; 176.000 Gs) desde la lista de ventas. El cierre impreso por el
filial dio **−117.500**; reimpreso desde la lista de cajas (central) dio **−9.500**.

Con los datos del central:

| | Gs |
|---|---|
| apertura | 2.597.000 |
| cierre | 2.186.500 |
| pagos en efectivo de ventas concluidas | 3.869.500 |
| vueltos de ventas concluidas | −1.229.000 |
| retiros | 2.400.000 |
| gastos | 641.500 |
| **diferencia (central)** | **−9.500** |
| vueltos de las 3 canceladas | −108.000 (−70.000 y −38.000) |
| **diferencia (filial) = −9.500 − 108.000** | **−117.500** |

Causa: `PdvCajaService.generarBalance`, rama `VentaEstado.CANCELADA`, hace
`vueltoGs -= cobroDetalle.getValor()`. El vuelto se guarda **negativo** (base local: 2925/2925 en
concluidas, 9/9 en canceladas), así que restarlo **suma** el vuelto y la caja da faltante por ese
monto. Lo mismo en reales y dólares. Las restas de descuento y aumento de esa rama también están
mal: la venta cancelada nunca los había sumado.

El central ya ignora las canceladas por completo (`VentaRepository.sumarioVentasPorCajaAndSurusal`
filtra `CONCLUIDA`/`EN_VERIFICACION`; el bloque equivalente quedó comentado).

Segundo síntoma, mismo método: `totalGeneral += venta.getTotalGs()` corre para **todas** las
ventas, canceladas incluidas. El central solo suma `CONCLUIDA`/`EN_VERIFICACION`. Por eso el total
de la caja calculado por el filial no baja al cancelar.

## Quién lee este balance (todo cálculo, nada se guarda)

| Lector | Campo | Efecto del arreglo |
|---|---|---|
| Ticket de cierre/reimpresión desde el PDV (`imprimirBalance`, `ConteoGraphQL` al cerrar, `TicketEscposGraphQL`) | `diferencia*` (las líneas de `totalVenta*` están comentadas en `ImpresionService`) | la diferencia deja de incluir el vuelto de las canceladas |
| PWA (`caja-cerrar`, `caja-detalle`) y mobile (`caja-info`) → central `imprimirBalance` sin impresora → `FilialCajaProxyService.imprimirBalanceEnFilial` → filial `imprimirBalanceVerificando` | `diferencia*` | el papel pasa a coincidir con lo que la PWA muestra en pantalla (que viene del central) |
| `balancePorCajaId` / `PdvCaja.balance` / `CajaResolver` → desktop `adicionar-retiro-dialog` y `adicionar-gasto-dialog` (`servidor=false`) | `−diferencia*` = saldo disponible | el saldo baja al real: hoy deja retirar de más por el vuelto de cada venta cancelada |
| `balancePorFecha` | `totalVenta*`, tarjeta, crédito | sin cambio (no tocan esos campos) |
| nadie hoy | `totalGeneral`, `totalDescuento`, `totalAumento`, `vuelto*`, `totalCanceladas*` del filial | se alinean con el central por coherencia. Los totales de caja del desktop (`list-venta`, `analisis-diferencia`) los pide al central con `balancePorCajaIdAndSucursalId`, que el filial no expone |

No cambia ningún tipo ni campo GraphQL ni tabla.

## Fase 1 — fix + test (un commit `fix(caja): ...`)

1. En `generarBalance`:
   - `totalGeneral += venta.getTotalGs()` solo si la venta es `CONCLUIDA || EN_VERIFICACION`, en un
     `if` propio **antes** de buscar el cobro (el central suma aunque no haya cobro). Una venta
     `ABIERTA` deja de sumar, igual que en el central.
   - Rama `CANCELADA`: queda solo la suma a `totalCanceladas{Gs,Rs,Ds}` de los pagos, con
     `Boolean.TRUE.equals(cobroDetalle.getPago())` (hoy desempaqueta un `Boolean` que puede ser
     null). Se borran las restas de vuelto, descuento y aumento.
2. Test nuevo `PdvCajaServiceBalanceTest` (Mockito, sin Spring), con la caja 7440 reducida:
   - caja abierta con apertura, una venta concluida con pago y vuelto, y una cancelada con pago y
     vuelto → `diferenciaGs`, `vueltoGs`, `totalGeneral` y `totalCanceladasGs` iguales a la cuenta
     del central;
   - lo mismo en reales y dólares;
   - cancelada con descuento y aumento → no mueven `totalDescuento`, `totalAumento` ni
     `totalGeneral`;
   - cancelada con `pago`/`vuelto` en NULL → no tira NPE (hoy rompe el balance entero y deja los
     diálogos de retiro/gasto sin saldo verificable);
   - ventas `EN_VERIFICACION` (suma) y `ABIERTA` (no suma) en `totalGeneral`.
   - Mocks: además de los servicios de listas, `movimientoCajaService` (lo llama
     `establecerTotalesApertura`) y `deliveryService.getRepository()`.
   - **Revertir el fix y comprobar que el test falla.**
3. Build `mvn -q package` (o `test` del test nuevo + `TicketEscposGraphQLTest`), leído del log.

## Pruebas por pantalla (paso 9)

Filial propio en el 8082, sucursal 24, SIFEN apagado, usuario MR:

1. Abrir caja, hacer 2 ventas en efectivo con vuelto y cancelar una. La cancelación vive en el
   central y baja por réplica (`VentaService.cancelarVenta` del filial rechaza); en local no hay
   réplica, así que se simula con `UPDATE operaciones.venta SET estado='CANCELADA'` en la base del
   filial, que es lo que deja la réplica.
2. Retiro y gasto: el saldo que muestra el diálogo = efectivo real (apertura + pagos − vueltos de
   las concluidas).
3. Cerrar la caja contando exacto → ticket del cierre con diferencia 0. Reimprimir desde «últimas
   cajas» → 0.
4. Comparar con el código viejo (antes del fix) la misma caja: faltante = vuelto de la cancelada.
5. Reimpresión desde la PWA: central local con `sucursal.ip` de la 24 apuntando al filial 8082,
   `imprimirBalance` sin impresora → el ticket sale del filial con la misma diferencia que muestra
   la PWA. Restaurar `sucursal.ip` al final.

Restaurar al final los datos de prueba si hace falta.

## Alcance y despliegue

- `develop` → filiales alpha; después `release/beta` (farmacia) y `master` (18 de bodega), que se
  actualizan solas en 15 min. Vuelta atrás: revert + release del canal (~15 min). `check-update`
  solo revierte solo si falla el health, y un error de cálculo lo pasa. No hay datos que deshacer.
- **Cajas abiertas al deploy:** el saldo de retiro/gasto baja de golpe en la suma de los vueltos
  de sus ventas canceladas. Un cajero que ya retiró hasta el saldo viejo puede quedar con saldo
  negativo y sin poder cargar retiros ni gastos en esa caja (es la plata real; el central ya lo
  calcula así). Llevar a `master` fuera del horario de cajas o avisar a tesorería.
- Central y desktop: **N/A** — el central ya calcula bien; el desktop no cambia.

## Fuera de alcance

- `PdvCajaGraphQL.balancePorFecha` (filial, ~`:87`): `totalVentaTarjeta = totalVentaCredito + cb.getTotalCredito()`
  pisa la tarjeta con el crédito. Bug aparte, sin tocar en esta rama.

## Auditoría del plan (paso 5)

- Eje B: sin persistencia ni migración (confirmado). Corregido: la vuelta atrás es revert +
  release (el health no detecta un error de cálculo); aviso por cajas abiertas al deploy; test
  ampliado (descuento/aumento, NULL, dólares, EN_VERIFICACION/ABIERTA); `totalGeneral` fuera del
  `if (cobro != null)`.
- Eje A: contrato GraphQL sin cambios. Corregido: faltaba el ticket de PWA/mobile vía proxy del
  central (agregado a la tabla y a la prueba 5); `totalGeneral` del filial no tiene lector; el
  ticket no imprime `totalVenta*`.

## Sin verificar

- Si algún reporte o persona usa `totalCanceladas*` del filial (el central siempre lo deja en 0).
  Se mantiene igual.
- Cierres ya impresos con faltante de más: no se corrigen (no hay dato guardado); la reimpresión
  después del deploy sale bien.
