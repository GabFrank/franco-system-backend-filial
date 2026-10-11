# PDV: la venta a crédito facturada no guarda el crédito (issue #133)

Estado: **implementado, sin probar en el PDV** (11/10/2026). Rama
`fix/venta-credito-facturada-guarda-credito`, desde `develop` (`5d306c6`). Solo filial: el desktop
ya manda todo lo necesario.

Definiciones de Franco, el 11/10/2026, probando cada versión:

1. Primera versión (dos papeles, el segundo repetía el ticket de venta; crédito fuera de la
   política): pidió **un solo papel y que la venta a crédito siga la política de facturación**.
2. Con el pagaré al pie de la factura, el ticket le resultó **demasiado largo**. Quedó: **la
   factura sola y, en un papel aparte, solo los pagarés**. Es lo implementado (decisiones 3 y 4).

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

1. **La venta a crédito sigue la política de facturación**, igual que «Venta + Ticket». En
   `decidirRuta`, con `facturar=true` y `pdvId`, factura si la política lo indica
   (`facturaInmediata`): siempre cuando `venta_ticket_respeta_politica` es `false` (el
   comportamiento por defecto, sin cambios), y según el modo cuando es `true` (toma turno en
   `INTERVALO`, nunca en `A_PEDIDO`). Si no toca factura, va por `PAGARE_CREDITO`. Antes quedaba
   fuera de «respeta» porque en la ruta de factura el crédito no se guardaba; ahora se guarda en
   las dos. Por eso `salioDeUnTurno` ya no recibe `credito`: una venta a crédito que tomó turno
   lo devuelve como cualquier otra.
2. **El crédito se guarda antes de facturar**, dentro de `facturarEImprimir`, con el mismo código
   que usa `PAGARE_CREDITO` (se extrae a un método). Todo corre en la transacción de `saveVenta`
   y nada sale de ella (`saveAndSend` es un `save`; el DE queda `PENDIENTE`), así que el orden no
   cambia lo que se persiste: se guarda primero para no armar una factura si el crédito falla.
   Si la factura rechaza la venta (`VentaNoFacturableException`) o falla después de escribir, el
   crédito se revierte con la venta.
3. **Salen dos papeles: los pagarés solos y el ticket-factura.** El cliente se lleva la factura
   y la caja se queda con el pagaré firmado. `VentaGraphQL.imprimirPagareSuelto` imprime un
   pagaré por cuota, sin repetir la venta (ni ítems, ni cobro, ni logo), y termina con un corte.
   El bloque del pagaré vive en `PagareTicket` y lo comparte con el ticket de venta; el texto es
   el de siempre (se comparó el ticket con pagaré antes y después: solo cambia la numeración,
   decisión 8). El ticket-factura no cambia.
   **El pagaré sale primero**, con la factura ya creada, y en la impresora del filial se espera
   (hasta 5 s) a que su trabajo quede enviado antes de imprimir la factura
   (`PrinterOutputStream.closeYEsperarEnvio`). No es un detalle: el envío a la impresora corre
   en otro hilo y `close()` no lo espera, así que con la factura primero el pagaré —el trabajo
   chico— llegó antes a la cola en la primera prueba real. Con `imprimirEnCliente` van los dos
   en `ticketEscpos`: los pagarés, un corte y la factura.
4. **Una falla del pagaré no se lleva la factura.** El pagaré se arma entero en memoria antes de
   mandarlo: si falla (cuota sin vencimiento, venta sin cliente) no sale ni medio pagaré, se
   loguea y no se relanza. La venta, el crédito y la factura ya están.
5. **Si la factura no se pudo crear** (`null`: cortó una validación previa), el crédito ya está
   guardado y la venta sigue viva: en vez del ticket simple sale el ticket **con pagaré**. Cambia
   la decisión 6 del plan de la #169, que dejaba el ticket simple porque el crédito no existía.
6. **Si el `VentaCredito` vuelve `null`** (no debería: `saveAndSend` devuelve la entidad), no se
   imprime pagaré, como hoy en `PAGARE_CREDITO`.
7. **Si la impresión del ticket-factura falla**, se relanza como hoy y no sale ningún papel. El
   crédito ya quedó guardado. El pagaré se reimprime con la mutation `imprimirPagare`, que
   **solo imprime en la impresora del filial** y saca el ticket de venta con pagaré, no el
   suelto: en una caja con «Imprimir desde esta PC» no hay reimpresión del pagaré
   (`TicketEscposTipo` no tiene `PAGARE`). Limitación previa, fuera de alcance.
8. **Numeración del pagaré corregida.** El encabezado concatenaba `x + 1` como texto: la primera
   de dos cuotas salía «01/2» y la segunda «11/2». Ahora «1/2» y «2/2», en los dos tickets.

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
  duplicada queda confirmada 5 segundos.
- **«Factura Legal = No» sigue sin facturar nunca.** El desktop lo manda como `facturar=false`,
  que para el filial significa «el frontend ya emitió una factura manual»: la venta va por
  `PAGARE_CREDITO` y no entra a la política ni siquiera en modo `TODAS`. El filial no puede
  distinguir los dos casos; cambiarlo es del desktop.

