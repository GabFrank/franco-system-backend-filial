# Plan — stream de impresora en variable local (issue #212)

Rama: `fix/impresion-stream-local` desde `develop`. Una sola fase.

## Problema

Beans singleton guardaban el `PrinterOutputStream` en un campo. Dos impresiones simultáneas por el
mismo bean se pisan: la primera escribe en el stream de la segunda (ticket mezclado) y su propio
stream queda sin cerrar.

Estado en `develop` (30/10) de los 7 puntos de la issue:

| Clase | Estado | Llamador vivo |
|---|---|---|
| `VentaGraphQL`, `ImpresionService` | ya arreglados por #206 (variable local, sin `if (true)`) | — |
| `graphql/financiero/FacturaLegalGraphQL` | stream ya local (#206), pero la **impresora** (`PrintService printService`) sigue en un campo: la factura de una caja puede salir por la térmica de otra | toda impresión/reimpresión de factura |
| `print/operaciones/MovimientoPrintService` | campo `printService` + `printerOutputStream` | `imprimirEntrada` (TICKET1) |
| `service/utils/PrintingService` | campo `printerOutputStream`; caché `printServiceList` en `ArrayList` escrita desde varios hilos | `print(image)` de `PrintGraphQL` (pantalla de tickets de fiesta) |
| `service/financiero/FacturaService` | campos `printService`, `printerOutputStream`, `escPos` | `printTicket58mmFactura` no tiene llamadores |
| `graphql/print/PrintGraphQL` | campos sin uso | — |

## Fase 1

- Pasar el stream a variable local en los 3 métodos y borrar los campos (y los `printService` /
  `escPos` que comparten el mismo problema o no se usan).
- `printServiceList` → `CopyOnWriteArrayList` (lecturas frecuentes, altas raras).
- Sin migración, sin cambio de `.graphqls`, sin cambio de contrato: N/A tabla de datos nuevos.

### Tests

- `ImpresionSinStreamCompartidoTest`: por reflexión, ninguna de las 7 clases de la issue tiene un
  campo de instancia `OutputStream`, `EscPos` ni `PrintService` (salvo `selectedPrintService`).
  Hecho: contra `origin/develop` falla y lista los 9 campos; con el fix pasa.
- Batería: `./mvnw clean verify -B`. Artefacto: `./mvnw clean package`.

### Prueba de runtime

Es de no regresión («cada camino sigue imprimiendo»), no de la carrera: la ventana es de
milisegundos y dos llamadas en paralelo casi nunca se pisan ni con el código viejo. La carrera la
cubre el test.

- Colas CUPS temporales `TICKET1`, `bematech` y `zz_212` con `socket://127.0.0.1:9`, aceptando y
  deshabilitadas (el trabajo queda en cola, no sale a papel), creadas **antes** de levantar el
  filial (Java cachea la lista de impresoras). Se borran al final con `lpadmin -x`.
- Filial local en 8082 (sucursal 24, SIFEN apagado).
- Por cada camino, la misma query que manda el botón del desktop: `imprimirEntrada`,
  `print(image)` y la reimpresión de factura; se confirma un trabajo nuevo en la cola.

## Auditoría del plan (paso 5)

- Eje A (REVISE): `FacturaLegalGraphQL.printService` compartido → **aplicado** (variable local en
  los 3 métodos, campo borrado, test extendido a `PrintService`/`EscPos`). Caché
  `CopyOnWriteArrayList` puede duplicar una entrada: inofensivo, se deja.
- Eje B (ACCEPT con reservas): no usar try-with-resources (cerrar manda medio ticket a CUPS; si
  nadie cierra, el vigía de `SalidaConEnvioEnHilo` cancela a los 30 s) → **no se agrega**. Prueba de
  runtime no concluyente para la carrera y colas que hay que crear aceptando → **plan ajustado**.

### Resultado (30/10)

- `./mvnw clean verify -B`: 827 tests, 0 fallos; jar `frc-filial-server.jar` generado.
- Runtime (filial 8082, perfil dev, SIFEN apagado): `imprimirEntrada` → 1 trabajo en `TICKET1`;
  `print(image)` → 1 en `bematech`; `saveFacturaLegal` con `print:true` → 1 en guaraníes y 1 en
  USD en `zz_212`. Datos de prueba (entrada 6, facturas 33142/33143, `timbrado.is_electronico` del
  6, `numero_actual` del detalle 108) y colas restaurados al terminar.
- Auditoría del diff: Fijo 1, 2 y 3 sin hallazgos sobre el diff.

## Qué queda sin verificar

- `FacturaService.printTicket58mmFactura`: sin llamadores, no se puede ejercitar por pantalla.
- `PrintingService.selectedPrintService` («última impresora usada») sigue compartida a propósito:
  `ImpresionService` la usa como respaldo cuando no llega nombre de impresora. Fuera de alcance.
