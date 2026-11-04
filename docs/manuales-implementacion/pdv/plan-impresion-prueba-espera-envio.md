# Plan: la prueba de impresora avisa cuando el ticket no sale (#213)

Rama `fix/impresion-prueba-espera-envio` del filial, desde `develop` (f7d1801). Pieza única: filial.

## Qué falta de #213

#217 y #218 hicieron que `close()` pueda esperar al hilo de envío (`SalidaConEnvioEnHilo.esperarEnvio()`) y lo
activaron en las reimpresiones. Queda un camino vivo que informa éxito sin que el ticket salga:

- Desktop, Configuración → Impresoras → probar en el servidor (`impresora.service.ts#probarEnBackend`) llama
  `imprimirPruebaEnImpresora` al central. Si la impresora es de una filial, el central la pasa a
  `imprimirEnDestino` del filial (`PrintRouterService.proxyImprimir`), que termina en
  `PrintRouterService.imprimirLocal`.
- `imprimirLocal` abre la salida con `ImpresoraPrintService.abrirOutput` (cola CUPS o socket RAW) sin esperar el
  envío. El ticket de prueba es chico (< 1024 bytes): entra entero en el pipe, ni `write` ni `close` se enteran de la
  falla y devuelve `true`. El desktop muestra «enviado» con la cola rechazando o la IP sin responder.

El resto de lo que no espera está fuera de alcance, como dejaron anotado los planes de #217/#218: legados o sin
llamador (`PrintingService`, `MovimientoPrintService`, `FacturaService`, `VentaCreditoGraphQL`, que el desktop llama
contra el central) o guardados que imprimen dentro de su transacción (venta del PDV, retiro, gasto, cierre).

## Fase 1 — `abrirOutput` espera el envío

`ImpresoraPrintService.abrirOutput` llama `esperarEnvio()` sobre la salida que crea, sea `TcpIpOutputStream` o
`PrinterOutputStream`. Su único llamador es `PrintRouterService.imprimirLocal` (`abrirEscPos` no tiene
llamadores), que ya convierte cualquier excepción del `try`-with-resources (incluido el `close`) en `false` con log.

- Cubre las tres mutations de `print-router.graphqls`: `imprimirEnDestino`, `imprimirEnImpresora` e
  `imprimirPruebaEnImpresora`. A `imprimirEnDestino` también llegan, por el proxy del central,
  `imprimirTicketFacturaEnImpresora` e `imprimirPdfFacturaEnImpresora` (central `FacturaPrintRouterGraphQL`, desktop
  «Imprimir en la sucursal» de facturas): también pasan a avisar la falla.
