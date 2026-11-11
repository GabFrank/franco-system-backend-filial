# Plan #254: la factura del delivery dice el envío que se cobró

Issue: GabFrank/franco-system-backend-filial#254. Rama: `fix/delivery-envio-en-moneda` (desde `develop` f2e1521).
Pieza: **filial** solamente. Pedido del CEO (11/11/2026): «la factura tiene que decir lo mismo que se cobró».

## Problema

En un PDV en otra moneda (USD), el desktop cobra el envío convertido y redondeado a los decimales de esa moneda:
5.000 Gs / 6.450 = 0,7752 → **0,78 USD** = 5.031 Gs (`pago-touch.component.ts:274-279`, `delivery-moneda.service.ts:58`).
La factura (`FacturaService.crearFacturaLegalDesdeVenta`, l.391-402) pone el valor de catálogo, `delivery.precio.valor`
= **5.000 Gs**. Los ítems sí cuadran: su `venta_item.precio` ya es el equivalente (4,83 × 6.450 = 31.154).

Caso de la issue, en alpha: venta 80129, delivery 9, factura 30093.
- Cobrado: 50.000 Gs − 13.815 de vuelto = **36.185 Gs**.
- Facturado: 31.154 + 5.000 = **36.154 Gs**.
- Diferencia: 31 Gs = 5.031 − 5.000. Puede llegar a ±(cotización/200) por envío.

Mismo defecto a la vista: en el ticket de esa venta (`VentaGraphQL`, l.1012-1019) los ítems salen en USD y el envío
como «Delivery: 5.000», en Gs de catálogo.

## Enfoque

El filial recalcula el envío cobrado con la regla del desktop, sin datos nuevos. La venta ya guarda `moneda` y
`cotizacion` (V111.5), y `MonedaVenta.equivalente` ya hace monto × cotización con un solo redondeo HALF_UP:

```
envioEnMoneda = round(valorCatalogo / cotizacion, decimales(moneda de la venta))   // 0,78
envioCobrado  = round(envioEnMoneda × cotizacion, decimales(moneda de la empresa)) // 5.031
```

Los dos redondeos van con `RoundingMode.HALF_UP` y escala explícita (la división no puede tirar `ArithmeticException`).
Una moneda sin `decimales` cuenta como 0, igual que el desktop (`moneda-pdv.util.ts`, `decimalesDe`): la factura tiene
que decir lo que el PDV cobró, aunque la moneda esté mal cargada.

Una venta sin `moneda` (todas las de bodega y farmacia hoy; el filial solo la guarda si es otra que la de la empresa,
`VentaGraphQL.aplicarMonedaDeLaVenta`) o sin cotización positiva → el valor de catálogo, como siempre.

**La cotización es la de la venta.** El desktop cobra el envío de un delivery con `envioEnBase`, que
`DeliveryMonedaService.convertirABase` calcula con `venta.cotizacion` al abrir la lista de deliverys
(`list-delivery.component.ts:346`). Solo si faltara `envioEnBase` usaría la cotización del momento
(`pago-touch.component.ts:276`). Se prueba en runtime cambiando la cotización entre armar y finalizar.

**Descartado:**
- Que el desktop mande el envío cobrado: hace falta un campo nuevo en el input del delivery y la venta, y con eso un
  desktop viejo y un filial nuevo (o al revés) no lo mandan o no lo conocen.
- «Cobrar el envío exacto» (0,7752 USD): el PDV cobra en centavos.

## Fases

### Fase 1: factura

- `MonedaVenta.envioEnMoneda(Double valor, Venta venta)` → `BigDecimal` en la moneda de la venta, o null si no aplica.
  `MonedaVenta.envioCobrado(Double valor, Venta venta, int decimalesEmpresa)` → `Double`: el equivalente, o `valor`
  si no aplica (sin moneda, sin cotización positiva o sin valor).
- `FacturaService.crearFacturaLegalDesdeVenta`: la línea «SERVICIO DE DELIVERY» usa `envioCobrado`. Los decimales de
  la empresa salen de `monedaService.principal()`, leída **solo si la venta tiene moneda** (las de bodega y farmacia no
  la leen) y dentro de un `try/catch`, como `VentaItemGraphQL.principal()`. Si falla o devuelve null: el valor de
  catálogo, como hoy. Riesgo aceptado: una excepción de JPA en esa lectura deja la transacción de `saveVenta`
  rollback-only aunque se ataje; es la misma lectura que ya hace `MonedaFacturaResolver.para` en este método para estas
  mismas ventas, y una consulta que falla con la conexión viva haría fallar la venta igual.
- Llamadores (sin cambio de firma): `VentaGraphQL:632` (venta con factura, F11) y `DeliveryGraphQL:425` (factura al
  finalizar un delivery).
- **Tests (fallan con el código viejo):**
  - `MonedaVentaTest`: 5.000 / 6.450 USD → 0,78 y 5.031; venta sin moneda → 5.000; cotización null o 0 → 5.000;
    valor null → null; moneda con 0 decimales.
  - `FacturaServiceDesdeVentaTest` (con `@Mock MonedaService`, descriptores capturados del `BuildRequest`): venta USD
    con delivery de 5.000 → línea de 5.031; venta sin moneda → 5.000 y `monedaService` sin tocar; `principal()` que tira
    o devuelve null → 5.000 sin excepción.

