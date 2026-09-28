# Plan — venta con tarjeta: terminal obligatoria y cancelación en cadena

Rama `fix/venta-tarjeta-terminal-y-cancelacion` en **central**, **filial** y **desktop** (un PR por repo,
un solo cambio). Fecha: 2026-09-28. Estado: plan auditado (paso 5, ejes A y B), **aprobado por Gabriel el 2026-09-28**.

## Problemas (verificados en código y en datos de farmacia filial 1)

1. **Se cierra la venta con tarjeta sin terminal.** En `pago-touch`, al agregar una línea TARJETA se abre
   `ScanTerminalPosDialog` (`escanearSiEsTarjeta`, ~675) y en la misma pasada `setFocusToValorInput()`
   (~682) enfoca el campo «valor» del cobro. El diálogo solo tiene `autofocus` (sin `cdkFocusInitial`).
   Si gana el foco del cobro, el lector keyboard-wedge escribe ahí y su `Enter` cae en el listener
   `keydown` de `pago-touch` (~323-389), que con saldo 0 llama `onFinalizar()`. `escanearTarjeta` y
   `EscanearCuponDialog` **no ponen `isDialogOpen`**, única guarda de ese listener. `onFinalizar()` acepta
   líneas con `terminalPosId: null`.
   Datos: filial 1, desde 2026-09-25, **116 de 631** `venta_tarjeta` sin terminal (109 NO_COMPLETADO).
2. **Atajos muertos tras CONVENIO/FIRMA**: `onConvenioClick` (~1130) y `onFirmaClick` (~1173) ponen
   `isDialogOpen = true` y nunca lo vuelven a `false`.
3. **La cancelación no alcanza a la tarjeta.** `VentaService.cancelarVenta` del central (cancela caja,
   stock, delivery, crédito, factura) no toca `venta_tarjeta`; el desktop la cancela aparte, contra el
   **filial**, y solo desde el backoffice. Al reactivar (el método alterna CANCELADA↔CONCLUIDA) nadie la
   restaura.
4. **Cancelar desde el PDV es falso.** «Últimas ventas» llama `cancelarVenta` con `servidor=false` → filial,
   cuyo `VentaService.cancelarVenta` es un stub que devuelve `true`: el cajero ve «Cancelado con éxito» y
   no cambia nada.
5. **El cupón de una venta cancelada queda bloqueado.** `motivoCuponNoUsable` (filial) rechaza por
   `qrCrudo` y por `identificadorTransaccion` sin mirar si la venta está cancelada.
6. **`venta_tarjeta` no vuelve del central a la filial en farmacia.** El registro la marca
   `replicate_central_to_branch_with_filter = t` (igual que `venta`), pero `central_filialN_pub` no la
   incluye (sync de publicaciones apagado en farmacia desde 2026-04-22). Sin eso, lo que el central
   cancele no llega a la filial, donde viven el cierre de caja y el control de duplicados.

Decisiones de Gabriel (2026-09-28): terminal obligatoria **configurable, default `true`**; la cancelación
se hace **en el central**; un cupón de venta cancelada **puede usarse en otra venta**.

## Fases

### F1 — filial (espejo + duplicados + stub)
- `V104.5__espejo_configuracion_venta_tarjeta_terminal_obligatoria.sql`: `ADD COLUMN IF NOT EXISTS
  terminal_obligatoria BOOLEAN` (sin default ni NOT NULL: el espejo no los lleva, central escribe; V96.5).
  Entidad + `.graphqls` (`terminalObligatoria: Boolean`).
- `motivoCuponNoUsable`: el chequeo por `qrCrudo` ignora `venta_tarjeta` en estado `CANCELADO`; el chequeo
  por `identificadorTransaccion` ignora cobros de ventas `CANCELADA` (query nueva que joinea
  `cobro_detalle → cobro → venta`, en vez del derivado plano). El de código de autorización ya mira solo
  COMPLETADO.
- `VentaService.cancelarVenta` (stub): deja de devolver `true`; tira `GraphQLException` «La cancelación se
  hace contra el servidor central». Un desktop viejo ve un error honesto en vez de un éxito falso.
- Tests (`VentaTarjetaServiceTest`): cupón reusado de una venta CANCELADA → permitido; de una CONCLUIDA →
  rechazado (con el código viejo el primero falla). Gate `./mvnw clean verify -B`.

### F2 — central (columna + cancelación en cadena)
- `V232.5__configuracion_venta_tarjeta_terminal_obligatoria.sql`: `ADD COLUMN IF NOT EXISTS
  terminal_obligatoria BOOLEAN NOT NULL DEFAULT true`. Entidad, input, `.graphqls`, PATCH en
  `saveConfiguracionVentaTarjeta` (ya protegido con `seg.requireGestionar()`).
