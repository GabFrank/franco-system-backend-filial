# Plan — el aumento en el ticket

Rama `fix/ticket-aumento` (filial). Pedido de Franco el 20/10/2026, al probar el tope del aumento
(desktop #546). Este plan muere en el PR (ciclo §11).

## Qué resuelve

Venta de 180.000, cobro en efectivo de 270.000 y aumento de 90.000 (F8). Hoy los dos tickets de esa
venta muestran mal el aumento:

**Ticket simple** (`VentaGraphQL.printTicket58mm`): lo ignora. El aumento se calcula (línea 725) y no
se usa. Lo dejó así a propósito `PLAN-DESCUENTO-TICKET-Y-FACTURA-SILENCIOSA.md` (24/09) como «otra
decisión de formato del ticket»; esta es esa decisión.

```
      Parcial    Desc.     Final
Gs.   180.000        0   180.000        ← y abajo: Pago 270.000, Vuelto 0
```

**Ticket de factura** (`FacturaLegalGraphQL.printTicket58mmFactura` y su variante en moneda extranjera,
que son los que se usan en «Venta + Ticket», delivery y la impresión desde el cliente): lo muestra como
un descuento negativo, bajo el encabezado fijo `Desc.`:

```
      Parcial    Desc.     Final
Gs.   180.000  -90.000   270.000
```

(`FacturaService.printTicket58mmFactura`, que sí tiene una línea «Aumento», no tiene llamadores.)

## Diseño

Los tres tickets tienen 32 columnas y la tabla de totales ya usa 31: no entra una columna más. La del
medio pasa a ser **el ajuste neto, con su nombre**:

- Encabezado **`Desc.`** si el ajuste es descuento (como hoy) y **` Aum.`** si es aumento (mismo ancho).
- Valor: el ajuste **sin signo**, en cada moneda. Final: el de hoy (parcial − ajuste neto).

```
      Parcial     Aum.     Final
Gs.   180.000   90.000   270.000
```

- **Ticket simple**: `ajuste = descuento − Math.round(|aumento|)`, de `AjusteCobro` (la cuenta única).
  - `|aumento|`: hay 4.709 líneas de aumento guardadas en negativo (en la `bodega` local), todas
    anteriores a 2024-10 y casi todas de deliverys; ningún cobro mezcla signos. El valor absoluto del
    total las imprime bien.
  - `Math.round`: el aumento en reales o dólares da Gs con decimales (0,6957 × 1.150 = 799,99…) y el
    `intValue()` de hoy imprimiría 799 y un Final 1 Gs menor que lo pagado.
- **Ticket de factura**: el ajuste es `facturaLegal.descuento` (neto con signo, como hoy); solo cambian el
  encabezado y el signo del valor. No se toca cómo se calcula el total.
- **Sin aumento, los tres tickets salen igual que hoy.**
- **Descuento y aumento en el mismo cobro** pasa (145 cobros, redondeo del PDV): se imprime el neto, que
  cierra con el pago (ej. D 1.380 + A 380 → `Desc. 1.000`).
- **Detalles** (pagos y vueltos) no cambia: con el Final corregido, ya cierra.
- **Sacar** las variables `aumento`, `descuentoRs` y `descuentoDs` de `printTicket58mm`, que hoy quedan
  sin usar. El bloque comentado de totales viejos no se toca.

## Fase única — filial

1. **Antes de editar**, fijar en el test el bloque de totales que imprime el código de hoy sin ajuste y
   con descuento (texto exacto de las 4 líneas, con el `cambio = 7000` del mock), en los dos tickets.
   Así «igual que hoy» queda probado contra la salida real y no contra sí misma.
2. Cambio en `VentaGraphQL.printTicket58mm` y en las dos tablas de `FacturaLegalGraphQL`.
3. Tests (`VentaGraphQLTicketDestinoTest` y `FacturaLegalTicketDestinoTest`):
   - aumento de 90.000: ` Aum.`, `90.000` y Final `270.000` (**falla con el código de hoy**);
   - aumento guardado en negativo: el mismo texto;
   - aumento en reales con fracción (0,695652173913043 × 1.150): `800`;
   - descuento y aumento juntos: `Desc.` y el neto;
   - delivery con aumento: el envío en Parcial y el aumento aparte;
   - las líneas `Rs.` y `Us.` con aumento;
   - factura con aumento: ` Aum.` y `90.000` sin signo; factura con descuento: igual que hoy.
4. Gate: `./mvnw clean verify -B`.

## Datos nuevos

Ninguno: no hay migración ni cambio de GraphQL. Es solo el formato de tres tickets.

## Puertas

- **Ticket simple** (`printTicket58mm`): venta, respaldo cuando la factura no se crea, pagaré de
  crédito, reimpresión, delivery e impresión desde el cliente.
- **Ticket de factura** (`FacturaLegalGraphQL`): «Venta + Ticket», delivery con factura, impresión
  desde el cliente y reimpresión de factura.
- **Fuera de alcance** (siguen con el total bruto, como hoy): el ticket de venta a crédito del central
  (`VentaCreditoGraphQL.imprimirVentaCredito`), el `reimprimirVenta` del central (sin llamador en el
  desktop) y el `VentaCreditoGraphQL.printTicket58mm` del filial (sin uso).

## Límites (escritos a propósito)

- **Ventas viejas con aumento negativo** (deliverys hasta 2024-10): el ticket simple usa `|aumento|`,
  pero la factura de esa venta guardó el aumento como descuento (`getNeto()` con signo). En una
  reimpresión, los dos tickets de esa venta van a diferir. Es histórico; ver central #454.
- **Sin cotización**, las líneas `Rs.`/`Us.` usan cambio 1 y un monto de 1.000.000 o más desborda la
  columna de 9. Ya pasa hoy y este cambio no lo empeora.

## Despliegue y riesgo

Sale solo, en ≤15 min, a las filiales del canal que reciba el merge (alpha en `develop`). No hay orden
con el central ni con el desktop: el desktop solo reenvía los bytes a la impresora. Rollback: el JAR
anterior imprime como hoy. Riesgo bajo: solo cambia el texto de los tickets de ventas con aumento.

## Qué queda sin verificar y cómo

- **El papel**: se prueba el texto en los tests y en ventas reales locales («Finalizar + Ticket»,
  «Venta + Ticket», delivery y reimpresión). Si la térmica de esta máquina (`ticket_soporte`) está
  disponible, se imprime uno.

## Hallazgos de la auditoría del plan (paso 5) y qué se hizo

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| «El ticket de factura ya muestra Aumento» citaba código muerto: el real muestra `Desc. -90.000` | A | Se corrige también, con el mismo encabezado |
| `|aumento|` en el ticket y `getNeto()` en la factura difieren en aumentos negativos | A y B | Escrito en Límites; histórico |
| «Bytes idénticos» sin contra qué comparar | A y B | Texto de hoy fijado en el test antes de editar |
| Otros tickets de la venta (crédito del central, reimpresión del central) | A | Fuera de alcance, listados |
| Cifras de signos infladas (join sin `sucursal_id`) | B | Corregidas: 76.694 / 4.709, nada mezclado |
| `intValue()` trunca un aumento en moneda extranjera | B | `Math.round` del aumento + test |
| «El PDV no ofrece descuento y aumento juntos» era falso (145 cobros) | B | Texto corregido + test |
| Variables sin uso | A | Se sacan |
