# Impresión del POS desde el cliente (filial)

> Desde 2026-09-29. Rama `feat/impresion-pos-cliente`. Contraparte en el desktop:
> `frc-sistemas-integrados-angular/docs/impresion-pos-desde-cliente.md`.

## Para qué

Hasta ahora **todos** los tickets del POS los imprime el filial: el desktop manda `printerName` y
el filial abre la cola CUPS y escribe el ESC/POS. Eso exige que la impresora esté conectada (o
compartida) al host del filial.

Con esta implementación, cada PC puede elegir en *Configuración → Imprimir desde esta PC* que el
filial **genere** el ticket y se lo **devuelva** en base64; el desktop lo imprime con Electron en
su impresora USB local. El flujo de siempre (**imprimir por servidor**) no cambia: todo lo nuevo
es opcional y, sin los argumentos nuevos, cada operación hace exactamente lo de antes.

## Cómo está hecho

### Renderers con destino opcional

Cada renderer del POS tiene una sobrecarga con un `OutputStream destino` al final:

| Renderer | Clase |
|---|---|
| `printTicket58mm(..., destino)` (ticket, pagaré, delivery) | `VentaGraphQL` |
| `printTicket58mmFactura(..., destino)` | `FacturaLegalGraphQL` |
| `printTicket58mmFacturaMonedaExtranjera(..., destino)` | `FacturaLegalGraphQL` |
| `printBalance / printGasto / printRetiro / printSenaCupon(..., destino)` | `ImpresionService` |

- `destino == null` → **lo de siempre**: busca la impresora por nombre y escribe en
  `PrinterOutputStream`. La firma vieja delega en la nueva con `null`.
- `destino != null` → escribe ahí y **no busca impresora** ni toca los campos compartidos
  `printerOutputStream` / `printService` de esos `@Component`.

El contenido del ticket **no se tocó**: el cambio en cada renderer es elegir el stream y no cerrar
el de la impresora cuando se escribe en memoria. Todo sigue en 58 mm / 32 columnas.

### Operaciones que guardan e imprimen en el mismo paso → devuelven el ticket

| Operación | Argumento nuevo | Dónde vuelve el ticket |
|---|---|---|
| `saveVenta` | `imprimirEnCliente: Boolean` | `Venta.ticketEscpos` |
| `saveDeliveryEstado` | `imprimirEnCliente: Boolean` | `Delivery.ticketEscpos` |

Qué se imprime lo sigue decidiendo `decidirRuta` / `facturarDelivery` (política de facturación):
factura, ticket, pagaré o nada. Con `imprimirEnCliente: true` los mismos comprobantes se escriben
en memoria en el mismo orden (el delivery `PARA_ENTREGA` lleva dos, uno tras otro). Si la ruta no
imprime nada (`SIN_FACTURA`, `FACTURA_SILENCIOSA`), `ticketEscpos` vuelve `null`.

`ticketEscpos` es `@Transient @JsonIgnore`: no se persiste ni viaja en la replicación.

### Operaciones que solo guardan → el ticket se pide después

| Operación | Argumento nuevo | Qué hace con `true` |
|---|---|---|
| `saveGasto` | `imprimirEnCliente: Boolean` | guarda y **no imprime** |
| `saveRetiro` | `imprimirEnCliente: Boolean` | guarda y **no imprime** |
| `saveFacturaLegal` | (ya existía `print: Boolean`) | el desktop manda `print: false` |
| `saveConteo` | (ya existía `imprimirBalance`) | el desktop manda `imprimirBalance: false` |

### Consultas nuevas (`graphql/print/ticket-escpos.graphqls`, `TicketEscposGraphQL`)

```graphql
ticketEscpos(tipo: TicketEscposTipo!, id: ID!, reimpresion: Boolean, local: String): String
senaCuponEscpos(input: SenaCuponInput!, local: String): String
```

Cada tipo reproduce la reimpresión que ya existía, con los mismos renderers:

| `tipo` | `id` | Equivale a |
|---|---|---|
| `VENTA` | venta | `reimprimirVenta` (su factura si tiene, si no el ticket REIMPRESION) |
| `FACTURA` | factura legal | `reimprimirFacturaLegal` (normal o moneda extranjera) |
| `BALANCE` | caja | `imprimirBalance` |
| `GASTO` | gasto | `reimprimirGasto`; `reimpresion` decide la marca REIMPRESION |
| `RETIRO` | retiro | `reimprimirRetiro`; `reimpresion` decide la marca REIMPRESION |
| `DELIVERY` | delivery | `reimprimirDelivery` |

Devuelven `null` si el renderer no escribió nada y lanzan `GraphQLException` si lo pedido no
existe. **Nunca** buscan impresora.

## Compatibilidad y despliegue

- **Primero se despliega el filial, después se activa "Imprimir desde esta PC".** Un desktop en
  ese modo contra un filial viejo manda argumentos que el schema no conoce y **la venta falla**.
- Un desktop en modo "Imprimir por servidor" manda exactamente lo mismo que antes: funciona contra
  filiales viejos y nuevos.

## Tests

| Test | Qué prueba |
|---|---|
| `ImpresionServiceDestinoTest` | balance, gasto, retiro y seña: en memoria == lo que le llega a la impresora, byte por byte, sin buscar impresora |
| `VentaGraphQLTicketDestinoTest` | ticket y pagaré: ídem; sin impresora el servidor sigue sin imprimir y el cliente igual recibe el ticket |
| `FacturaLegalTicketDestinoTest` | factura electrónica (QR/CDC) y en moneda extranjera se escriben en memoria sin impresora |
| `TicketEscposGraphQLTest` | cada `tipo` sigue la lógica de su reimpresión y devuelve base64 |

`CapturaPrintService` (test) es una impresora falsa que se queda con lo que le manda
`PrinterOutputStream`: es lo que permite comparar los dos caminos. El camino del servidor de las
facturas busca la impresora con un método estático y no se puede sustituir; lleva el mismo cambio
que los tickets que sí se comparan byte por byte.

Además se verificó que el SDL completo no suma errores respecto de `develop` (el schema ya tiene 4
errores preexistentes que el servidor tolera).