## Fases

### Fase 1 — guardar el crédito (`d41e11b`)

`VentaGraphQL.guardarVentaCredito`, usado por `PAGARE_CREDITO` y por `facturarEImprimir`, que
recibe el input y las cuotas, guarda el crédito y después factura. Esa versión imprimía dos
papeles; lo reemplazó la fase 3.

### Fase 2 — documentación

Este archivo pasa de plan a registro.

### Fase 3 — crédito dentro de la política (`3eabbcf`)

- `PagareTicket` (nuevo): el bloque del pagaré, compartido.
- `PoliticaFacturacionService`: `decidirRuta` sin la excepción del crédito; `salioDeUnTurno`
  sin `credito` (y sus llamadores en `VentaGraphQL` y `DeliveryGraphQL`).
- Esa versión imprimía el pagaré al pie del ticket-factura; lo reemplazó la fase 4.

### Fase 4 — pagaré en un papel aparte

- `VentaGraphQL.imprimirPagareSuelto`; `facturarEImprimir` lo llama con la factura ya creada,
  antes de imprimirla.
- `PrinterOutputStream.closeYEsperarEnvio`: cierra y espera el hilo de impresión.
- `FacturaLegalGraphQL` vuelve a quedar como en `develop`: el ticket-factura no cambia.
- Tests:
  - `PoliticaFacturacionServiceTest`: crédito con y sin turno, `A_PEDIDO`, política que no
    respeta, factura manual. La comparación con la lógica vieja sigue pasando sin tocarla.
  - `VentaGraphQLFacturaEImpresionTest`: el crédito se guarda antes de facturar; salen la
    factura y después el pagaré suelto, sin repetir el ticket de venta; el pagaré que falla no
    se relanza; contado y crédito no guardado salen sin pagaré; el turno se devuelve si la
    factura no se crea o si falla el guardado del crédito.
  - `VentaGraphQLTicketDestinoTest`: el pagaré suelto lleva solo los pagarés, empieza y termina
    con corte, es igual en memoria que impreso, y si falla no escribe nada.

## Verificación hecha

- Fase 1: con el fix desactivado fallaban los 4 tests que afirmaban el guardado del crédito.
- Auditoría del diff de la fase 1 (paso 8): sin bugs; sumó dos tests.
- Fase 3: `./mvnw clean verify -B` en verde. Auditoría del diff: sin bugs que bloqueen. Encontró
  que, al entrar el crédito a la política, una venta a crédito que tomó turno lo perdía si
  fallaba el guardado del crédito (la venta se revierte sin haber intentado facturar): ahora el
  turno vuelve, con su test. También reforzó tres tests y sacó código muerto.

### Prueba de runtime (11/10/2026, filial local, sucursal 24)

`saveVenta` llamado como lo hace el PDV (crédito con dos cuotas, `ticket=true`,
`imprimirEnCliente=true` para leer el comprobante devuelto), con la política local de la 24
(`venta_ticket_respeta_politica = true`):

| Caso | Comprobante devuelto | En la base |
|---|---|---|
| `A_PEDIDO`, «Factura Legal = Sí» | ticket con pagarés 1/2 y 2/2, sin factura | crédito + 2 cuotas, sin factura |
| `TODAS`, «Factura Legal = Sí» | (fase 3) factura con los pagarés al pie | crédito + 2 cuotas, factura con DE `PENDIENTE` |
| `TODAS`, «Factura Legal = No» | ticket con pagarés, sin factura | crédito + 2 cuotas, sin factura |

Cubre el `switch` de `saveVenta` y la transacción, que los tests unitarios no tocan. El modo
`INTERVALO` solo se probó en tests.

Y desde la pantalla del PDV (desktop servido como web, modo de impresión `BACKEND`, política
`TODAS`): venta con CONVENIO, cliente buscado en el diálogo de crédito, «Factura Legal = Sí» y
Firma. Con la fase 3 quedó la venta con su crédito, una cuota y su factura, y un solo trabajo de
impresión; Franco vio ese papel y lo encontró demasiado largo, de ahí la fase 4.

La fase 4 se probó en runtime con la política `TODAS` y dos cuotas:

- Impresión en cliente: el comprobante devuelto trae los pagarés 1/2 y 2/2 (2,4 KB, sin
  ítems), un corte y la factura (17 KB).
- Impresora de la caja: dos trabajos, primero el pagaré y después la factura. En la versión
  anterior (factura primero, sin esperar) habían entrado a la cola al revés.
- En los dos casos la venta quedó con su crédito, dos cuotas y su factura.

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

- **Cómo se ven los dos papeles** impresos: los trabajos salieron en orden, nadie los miró.
- El caso de una impresora que no acepta trabajos: la espera del pagaré corta a los 5 s, pero
  escribir en el pipe de un hilo de impresión muerto se cuelga, igual que ya pasa con la factura.
- No se revisó qué sucursales tienen hoy `venta_ticket_respeta_politica` activa.
- Que una venta a crédito rechazada por `VentaNoFacturableException` no deje `venta_credito`.
- Cuántas ventas perdieron el crédito en otras sucursales.