### Fase 2: ticket de la venta

- `VentaGraphQL` (ticket de venta, l.886): `precioDeliveryGs` pasa a ser `envioCobrado`. Con eso cuadran la línea
  «Delivery:» y los totales en Gs, Rs y Ds del ticket (l.1077 suma `totalGs + precioDeliveryGs`: hoy da 36.154 en el
  caso de la issue).
- En una venta en otra moneda (`TicketMonedaVenta.aplica`), la línea «Delivery:» muestra el envío en esa moneda (0,78),
  como los ítems, con `MonedaVenta.envioEnMoneda`.
- **Test:** `TicketMonedaVentaTest`: envío 5.000 a 6.450 USD → «0,78».

## Fuera de alcance (anotado)

- `FacturaLegalGraphQL.printTicket58mmFactura*` (l.1204, l.1646) suma el envío de catálogo a un subtotal y, si no
  cuadra con `totalFinal + descuento` (diferencia > 1), usa ese total. Con el arreglo la línea de la factura ya
  incluye el envío cobrado y el subtotal sigue cayendo al respaldo, como hoy. No cambia el monto impreso.
- `FacturaLegalGraphQL:1008-1010`: asigna `precioDelivery{Gs,Rs,Ds}` de catálogo pero no los imprime. Sin efecto.
- `PdvCajaService:443-447`: `totalDelivery` (con `=+`) no se usa en ningún lado. Variable muerta.
- `VentaCreditoGraphQL` (ticket de venta a crédito): no hay venta a crédito en otra moneda.
- El central (`FacturaLegalGraphQL:355/706`, `VentaCreditoGraphQL:337`) imprime con el envío de catálogo si reimprime
  una venta en otra moneda. Misma limitación de impresión, no cambia ninguna factura; se anota para el central.
- Facturas ya emitidas con la diferencia: no se corrigen (nota de crédito/débito si hace falta). En producción no hay
  ninguna: bodega y farmacia no tienen todavía `venta.moneda_id` (consulta del 11/11).

## Datos nuevos

Ninguno: sin columna, campo GraphQL, input ni migración.

## Contrato y canales (eje A)

Solo filial. El desktop no cambia: ya cobra 0,78. Un filial nuevo con un desktop viejo da lo mismo, porque el envío
se cobra igual desde la versión con PDV en otra moneda. Ventas sin moneda: sin cambio.

## Pruebas de runtime

Filial de esta rama por jar en el 8092 (`SUCURSALID=2`, base `general@5552` del alpha, `sifen.enabled=false`) y el
desktop `web-production` estático apuntado a él, con el PDV de la sucursal 2 en USD (configuración 12, se apaga al
terminar).

1. Delivery con BANES 200 en USD, «Finalizar» con factura → la línea «SERVICIO DE DELIVERY» de la factura = 5.031 y
   total factura = lo cobrado.
2. Venta con factura (F11) de un delivery, si ese camino lo permite.
3. Ticket de la venta → «Delivery: 0,78» y total en Gs 36.185.
4. Cotización cambiada entre armar el delivery y finalizarlo → el desktop cobra con la de la venta y la factura coincide
   (si no, se revisa el enfoque).
5. Regresión: PDV en Gs con delivery → línea de 5.000 y ticket «Delivery: 5.000».

Al terminar: configuración del PDV de la sucursal 2 apagada, filial del 8092 bajado.

## Auditoría (paso 5, 11/11/2026)

| # | Hallazgo | Qué se hizo |
|---|---|---|
| B1 | `monedaService.principal()` dentro de la transacción de `saveVenta` puede dejarla rollback-only | Solo para ventas con moneda, con `try/catch` y respaldo al catálogo; riesgo residual anotado (misma lectura que ya hace `MonedaFacturaResolver`) |
| B2 | `FacturaServiceDesdeVentaTest` no inyecta `MonedaService` | `@Mock MonedaService`; la venta sin moneda no lo toca |
| B3 | Falta test a nivel de `crearFacturaLegalDesdeVenta`, con `principal()` que falla | Agregado |
| B4 | Moneda sin decimales redondea 0,7752 → 1 USD | Se mantiene: el desktop cobra 1 USD en ese caso (`decimalesDe` → 0), y la factura tiene que decir lo cobrado |
| A1 | El desktop podría cobrar el envío con la cotización del momento y no con `venta.cotizacion` | Verificado: `envioEnBase` sale de `venta.cotizacion`; prueba de runtime 4 lo confirma |
| A2 | Otros lectores del envío de catálogo | Ticket de venta (l.886/1077) entra en la fase 2; l.1008 y `PdvCajaService` son código muerto; central anotado |
| A3 | `PdvCajaService` suma con `=+` | Variable muerta, fuera de alcance |
| A4 | Redondeo sin modo explícito | `HALF_UP` con escala explícita en las dos divisiones |

## Sin verificar

- El DE electrónico con `monedaExtranjera` y `tipoCambio`: cómo redondea jsifenlib una línea de 5.031 a USD (se ve en
  el KuDE de la prueba de runtime si el filial de prueba emite DE).
- Las 18 filiales de bodega y las 6 de farmacia no tienen ventas en otra moneda: el cambio no se ejercita en
  producción hasta que llegue moneda por módulo.
