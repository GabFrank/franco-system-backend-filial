# Plan #226 — sacar la «última impresora usada» compartida

## Problema

`PrintingService` (singleton) guarda en `selectedPrintService` la última impresora usada por
cualquier caja. `ImpresionService.printBalance` la usa cuando llega `printerName == null` y no hay
`destino`, así que un balance puede salir por la impresora de otra caja.

## Quién llega con `printerName` nulo (verificado en `develop`)

| Llamador | Cuándo es nulo | Qué pasa después del fix |
|---|---|---|
| `PdvCajaGraphQL.imprimirBalance` (mobile vía central, sin impresora en el pedido) | el filial no encuentra `config-backup.json` con `printers.ticket` | `imprimirBalanceVerificando` da false → GraphQLException ya existente «revisar la impresora de tickets de la sucursal» |
| `ConteoGraphQL` cierre con `imprimirBalance=true` | idem, pero hoy nadie lo llama así (el central reenvía `false`, el desktop no manda la variable) | no imprime; el cierre sigue OK (try/catch post-commit); queda un `log.warn` con la caja |
| desktop sin `printers.ticket` configurada | manda `""`/`undefined` y el filial lo trata como el mobile | mismo error que el mobile |
| `TicketEscposGraphQL` (impresión desde cliente) | siempre, pero con `destino` | sin cambio: no busca impresora |
| desktop (`imprimirBalance` con impresora) | nunca | sin cambio |

## Decisión

Opción 1 de la issue: sin impresora resuelta, `printBalance` devuelve `false` y avisa en el log. No
se elige impresora por caja/PDV (opción 2): no hay hoy un dato caja → impresora que lo permita, y
sería una feature.

## Fase 1 (única) — `fix(impresion)`

- `PrintingService`: borrar el campo `selectedPrintService` y `getLasUsedPrinter()`;
  `getPrintService`/`setPrintService` dejan de escribirlo (la caché `printServiceList` queda).
- `ImpresionService.printBalance`: con `printerName == null` y sin destino, `log.warn` (con `idCaja`) y `false`.
- `DesktopPrinterConfigService`: agregar **primero** las rutas donde escribe el desktop actual
  (`app.getPath('userData')/config/config-backup.json`, `name: "FRC"`):
  `~/.config/FRC/config/` (Linux) y `~/AppData/Roaming/FRC/config/` (Windows). Hoy busca solo
  `~/.config/frc-sistemas/...` y otras que el desktop ya no escribe, así que en las filiales el balance
  del mobile sale solo gracias a la «última impresora». Sin esto el fix lo dejaría sin balance.
  Van antes de las viejas porque una copia vieja (en esta PC, `frc-sistemas` es de mayo) puede traer
  otra impresora.
- `PrintingService.searchPrinter`: con `printerName == null` devolver `null`. Hoy hace
  `printerName.equals` y tira NPE en cuanto la caché tiene una impresora; `printRetiro`/`printGasto`
  solo atajan `IOException`, así que un `saveRetiro` sin impresora se revertiría entero.
- Tests:
  - `ImpresionSinStreamCompartidoTest`: quitar la excepción de `selectedPrintService` (con el código
    viejo falla: es el test de regresión).
  - `ImpresionServiceEsperaTest`: nuevo caso, balance sin impresora ni destino → `false` y
    `verifyNoInteractions(printingService)` (con un `never().getPrintService` pasaría con el código
    viejo, porque el mock de `getLasUsedPrinter` da null). Con y sin `esperarEnvio`.
  - `DesktopPrinterConfigServiceTest`: lee `~/.config/FRC/config/config-backup.json` (home temporal)
    y lo prefiere a una copia en `~/.config/frc-sistemas/config/`.
  - `PrintingServiceTest`: `searchPrinter(null)` con caché no vacía → `null`, sin NPE.
  - Quitar los stubs de `getLasUsedPrinter()` en `ImpresionServiceDestinoTest` y
    `ImpresionServiceEsperaTest`.

Sin migraciones, sin cambios de esquema GraphQL, sin datos nuevos. Un solo repo (filial).

## Riesgo aceptado

Una filial sin `config-backup.json` en ninguna ruta deja de imprimir el balance del mobile y el
teléfono ve «No se pudo imprimir el balance» (el central se come el texto del filial). Es lo pedido:
mejor avisar que imprimir en otra caja. Con las rutas nuevas, eso queda para filiales sin desktop
configurado en la misma PC/usuario del servicio.

Sin verificar: qué ruta tiene cada filial real (no se entró por SSH). Se ve después del deploy con
`[DesktopConfig] Config leida desde` / `No se encontro config` en el log.