- Sin cambios de esquema GraphQL, ni de base, ni del desktop: ya trata `false`. La prueba muestra «verificá que la
  sucursal esté online y la cola exista»; el diálogo de facturas, `avisarSinConfirmar()` (puede haber salido) y no
  deja repetir desde ahí (#501).
- Costo: el `close` espera a que CUPS o el socket tomen el trabajo (< 1 s); con la impresora colgada, hasta 10 s.
  El proxy del central espera hasta 30 s (`FrancoSystemsApplication`, `setReadTimeout(30_000)`).

### Test

`ImpresoraPrintServiceEsperaTest` (nuevo), por el camino real de `imprimirLocal`:

- cola CUPS falsa registrada con `PrintServiceLookup.registerService` (nombre `zz_…` propio, declara el flavor
  PAGEABLE que pide `PrinterJob.lookupPrintServices`), cuyo trabajo lee el ticket entero y después tira
  `PrintException` (el mismo `DocPrintJob` que `ImpresionServiceEsperaTest`): `imprimirLocal` devuelve `false` y se
  verifica que el trabajo se creó (no es un `null` por cola no encontrada). Con el código anterior devuelve `true`
  (se comprueba revirtiendo el fix);
- cola que acepta: `true`.

No se usa un socket a un puerto cerrado: el rechazo puede llegar antes del `write` y el código viejo también daría
`false` (carrera), así que no probaría el bug.

## Datos nuevos

Ninguno.

## Prueba de runtime

Filial propio en el 8082 (sucursal 24, SIFEN apagado) y central local; desktop web contra el central. Una impresora de
la sucursal 24 apuntando a una cola CUPS propia `zz_` que rechaza (`cupsreject`) y después a una que acepta:
«probar» en Configuración → Impresoras avisa el fallo y después el envío. Lo mismo con «Imprimir en la sucursal» de
una factura de la 24, en ticket y en PDF. Las colas `zz_` se borran al final.

## Auditoría del plan (paso 5)

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| Si la espera vence (10 s) el hilo sigue y el ticket puede salir igual; el usuario ve `false` y reintenta: copia de más. Por socket, `new Socket(host, port)` no tiene timeout de conexión | B | Aceptado y anotado: hoy el único llamador del desktop es la prueba (sin plata en juego) y el envío colgado ya existía; antes se informaba éxito |
| La cola falsa sin flavor PAGEABLE no se encuentra y el test daría `false` por un `null` | B | El test declara PAGEABLE y verifica que se creó el trabajo |
| Proponía probar con un puerto cerrado | B | Descartado: carrera, no falla con el código viejo |
| «Imprimir en la sucursal» de facturas (ticket y PDF) también llega a `imprimirEnDestino` y no figuraba | A | Agregado al alcance y a la prueba de runtime. El riesgo de factura doble ya lo cubre el desktop: con `false` avisa que pudo salir y no deja repetir (#501) |
| Timeouts: central→filial 30 s, desktop 60 s, filial→filial sin tope; la espera corta a 10 s | A | Sin cambios: todos ≥ 10 s o sin tope |

## Auditoría del diff (paso 8)

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| Sin cambios de roles ni de datos expuestos; el motivo de la falla queda en el log del servidor | Fijo 1 | Nada |
| Sin esquema, entidades, enums ni migraciones. El registro de colas del test es global; la búsqueda por «contiene» de `getPrintServiceByName` podría encontrarlas desde otro test | Fijo 2 | Nombres `zz_…_213` y comentario en el test |
| Central (develop, beta, master) y desktop (develop, master) toman `false` como fallo y no reintentan. «Probar» dice «verificá que la sucursal esté online y la cola exista» también cuando la cola rechaza | Fijo 3 | Texto del desktop: pendiente, otro repo |

## Resultados

- `ImpresoraPrintServiceEsperaTest`: 2/2 con el fix; con el código anterior el caso de la cola que rechaza da `true`.
- `./mvnw clean verify -B`: 852 tests, 0 fallas; `frc-filial-server.jar` generado.
- Runtime (04/11), filial de la rama en el 8082 (SIFEN apagado), central de `develop` en el 8081, desktop web de
  `develop`, colas `zz_acepta_213` / `zz_rechaza_213` e impresoras 990213/990214 de la 24 sembradas y borradas al final:
  - por API: `imprimirPruebaEnImpresora` al filial y por el central, `imprimirTicketFacturaEnImpresora` e
    `imprimirPdfFacturaEnImpresora` (factura 29604) por el central: `false` con la que rechaza, `true` con la que acepta.
    El filial contesta en ≤ 1 s (PDF incluido); la demora es del central armando el documento;
  - por pantalla: «Probar» avisa «No se pudo imprimir en ZZ RECHAZA 213…» y «Ticket de prueba enviado a ZZ ACEPTA 213»;
    «Imprimir ticket/PDF en sucursal» avisa «No se pudo confirmar la impresión…» con la que rechaza e «Impresión enviada
    a la sucursal.» con la que acepta;
  - CUPS: 7 trabajos en `zz_acepta_213` (los 7 `true`), ninguno en `zz_rechaza_213`;
  - RED, por API al filial: impresora a un puerto local que escucha → `true` (llegaron los 465 bytes del ticket);
    a un puerto cerrado → `false`.

## Sin verificar

- El central no lo necesita: su `SalidaConEnvioEnHilo.close()` espera siempre al envío (central#544, issue central#534
  cerrada), así que su `imprimirLocal` ya devuelve `false` (verificado en `develop` el 04/11).
- La espera vencida (impresora colgada 10 s) no se reprodujo en runtime; la cubren los tests de `SalidaConEnvioEnHilo`.