- `VentaService.cancelarVenta`: en la misma transacción, cada `venta_tarjeta` de la venta
  (`findAllByVentaIdAndSucursalId`):
  - al cancelar → `CANCELADO`;
  - al reactivar → estado que corresponde por sus datos: con cupón (`codigoAutorizacion`, `numeroBoleta`,
    `qrCrudo` o `montoEscaneado`) → `COMPLETADO`; sin cupón y `noCompletadoEn != null` → `NO_COMPLETADO`;
    si no → `PENDIENTE`. Regla en un método estático testeable.
- Tests: la regla de reactivación (4 casos) y que cancelar marca CANCELADO. Gate
  `./mvnw clean verify -B -DskipFlyway=true`.

### F3 — desktop (foco, atajos, terminal obligatoria, cancelación)
- `pago-touch`: `isDialogOpen = true` mientras estén abiertos `ScanTerminalPosDialog` y
  `EscanearCuponDialog` (vuelve a `false` al cerrar el último); no llamar `setFocusToValorInput()` cuando
  la línea abrió el escaneo (devolverlo al cerrar); arreglar CONVENIO/FIRMA (`false` al cerrar).
- `scan-terminal-pos-dialog`: `cdkFocusInitial` en el input; si el foco sale del diálogo mientras está
  abierto, volver al input.
- `onFinalizar()`: si `terminalObligatoria` (leída del filial junto con `habilitado`) y hay una línea de
  tarjeta registrable sin terminal → no cierra, avisa y abre la selección de terminal de esa línea. Regla
  pura `lineasTarjetaSinTerminal(...)` con spec.
- ABM: modelo, query y `toInput()` con `terminalObligatoria`; toggle en `configuracion-venta-tarjeta-dialog`.
- **La query de configuración que lee el PDV no puede depender del campo nuevo para `habilitado`**
  (auditoría A): graphql-java valida la selección entera, y un desktop nuevo contra un filial sin F1
  perdería también `habilitado`. `terminalObligatoria` se pide en una query aparte; si falla, se toma
  `true` (el lado seguro) y `habilitado` sigue saliendo de la query de siempre.
- Cancelación: «Últimas ventas» cancela contra el central (`servidor=true`), con manejo de error
  explícito (hoy el subscribe no tiene `error:` y la UI queda muda). `list-venta` y `generic-list-venta`
  **mantienen** `onCancelarPorVentaId` (filial) además de lo que hace el central (auditoría B): es
  redundante e idempotente (los dos escriben CANCELADO) y cubre el caso de revertir el central por debajo
  de F2 con el desktop nuevo. «Últimas ventas» también lo llama. Gate `npm run check`.

### F4 — operación de réplica (farmacia; bodega a verificar)
⚠️ **`copy_data = false`**, NO `true`: el precedente del 2026-09-25 (anexo del runbook) usó `true` en la
dirección opuesta (filial→central). Acá `true` bajaría las 61 filas viejas (id ≥ 100000) a la filial.
Rollback de F4: `ALTER PUBLICATION central_filialN_pub DROP TABLE financiero.venta_tarjeta` + REFRESH
(`copy_data = false`) en cada filial tocada.
Por cada filial 1, 3, 4, 6: en el central `ALTER PUBLICATION central_filialN_pub ADD TABLE
financiero.venta_tarjeta WHERE (sucursal_id = N)`; en la filial `ALTER SUBSCRIPTION
central_filial_farmacia_N_sub REFRESH PUBLICATION WITH (copy_data = false)` — sin copia: la filial ya tiene
sus filas y así no le bajan las 61 de julio (id ≥ 100000). Bodega: verificar si su sync (encendido) ya la
agrega. Filial 5 cuando vuelva.

### F5 — datos, una vez (central, después de F4)
`UPDATE venta_tarjeta SET estado='CANCELADO'` para las de ventas CANCELADA con `id < 100000` que no estén
CANCELADO (filial 1 hoy: 17). El `WHERE` joinea `venta.estado` **al ejecutarse**, no una lista tomada
antes. Pre-check, dry-run, COMMIT. Baja a la filial por F4. Rollback: antes del COMMIT se guardan
`(id, sucursal_id, estado_anterior)` en una tabla de respaldo; volver es un UPDATE desde ella.

