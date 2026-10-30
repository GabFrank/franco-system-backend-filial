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

## Runtime (30/10)

Jar de la rama en el 8082 contra `general` local (sucursal 24, PDV 25, SIFEN apagado), desktop `web-production`
estático en `127.0.0.1:4203` con MR, impresora `zz_211` (cola CUPS propia a `/dev/null`, borrada al final). Deliveries
de la caja 1185: 8044 (factura 33137, papel), 8043 (sin factura), 8036 (factura 33093, electrónica).

Por pantalla (PDV › Delivery › fila › Reimprimir, modo servidor):

| Caso | Resultado |
|---|---|
| 8044 | trabajos 1590 (9 KB, factura) y 1591 (7 KB, ticket), en ese orden; sin aviso |
| 8043 | un trabajo (ticket) |
| 8036 sin DE (desenganchado y restaurado) | sale el ticket; aviso «No se pudo reimprimir la factura del delivery» |
| 8044, cola detenida (`cupsdisable`) | CUPS toma los dos trabajos en el mismo segundo; respuesta inmediata, lejos del corte de 60 s |
| 8044, cola que rechaza (`cupsreject`) | «No se pudo reimprimir la factura ni el delivery: No se pudo imprimir: Printer is not accepting job.» |

Este último caso salió primero solo con «la factura»: con la cola que rechaza, `printTicket58mm` a veces devuelve
`false` en vez de tirar. Se corrigió para que el mensaje nombre los dos y se sumó el test.

Modo cliente (`ticketEscpos(DELIVERY)`, la query que manda el desktop con «Imprimir desde esta PC»; en el navegador
ese modo no existe, necesita Electron): 8044 → factura + ticket (15.065 bytes, factura primero); 8043 → solo ticket
(6.493); 8036 sin DE → solo ticket (6.117), sin bytes cortados, aviso en el log.

Encontrado de paso, **previo y fuera de este cambio**: `FacturaLegalGraphQL.printTicket58mmFactura` hace
`getIsElectronico() != true` y tira NPE si el timbrado tiene `is_electronico` null (el timbrado 4 de `general`, y 3 de 6
en `bodega` local). Para probar el 8044 se puso temporalmente en `false` y se volvió a null.

Otro, también previo: después de un `cupsreject`, Java siguió viendo la cola como «no acepta» tras el `cupsaccept`
hasta reiniciar el filial (caché de `javax.print`).

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
