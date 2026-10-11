# PDV: la venta a crédito facturada no guarda el crédito (issue #133)

Estado: **implementado, sin probar en el PDV** (11/10/2026). Rama
`fix/venta-credito-facturada-guarda-credito`, desde `develop` (`5d306c6`). Solo filial: el desktop
ya manda todo lo necesario. El papel que sale (decisión 3) es un supuesto a confirmar por Franco.

## El problema

`PoliticaFacturacionService.decidirRuta` (`:51-53`) manda a `FACTURA_E_IMPRESION` toda venta con
`ticket=true`, `pdvId`, `facturar=true` y crédito. Esa ruta (`VentaGraphQL.facturarEImprimir`)
factura e imprime, pero **no guarda el `VentaCredito` ni sus cuotas, ni imprime el pagaré**. El
orden «factura antes que crédito» viene de `afea7f4` (26/08/2024).

Cuándo pasa de verdad: el desktop manda `facturar = !facturado`, y en un cobro con CONVENIO
`pago-touch.component.ts:1337` hace `facturado = !res.factura`. El toggle «Factura Legal» del
diálogo de crédito nace en «No», así que el flujo normal manda `facturar=false` y cae en
`PAGARE_CREDITO`. **Solo falla cuando el cajero pone «Factura Legal = Sí».**

Evidencia (copia local de la sucursal 24, dic-2024 a sep-2026): 1292 ventas con cobro CONVENIO,
las 1292 con su `venta_credito`; 3 con factura. No se midió en otras sucursales.

Segundo defecto en la misma ruta: `FacturaService.crearFacturaLegalDesdeVenta` (`:356`) fija
`credito=false`. La factura de una venta a crédito sale como «Condición: Contado», en el papel y
en el DE (`SifenService:1889` deriva `iCondOpe` de ese campo). La factura silenciosa ya lo hace
bien (`VentaGraphQL.inputFacturaSilenciosa`, `setCredito(credito)`). **No se arregla acá**: ver «Qué no
se toca».

## Decisiones

1. **La ruta no cambia.** `decidirRuta` queda igual: una venta a crédito con `facturar=true`
   sigue en `FACTURA_E_IMPRESION`, fuera de «respeta» y sin tomar turno.
2. **El crédito se guarda antes de facturar**, dentro de `facturarEImprimir`, con el mismo código
   que usa `PAGARE_CREDITO` (se extrae a un método). Todo corre en la transacción de `saveVenta`
   y nada sale de ella (`saveAndSend` es un `save`; el DE queda `PENDIENTE`), así que el orden no
   cambia lo que se persiste: se guarda primero para no armar una factura si el crédito falla.
   Si la factura rechaza la venta (`VentaNoFacturableException`) o falla después de escribir, el
   crédito se revierte con la venta.
3. **Salen los dos papeles: el ticket-factura y después el pagaré** (supuesto, a confirmar por
   Franco). El pagaré se imprime con `printTicket58mm(..., pagare=true, cuotas)`, igual que en
   `PAGARE_CREDITO`: es el ticket de la venta con los pagarés al pie, no un pagaré suelto. Con
   `imprimirEnCliente` los dos van al mismo `ticketEscpos`, uno detrás del otro. El
   ticket-factura no corta papel (termina en `feed(5)`): en modo cliente salen en una sola tira
   y el cajero los separa a mano.
4. **Una falla del pagaré no se lleva la factura.** El pagaré se arma aparte (en modo cliente, en
   un buffer que solo se agrega si terminó) y su falla se loguea sin relanzar: la venta, el
   crédito y la factura ya están, y el ticket-factura sale igual.
5. **Si la factura no se pudo crear** (`null`: cortó una validación previa), el crédito ya está
   guardado y la venta sigue viva: en vez del ticket simple sale el ticket **con pagaré**. Cambia
   la decisión 6 del plan de la #169, que dejaba el ticket simple porque el crédito no existía.
6. **Si el `VentaCredito` vuelve `null`** (no debería: `saveAndSend` devuelve la entidad), no se
   imprime pagaré, como hoy en `PAGARE_CREDITO`.
7. **Si la impresión del ticket-factura falla**, se relanza como hoy y el pagaré no sale. El
   crédito ya quedó guardado. Se reimprime con la mutation `imprimirPagare`, que **solo imprime
   en la impresora del filial**: en una caja con «Imprimir desde esta PC» no hay reimpresión del
   pagaré (`TicketEscposTipo` no tiene `PAGARE`). Limitación previa, fuera de alcance.

## Qué no se toca

