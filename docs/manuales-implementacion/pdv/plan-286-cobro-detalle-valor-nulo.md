# Plan — un `cobro_detalle` sin monto traba el balance de la caja (#286, filial)

Rama: `fix/cobro-detalle-valor-nulo` (desde `develop`). Pieza: **filial**. Sin migración.

## Problema

Filial 18 (5.7.0), 27/11/2026, caja 2996: ningún cajero podía hacer el retiro. El desktop mostraba
«Internal Server Error(s) while executing query», y el filial daba un NPE en
`PdvCajaService.generarBalance` (`totalVentaGs += cobroDetalle.getValor()`, línea 526) que llegaba
por `balancePorCajaId`. La causa era una sola fila de `operaciones.cobro_detalle`, la 297400, con
`valor` y `usuario_id` en NULL. Es un pago en efectivo en Gs del cobro de un delivery, y nació en
el mismo instante del alta del delivery 21244. Se destrabó a mano con `valor = 0`.

En las otras filiales hay filas viejas iguales, todas de deliverys y en cajas cerradas: la 1
(2024, caja 3247) y la 11 (2023, caja 415). Pasa desde 2023, no desde la 5.7.0.

Dos defectos del filial:

1. **Graba una línea sin monto.** `CobroDetalleGraphQL.guardarDetalle` guarda lo que llega. Es el
   único camino de alta. Lo usan `saveCobroDetalle`, `saveCobroDetalleList`,
   `CobroGraphQL.guardarCobro` (← `saveVenta` y ← `DeliveryGraphQL.saveDeliveryAndVenta`, el alta del
   delivery) y `VentaGraphQL.saveVentaDelivery`. `VentaTarjetaService` hace `repository.save` solo
   sobre filas ya cargadas, sin crear ninguna.

   **De dónde sale en el desktop** (auditoría eje A, verificado en `origin/develop`):
   `edit-delivery-dialog.component.ts:77` tiene `vueltoControl = new FormControl(null)`, sin
   `Validators.required`. Si el campo queda vacío (botón «Clear» o borrado) y se confirma con Enter,
   `addCobroDetalle` arma la línea con `valor = null`. En un delivery nuevo, esa línea se agrega a
   `cobroItemList` (`:834`) y viaja en `saveDeliveryAndVenta`, sin `usuarioId`. Coincide con la
   fila 297400: valor y usuario NULL, efectivo Gs, creada en el mismo instante que el alta. En un
   delivery ya guardado, el mismo Enter va por `saveCobroDetalle`. `pago-touch` sí tiene
   `Validators.required` (`:491`). No se probó por la pantalla.
2. **Una sola línea así rompe el balance de toda la caja.** `generarBalance` desempaqueta
   `getValor()` en unas 25 sumas: con un `Double` null, la caja se queda sin saldo, sin retiro, sin
   gasto y sin el ticket de cierre.

## Qué se cambia