## Tabla de datos nuevos
| Dato | Escribe | Lee |
|---|---|---|
| `configuracion_venta_tarjeta.terminal_obligatoria` | central `saveConfiguracionVentaTarjeta` ← desktop `configuracion-venta-tarjeta-dialog` | desktop `pago-touch.onFinalizar` vía la query del filial (réplica MAIN_TO_ALL) |
| `venta_tarjeta.estado = CANCELADO` por cancelación de venta | central `VentaService.cancelarVenta` (y F5) | filial `motivoCuponNoUsable`, cierre de caja (`PENDIENTE` deja de contar), reportes |

## Orden de despliegue
1. filial (espejo de la columna antes que el publisher); 2. central (Deploy farmacia/bodega; alpha
primero); 3. desktop; 4. F4; 5. F5.
**Gate antes de tocar el ABM** (auditoría B): nadie guarda la configuración hasta verificar por
`/api/version` que las filiales activas del canal tienen el release con V104.5. `ADD COLUMN ... DEFAULT`
no genera cambios de fila (no replica nada); el primer UPDATE de la fila sí, y una filial sin la columna
traba toda su suscripción. Filial 5 (apagada) se autocorrige: al volver toma el release en ≤15 min. Desktop nuevo + central viejo: la tarjeta no se cancela (igual que
hoy), por eso central va antes.

## Herederos del cambio (decisión registrada)
- `FacturaLegalGraphQL.cancelarFacturaLegal` con `cancelarVenta=true` (líneas ~1403 y ~1427) llama
  `VentaService.cancelarVenta`: **hereda** la cancelación de la tarjeta. Es lo pedido (cancelar la venta
  cancela toda la cadena).
- mobile-pwa y mobile: solo piden `{ id habilitado }` de la configuración y no llaman `cancelarVenta`
  (clase sin uso / código comentado). Verificado: el stub del filial que pasa a tirar error no los toca.

## Riesgos aceptados
- **Ventana entre F2 y F4**: lo que el central cancele antes de F4 no baja a la filial. F4 va el mismo día
  que el deploy del central, y F5 después de F4 recupera esas filas.
- **Last-write-wins** entre central (cancelar) y filial (completar un PENDIENTE de la misma venta en el
  mismo instante): cada nodo aplica al final la escritura del otro y pueden quedar distintos (central
  COMPLETADO / filial CANCELADO o al revés). No se puede evitar sin dejar de cancelar un COMPLETADO, que
  es lo pedido. Colisión improbable (misma venta, mismo segundo). Detección en el runbook: comparar
  `(id, sucursal_id, estado)` entre central y filial de las `venta_tarjeta` de ventas CANCELADA.

## Fuera de alcance (issues)
- **Delivery con TARJETA** (`edit-delivery-dialog.component.ts:403,497`): se cobra con tarjeta sin
  terminal y sin crear `venta_tarjeta` en absoluto. No respeta ninguna perilla del módulo. Issue aparte:
  es una integración, no un ajuste.
- `VentaCreditoService.cancelarVentaCredito(id, sucId, null)` cancela la venta con `save` directo y se
  salta toda la cadena (caja, stock, factura, tarjeta).
- `reabrirVentaTarjeta` sin rol en backend (#147).

## Qué queda sin verificar
- Runtime del foco con un lector real (se prueba en alpha con el Electron).
- Si bodega tiene `venta_tarjeta` en `central_bodega_filialN_pub` (su sync está encendido).

## Auditoría del plan (paso 5)
| Eje | Hallazgo | Sev. | Qué se hizo |
|---|---|---|---|
| A | Query de config con campo nuevo: desktop nuevo + filial viejo pierde `habilitado` | alta | `terminalObligatoria` en query aparte, default `true` si falla |
| A | Delivery cobra TARJETA sin terminal ni `venta_tarjeta` | alta | Fuera de alcance, issue aparte (es una integración) |
| A | `cancelarFacturaLegal` hereda la cancelación de tarjeta | media | Registrado como decisión |
| A | mobile/mobile-pwa | baja | Verificado: no los toca |
| A | Ventana F2→F4 y last-write-wins | media | Riesgos aceptados, F4 el mismo día, detección en runbook |
| B | Gate pre-ABM por filiales rezagadas | alta | Paso explícito en el orden de despliegue |
| B | `copy_data` equivocado en F4 | media | Advertencia explícita + rollback de F4 |
| B | Carrera cancelar/completar | alta | Riesgo aceptado (cancelar COMPLETADO es lo pedido) + detección |
| B | F5 sobre estado vivo y rollback | baja | WHERE al ejecutar + tabla de respaldo |
| B | Revertir central con desktop nuevo = nadie cancela la tarjeta | alta | El desktop mantiene la cancelación en la filial (redundante) |
| B | «Últimas ventas» sin manejo de error | media | Manejo de error en F3 |