## Auditoría del plan (paso 5)

- Eje A: MAJOR rutas del desktop → incorporado. MINOR cierre de `saveConteo` no tiene llamador vivo
  → anotado en la tabla. MINOR el mobile ve un error genérico → anotado en el riesgo.
- Eje B: MAJOR el test nuevo pasaba con el código viejo → `verifyNoInteractions`. MINOR caso
  desktop sin ticketera → en la tabla. NPE de `searchPrinter` en retiro/gasto → incorporado.

## Auditoría del diff (paso 8)

- Fijo 1 (seguridad): sin resolvers nuevos; rutas de archivo sin entrada del cliente. MEDIO previo:
  `printerName = ""` caía en la «tercera búsqueda» por `contains("")` de `PrinterOutputStream` y
  devolvía la primera impresora del sistema → **incorporado**: `getPrintService` corta nulo y blanco
  (también evita el stacktrace de `setPrintService(null)` en cada impresión sin nombre). El match
  por `contains` (`ticket` coincide con `ticket2`) queda fuera: cambia comportamiento.
- Fijo 2 (esquema): sin migraciones ni esquema. El desktop escribe en las rutas nuevas (verificado
  en `app/main.ts` y `app/package.json`). **Sin verificar**: con qué usuario corre la Scheduled
  Task del filial en Windows; si es SYSTEM, `user.home` no es el del cajero y ahí el balance del
  mobile pasa a dar error. `FRC_DESKTOP_CONFIG_PATH` se lee con `getenv` y `start-filial.ps1` pasa el
  `.env` como `-D`: en Windows ese override no llega (hoy ninguna filial lo usa).
- Fijo 3 (clientes): sin cambio de contrato. `saveRetiro` sin impresora pasa de revertirse por NPE
  (con la caché llena) a guardarse sin ticket, con solo un `log.warn`; se reimprime con
  `reimprimirRetiro`. Aceptado: perder el retiro era peor.

## Verificación

- `./mvnw clean verify -B` del filial; revertir el fix y ver fallar los dos tests.
- Runtime: filial local 8082 (sucursal 24, SIFEN apagado), mutation `imprimirBalance` sin
  `printerName` con y sin `config-backup.json` (vía `FRC_DESKTOP_CONFIG_PATH`), antes y después de
  imprimir otro ticket con impresora explícita; cola CUPS propia `zz_` para no tocar las reales.

Resultado (06/11, filial local 8082 con `user.home` temporal, colas CUPS `zz_t226_a/b`
deshabilitadas para que no salga papel):

| Caso | develop | con el fix |
|---|---|---|
| balance del desktop con `zz_t226_b`, luego del mobile sin impresora y config en `~/.config/FRC` → `a` | sale por `b` (otra caja); «No se encontro config» | sale por `a` |
| + copia vieja en `~/.config/frc-sistemas` → `b` | — | gana la actual (`a`) |
| sin ninguna config / desktop con `""` | — | error «revisar la impresora de tickets», sin ticket |
| `saveRetiro` sin impresora, caché llena | NPE, retiro revertido | retiro guardado (1009, borrado después), sin ticket |

`./mvnw clean verify -B`: 857 tests, 0 fallas (en una copia fuera de `dev-frc` por jdtls). Con el
código de develop fallan los 6 tests nuevos o cambiados.

No probado por pantalla: el botón del mobile/PWA → central → filial. El central y los clientes no
cambian; se probó la misma consulta que el central reenvía (`printerName` y `local` nulos).

## Filiales Windows (06/11)

La única Windows en producción es la 4 de bodega (SUC-INDUSTRIAL). El filial corre como `franco`
(Scheduled Task `FRC-Filial-Server`), pero con `-Duser.home=C:/frc-filial` (sale del `.env` vía
`start-filial.ps1`): nunca ve la config del desktop en `C:\Users\...`, ni con las rutas viejas ni
con las nuevas. Hoy su balance del mobile salía por la «última impresora», que es la única térmica
(`ticket`, USB). Se creó `C:\frc-filial\config-backup.json` con `{"printers":{"ticket":"ticket"}}`
(se lee como `user.home/config-backup.json`, ruta que ya existía): no cambia nada hoy y evita el
error después del merge. En Linux el `user.home` real es `/home/franco` (gotchas.md), así que ahí
alcanzan las rutas nuevas. Una filial Windows nueva necesita el mismo archivo.

## Fuera de alcance

El central tiene el mismo patrón en su `PrintingService`: issue aparte.