| Dónde | Cambio |
|---|---|
| `CobroDetalleGraphQL.guardarDetalle` | si `input.getValor() == null`: `log.warn("COBRO_DETALLE_SIN_VALOR_DESCARTADO …")` con `cobroId`, `id`, `usuarioId`, `monedaId`, `formaPagoId` y las banderas pago/vuelto/descuento/aumento, y `return null`, sin grabar. Agrega un logger (`@Slf4j`) a la clase. Es el mismo criterio que el segundo descuento de `guardarCobro`. Los llamadores que recorren listas ya toleran un `null` (`guardarCobro` mira `cd != null`; `saveVentaDelivery` no usa la respuesta; `saveCobroDetalleList` devuelve `[CobroDetalle]`, con elementos que pueden ser null) |
| `CobroDetalleGraphQL.saveCobroDetalle` (mutation directa, responde `CobroDetalle!`) | con `valor` null lanza `GraphQLException("La línea de cobro no tiene monto: no se guardó.")` en vez de devolver null, porque el no nulo del esquema lo convertiría en un error genérico. Sus llamadores en el desktop (`pago-touch` y `edit-delivery-dialog`, cobro de delivery) ya revierten la línea en su `error:` (#390) |
| `CobroGraphQL.guardarCobro` y `VentaGraphQL.saveVentaDelivery` (bucles con el control de un solo descuento) | una línea con `valor` null no marca `discountApplied`, para que un descuento sin monto (que se descarta) no haga descartar al descuento válido siguiente (auditoría eje B, m2) |
| `PdvCajaService.generarBalance` | en los dos recorridos de detalles (concluida/en verificación y cancelada), una línea con `getValor()`, `getMoneda()` o `getFormaPago()` en null se saltea (`continue`): un monto desconocido no suma. Las filas viejas de la 1 y la 11 dejan de romper el balance si alguien abre esas cajas. Moneda y forma de pago se agregan por las dudas: en las filas conocidas y en los últimos 90 días del central no hay ninguna en null |

No cambia ningún tipo ni campo GraphQL ni tabla. No hay dato nuevo.

## Fase 1 — fix + tests (un commit `fix(caja): ...`)

1. Los tres cambios de la tabla.
2. Tests (Mockito, sin Spring):
   - `PdvCajaServiceBalanceTest`: una venta concluida con un pago normal y una línea con `valor`
     null (el caso de la caja 2996), y otra cancelada con un pago null. El balance no lanza y suma
     solo las líneas con monto.
   - `CobroDetalleGraphQLValorNuloTest` (nuevo): `guardarDetalle` con `valor` null no llama a
     `saveAndSend` y devuelve null; `saveCobroDetalle` con `valor` null lanza `GraphQLException`;
     con monto graba como siempre.
   - `guardarCobro` (el camino de `saveVenta` y de `saveDeliveryAndVenta`) con un pago normal, una
     línea sin monto y un descuento: graba el pago y el descuento, no la línea sin monto.
   - El helper `detalle(...)` del test de balance recibe `double`: se agrega una variante con
     `Double` para poder pasar null.
   - Revertir cada fix y comprobar que su test falla.
3. `./mvnw clean verify -B` leído del log.

## Prueba de runtime (local, filial propio en el 8082, sucursal 24, SIFEN apagado)

1. Sembrar por SQL una línea con `valor` NULL en una venta concluida de una caja abierta de la 24.
   Con el jar de `develop`, `balancePorCajaId` falla; con la rama, responde. Por la pantalla:
   «Retiro» del desktop web abre el saldo sin error.
2. Por la pantalla, en el diálogo de delivery: **delivery nuevo**, agregar un pago con monto,
   limpiar el campo del monto con la X, apretar Enter y guardar. El delivery se guarda, la línea
   vacía no se graba (SQL) y el log muestra `COBRO_DETALLE_SIN_VALOR_DESCARTADO`. Con el jar de
   `develop`, el mismo paso graba la fila NULL: así se confirma el origen.
3. **Delivery ya guardado**: el mismo Enter con el monto vacío va por `saveCobroDetalle`. El
   desktop muestra «La línea de cobro no tiene monto…», revierte la línea y no graba nada.
4. Restaurar al final los datos de prueba.

## Lo que queda afuera

- **Desktop**: el campo del monto del diálogo de delivery tendría que ser obligatorio, como en
  `pago-touch`. Va en un issue aparte del desktop. Mientras tanto, en un delivery nuevo la línea
  vacía se sigue viendo en el diálogo hasta reabrirlo; suma 0, así que el total no cambia.
- **Central**: tiene filas viejas con `valor` NULL. No pasa por este `generarBalance`: su balance de
  caja sale de `sumarioVentasPorCajaAndSurusal` (SQL). No se tocó ni se verificó.
- **Otros lectores con filas viejas**: la reimpresión de ticket (`VentaGraphQL` ~1219) y de factura
  (`FacturaLegalGraphQL` ~1356) hacen `vueltoTotal += cd.getValor()` y darían NPE solo si la fila
  NULL es un **vuelto**. Las tres filas conocidas son pagos. Los demás lectores (`PagosTicketAgrupador`,
  `AjusteCobro`, `SorteoCalculo`, `VentaTarjetaService`, `EventosVenta`, `descuentoEnOtraMoneda`) ya
  toleran null.
- **Otros NPE posibles en `generarBalance`** con datos de otras columnas rotas: `getCambio()` null
  en un aumento en R$ o US$ (~540/556) y `venta.getTotalGs()` null (~501). En los últimos 90 días del
  central no hay ningún aumento sin cambio. No se cubren.

## Despliegue

Filial: al mergear a `develop` sale solo a alpha en ≤15 min. Después `release/beta` (6 filiales de
farmacia) y `master` (18 de bodega), y cada filial reinicia el servicio al actualizarse. Sin orden
con otras piezas: el desktop no cambia.
