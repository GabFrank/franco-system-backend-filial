# Plan — «Reimprimir» de un delivery reimprime también su factura (#211)

Rama `fix/delivery-reimprimir-factura` (desde `develop`). Pieza: **filial**. Sin migración, sin cambios de API ni
del desktop.

## Problema

En el PDV, «Reimprimir» de un delivery imprime solo el ticket del delivery (marcado REIMPRESION), aunque la venta
tenga factura legal. Si la factura no salió (impresora), desde esa pantalla no hay forma de reimprimirla: la única
salida es volver a marcar PARA ENTREGA (#207/#208).

El desktop llega al filial por dos caminos, según dónde imprime (`DeliveryService.onReimprimirDelivery`):

- impresora del filial → query `reimprimirDelivery` (`DeliveryGraphQL.reimprimirDelivery`);
- impresora del cliente → query `ticketEscpos(tipo: DELIVERY)` (`TicketEscposGraphQL.delivery`).

Los dos imprimen solo el ticket.

## Cambio (una fase)

Mismo orden que PARA ENTREGA: primero la factura vigente de la venta, si tiene, y después el ticket del delivery.

1. `DeliveryGraphQL.reimprimirDelivery`: después del control de caja, `facturaLegalService.findVigenteDeVenta(
   venta.getId(), delivery.getSucursalId())`. Si hay factura, `facturaLegalGraphQL.reimprimirFacturaLegal(...)`;
   si devuelve false, `GraphQLException("No se pudo reimprimir la factura")` (como `reimprimirVenta`) y no sigue
   con el ticket. Después, el ticket como hoy.
2. `TicketEscposGraphQL.delivery`: igual, con `factura(id, out)` antes del ticket en el mismo `out`.

Se usa `findVigenteDeVenta` (lista, filtra anuladas, por sucursal) y no `findByVentaId`, que tira error con las ventas
que quedaron con dos facturas (#207) y devolvería una anulada.

## Datos nuevos

Ninguno.

## Tests

- `DeliveryReimprimirTest`: con factura reimprime factura y ticket, en ese orden; si la factura falla, error y sin
  ticket; sin factura, solo el ticket (los tests actuales).
- `TicketEscposGraphQLTest`: con factura, el base64 trae factura + ticket; sin factura, igual que hoy.
- Batería `./mvnw clean verify`.

## Runtime

Filial propio contra `general` local (sucursal 24), SIFEN apagado, cola CUPS `zz_` propia: un delivery con factura
y uno sin factura, por la pantalla de deliveries del PDV, en los dos modos de impresión.

## Auditoría del plan (paso 5) y del diff (paso 8)

- **Mayor (eje B):** con el plan original, una factura que no sale (impresora, o electrónica sin fila de DE: NPE en
  `FacturaLegalGraphQL.printTicket58mmFactura`, que `reimprimirFacturaLegal` traga y devuelve false) frenaba también
  el ticket, que hoy sale. → El ticket se imprime igual y después se avisa «No se pudo reimprimir la factura del
  delivery». En modo cliente la factura va a un buffer aparte: si falla no quedan bytes cortados y sale el ticket.
- **Mayor (eje A):** dos envíos a CUPS seguidos contra el corte de 60 s del desktop. Cada espera de CUPS es de 10 s,
  así que el peor caso ronda los 20 s. Se prueba en runtime con la cola detenida.
- **Menor (eje A):** el motivo exacto de la impresora en la factura no llega al cajero (queda en el log), igual que en
  `reimprimirVenta`.
- **Menor (diff):** si fallan la factura y el ticket, el mensaje nombra los dos.
- **Declarado (diff):** en modo cliente, una factura que no se pudo armar solo queda en el log (`ticketEscpos`
  devuelve bytes, no tiene por dónde avisar); el cajero ve que salió solo el ticket.
- **Declarado:** si la factura sale y el ticket falla, reintentar vuelve a imprimir la factura. Es papel, sin datos.
- **Fuera de este cambio:** el resolver `reimprimirFacturaLegal` no controla la caja (anotado aparte, no acá).
