# Plan — reintentar PARA ENTREGA no crea otra factura (#207)

Rama `fix/delivery-reintento-no-refactura` (desde `develop`). Pieza: **filial**. Sin migración ni cambios de API ni
del desktop.

## Problema

`DeliveryGraphQL.saveDeliveryEstado(PARA_ENTREGA)` guarda el delivery, crea la factura (con DE, ya confirmada) e
imprime. Si la impresión falla, el desktop vuelve a mostrar el delivery como ABIERTO con el botón PARA ENTREGA, y
el reintento llama otra vez a `crearFacturaLegalDesdeVenta`: segunda factura para la misma venta, otro número y
otro DE. El error le llegaba al cajero como «Delivery no encontrado».

## Cambio (una fase)

1. Antes de decidir si factura, se busca la factura vigente de la venta: `FacturaLegalService.findVigenteDeVenta`
   (nuevo), la activa más nueva de esa venta y sucursal, sobre una lista. No usa `findByVentaId`, que devuelve un solo
   resultado y tiraría error con las ventas que ya quedaron con dos facturas por este bug. Si existe, se reimprime
   esa y no se consulta la política: el reintento no crea factura ni consume otro turno del modo intervalo.
2. La impresión de la factura nueva sale del `try` que la crea: una falla de impresora ya no se informa como
   «Problema al generar factura electrónica» ni pasa por la devolución de turno (que de todos modos no aplicaba:
   `fallaAntesDeEscribir` daba false).
3. El `catch` final deja pasar los `GraphQLException` con su mensaje, y una `IOException` de impresión sale como
   «No se pudo imprimir: <motivo>. Volve a marcarlo PARA ENTREGA para reimprimir.». Lo demás, «Algo salió mal».

## Tests

- `DeliveryParaEntregaReintentoTest`: con factura existente se reimprime sin crear ni tomar turno; con la impresora
  fallando, el mensaje lo dice.
- `FacturaLegalServiceVigenteDeVentaTest`: con varias facturas devuelve la activa más nueva; sin facturas, null.
- Batería `./mvnw clean verify`.

## Runtime (28/10)

Jar con este cambio y el de #206 juntos, en el 8092 contra `general` local (sucursal 24, PDV 25, SIFEN apagado),
delivery 8044 / venta 79284. Para que facture se pasaron temporalmente la política de la 24 a TODAS y el timbrado no
electrónico 4 (detalle 114) al PDV 25; los dos se restauraron. Colas CUPS `zz_rechaza` y `zz_ok` hacia una
impresora falsa local.

| Paso | Resultado |
|---|---|
| PARA ENTREGA → cola que rechaza | error en 1,7 s: «No se pudo imprimir: Printer is not accepting job. Volve a marcarlo PARA ENTREGA…»; factura 33137 creada |
| PARA ENTREGA otra vez → cola que acepta | imprime factura (8572 bytes) y ticket (6488); la venta sigue con una sola factura, 33137 |

Queda en la base local: factura 33137 (sin DE) y el delivery 8044 en PARA_ENTREGA, como estaba.

El cliente recibe «Internal Server Error(s) while executing query»: el filial no expone al cliente el mensaje de un
`GraphQLException` común (no es un `GraphQLError`). Pasa con todos los errores del filial y es previo a este cambio;
el motivo queda en el log.

## Auditoría (paso 5)

- **Mayor (los dos auditores):** `findByVentaId` es de resultado único y trababa las ventas ya duplicadas →
  `findVigenteDeVenta` con lista, filtrando `activo=false` y por sucursal.
- **Test vacío:** la aserción «no devuelve el turno» pasaba igual con el código viejo → se sacó.
- **Declarado:** si al crear la factura falló el DE (el builder la guarda sin `cdc`), el reintento la reimprime sin
  QR, igual que salió la primera vez; antes creaba otra factura con otro DE. El reintento del DE no es de este cambio.
- **Declarado:** sin #206, la falla típica de CUPS no llega como excepción (el hilo de impresión la traga) y el
  mensaje de «No se pudo imprimir» casi no se ve; con #206 sí.
- **Declarado:** el desktop vuelve el delivery a ABIERTO en la grilla aunque en la base quedó PARA_ENTREGA, hasta
  refrescar. Es lo que permite el reintento; no se cambia.

## Fuera de alcance

- Dos clics simultáneos todavía pueden crear dos facturas (no hay lock por venta). Ya pasaba.
- «Reimprimir» del delivery imprime solo el ticket, no la factura.
- Hasta que se mergee #206, la falla de impresión se cuelga en vez de llegar como error; este cambio no depende de
  ese PR para ser correcto.

## Riesgo y vuelta atrás

- **Riesgo:** bajo. Un método del filial y una consulta de lectura nueva; sin migración.
- **Vuelta atrás:** revertir el commit.