- **La condición de la factura.** `FacturaService.crearFacturaLegalDesdeVenta` (`:356`) fija
  `credito=false`: la factura de esta venta sigue saliendo «Condición: Contado», en el papel y en
  el DE, igual que hoy. Marcarla como crédito cambia el XML que va a SIFEN (`SifenService:1889`:
  `iCondOpe=CREDITO` + `gPagCred` «30 días», y a la vez `gPaConEIni` EFECTIVO por el 100 %), y no
  hay ningún DE a crédito aprobado que lo respalde: en la sucursal 24 hay 0 facturas con
  `credito=true`, y una venta a crédito nunca llega a la factura silenciosa (el desktop manda
  `ticket=true`). Va en un trabajo aparte, con un DE de prueba aprobado antes.
- Delivery (`DeliveryGraphQL:243`, `saveVentaDelivery`): guarda su crédito por otra vía.
- El desktop. Ni el schema GraphQL: no cambia ninguna firma expuesta.
- Datos existentes: no hay migración ni backfill. Las ventas viejas que hayan perdido el crédito
  no se reparan acá (en la 24 no hay ninguna).
- Preexistente en `PAGARE_CREDITO` y ahora también acá: si guardar el crédito falla, la
  transacción se revierte en el commit, el cajero ve un error genérico y la huella de venta
  duplicada queda confirmada 5 segundos. Y el encabezado del pagaré imprime «01/2» por una
  concatenación (`VentaGraphQL:1040`).

## Fases

### Fase 1 — fix + tests (un commit)

- `VentaGraphQL`: método `guardarVentaCredito(venta, input, cuotas)`; `PAGARE_CREDITO` lo usa;
  `facturarEImprimir` recibe el input y las cuotas en vez del `boolean credito`, guarda el
  crédito, factura e imprime el pagaré.
- Tests (`VentaGraphQLFacturaEImpresionTest`; `VentaGraphQLTicketDestinoTest` por el cambio de
  firma):
  - crédito + factura → se guarda el `VentaCredito` (con `ventaId` y `sucursalId` de la venta),
    sale el ticket-factura y después el pagaré con las cuotas;
  - el crédito se guarda **antes** de crear la factura (`InOrder`);
  - crédito + factura `null` → ticket con pagaré, sin ticket-factura, el turno no se toca;
  - el pagaré falla → no se relanza y el ticket-factura queda en el stream del cliente;
  - contado → nunca se llama a `saveVentaCredito` ni sale pagaré (no cambia);
  - **Revertir el fix y comprobar que los tests nuevos fallan.**
- Los tests entran por `facturarEImprimir`: no cubren el `switch` de `saveVenta` ni la
  transacción. Eso lo cubre la prueba de runtime (abajo).

### Fase 2 — documentación (un commit)

Este archivo pasa de plan a registro.

## Verificación hecha

- `./mvnw clean verify -B`: 448 tests, 0 fallas, antes de sumar el último test; después se corrió
  la clase tocada (19 tests en `VentaGraphQLFacturaEImpresionTest`).
- Con el fix desactivado fallan los 4 tests de crédito que afirman el guardado y el pagaré.
- Auditoría del diff (paso 8): sin bugs. Sumó dos tests: que el pagaré se intenta aunque falle, y
  el caso de la impresora del filial. Ejes fijos 2 y 3: N/A, el diff no toca migraciones,
  entidades ni `.graphqls`.

## Auditoría del plan (paso 5)

Dos auditores, sin verse. Hallazgos y qué se hizo:

| Hallazgo | Eje | Resolución |
|---|---|---|
| El DE a crédito no tiene precedente aprobado por SIFEN | A y B | Se sacó del fix (ver «Qué no se toca») |
| Una falla del pagaré dejaba sin ticket-factura en modo cliente | A y B | Decisión 4 + test |
| Stubs de 4 argumentos dejarían de matchear | A | Ya no aplica: la firma de `FacturaService` no cambia |
| `imprimirPagare` no sirve con impresión en cliente | A y B | Documentado en decisión 7 |
| El ticket-factura no corta papel | A | Documentado en decisión 3 |
| La justificación del orden era incorrecta | B | Corregida en decisión 2 |
| Los tests no cubren el `switch` de `saveVenta` | B | Prueba de runtime local; no se refactoriza `saveVenta` |
| Orden de dos trabajos en impresora física | A | Sin verificar: prueba física |

## Datos nuevos

Ninguno: no nace ninguna columna, campo ni clave. `factura_legal.credito` ya existe y ya lo leen
el ticket-factura y `SifenService`.

## Qué queda sin verificar

- **La impresión física** de los dos papeles seguidos en una térmica, y su orden.
- La prueba manual en el PDV: venta con CONVENIO y «Factura Legal = Sí».
- Que una venta a crédito rechazada por `VentaNoFacturableException` no deje `venta_credito`.
- Cuántas ventas perdieron el crédito en otras sucursales.
