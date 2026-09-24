# Plan — cerrar la carrera del guard de venta duplicada

Rama: `fix/pdv-venta-duplicada-concurrente` · Repos: **filial** (fase 1) + **desktop** (fase 2, opcional)
Origen: incidente caja 662 / sucursal 4 de farmacia, 2026-09-17.

## 1 · El caso

La caja 662 cerró con una diferencia de **−9.658.000 Gs**, igual peso por peso a las ventas en
efectivo guaraníes del día. Eran **dos ventas gemelas**:

| `cobro_detalle` | cobro | venta | valor | creado_en |
|---|---|---|---|---|
| 42829 | 25526 | 25457 | 4.829.000 | 12:16:41.319 |
| 42830 | 25527 | 25458 | 4.829.000 | 12:16:41.328 |

Mismo producto (11807), misma presentación, misma cantidad (10), mismo precio; huella md5 de los
ítems idéntica. Es el único par gemelo de sucursal 4 desde el 2026-08-01.

Los timestamps prueban que los dos requests se procesaron **intercalados**, no en serie: el
request 2 creó su cobro (.328) 68 ms antes de que el request 1 creara su venta (.396), y los ítems
salieron .523 / .571.

## 2 · Causa raíz

`VentaGraphQL.saveVenta` es un **check-then-act sin lock**:

- `VentaGraphQL.java:204` — consulta el cache
- `VentaGraphQL.java:223` — crea el cobro
- `VentaGraphQL.java:249` — **recién acá** puebla el cache, después de persistir venta e ítems

Entre el chequeo y la inserción hay varios viajes a la base. Los dos requests consultaron el cache
cuando todavía estaba vacío y los dos pasaron. `ConcurrentHashMap` hace atómica cada operación
suelta, pero no la secuencia consultar-y-después-insertar.

El guard solo atrapa repeticiones **secuenciales** (el cajero que reintenta cuando la primera
venta ya terminó, dentro de 5 s). El doble envío concurrente le pasa por al lado por diseño.

`[ev: commit 64f4f51 «Sistema de prevención de ventas duplicadas», 2025-12-26, en develop, master y release/beta desde entonces — estaba desplegado el día del incidente y no lo frenó]`

### Lo que NO es la causa

- No hay bug en el cálculo del balance de caja: la fórmula de `PdvCajaService.generarBalance`
  (`central:PdvCajaService.java:457`) es correcta y los cobros del día reconcilian exactamente
  contra las ventas.
- No hay cobro compartido entre ventas ni doble conteo en la query del sumario.
- **El desktop ya está arreglado**: `3029ca9c` (`fix(pdv): no abrir el pago ni cobrar de nuevo
  mientras se guarda una venta`, Refs #316) levanta `guardandoVenta` de forma sincrónica al
  disparar la mutation y lo baja en `finalize`. El camino desde `onTicketClick` hasta ahí no tiene
  un solo `await`, así que la reentrada del front está cerrada. **Pero solo está en `develop`**:
  `release/beta`, que es lo que corre la farmacia, no lo tiene. Promoverlo es decisión de release,
  fuera de este plan.

### Por qué el backend hace falta igual

Con el front arreglado, el duplicado concurrente ya no sale de *este* cliente. Queda el reintento:
el `subscribe` de `onSaveVenta` (`desktop:venta-touch.component.ts:1384`) **no tiene handler de
`error` de primer nivel**. Ante un fallo de red el `finalize` corre y el POS no queda trabado, pero
el cajero **no recibe aviso**. Ve que no pasó nada y vuelve a apretar. Si la request sí llegó al
filial y lo que se perdió fue la respuesta, eso es un duplicado que ningún guard del front puede
evitar: son dos intentos legítimos separados por segundos. Solo lo frena el backend.

## 3 · Alcance decidido

Reserva-primero sobre el cache en memoria que ya existe. **Sin clave de idempotencia y sin
migración** (decisión de Franco, 2026-09-18).

Lo que esto **no** cubre, explícito:

- No sobrevive a un reinicio del filial.
- No cubre dos instancias de filial contra la misma base.
- No cubre un reintento después de los 5 s de ventana.

La garantía real sería una clave de idempotencia por intento de cobro con `UNIQUE` en la base.
Arrastra central (espejo de migración), filial y desktop, porque `operaciones.venta` es
**BRANCH_TO_MAIN** y exige desplegar el central primero. Queda como trabajo posterior.

## 4 · Diseño

`VentaDuplicadaCacheService` pasa de `Map<usuarioId, List<entradas>>` a
`Map<huella, Entrada>`, donde la huella se deriva de `(usuarioId, ítems normalizados)` — la misma
semántica de comparación que hoy, para no cambiar nada más que la carrera.

Tres operaciones en lugar de dos:

| Operación | Cuándo | Qué hace |
|---|---|---|
| `reservar(usuarioId, items)` | **antes** de `saveCobro` | `putIfAbsent` de la huella. Si ya está y es reciente → duplicado. Si está y venció → `replace` atómico; el que pierde el `replace` es duplicado. Devuelve un token. |
| `confirmar(token, ventaId)` | después de persistir venta e ítems | Le pega el `ventaId` real a la entrada, para que el próximo duplicado lo pueda nombrar. |
| `liberar(token)` | en un `finally`, si no se confirmó | `remove(huella, entrada)` condicional, para que un reintento legítimo no quede bloqueado 5 s. |

La atomicidad la da `putIfAbsent`/`replace`/`remove` condicional de `ConcurrentHashMap`: no hace
falta lock ni `synchronized`.

### D1 · El `finally` hay que crearlo: hoy no existe

**No hay ningún `try/catch` alrededor del tramo que se va a instrumentar.** El único `try` del
método arranca en `VentaGraphQL.java:272`, dentro de la rama `else` — o sea *después* de que la
venta ya se guardó: protege la facturación y la impresión, no la creación.

Entonces el wiring es un **`try { ... } finally { if (!confirmado) liberar(token); }` que envuelve
todo el tramo desde `reservar` hasta `confirmar`**, no un `catch` en el camino conocido. Motivo: no
alcanza con cubrir el `deshacerVenta` de `venta.getId() == null`, porque hay salidas por excepción
que ni siquiera llegan ahí. La comprobada:

- `VentaGraphQL.java:240` — `throw new GraphQLException("Esta caja ya esta cerrada")`, lanzado
  **después** de `saveCobro` y **antes** de que exista `venta.getId()`. Sale directo del método.

Y las no enumerables: cualquier excepción de `cobroGraphQL.saveCobro`, `service.saveAndSend` o
`ventaItemGraphQL.saveVentaItemList`. El `finally` las cubre todas, incluidas las que se agreguen
en el futuro.

Esto importa porque **el rollback de la transacción no alcanza al `ConcurrentHashMap`**: Postgres
deshace la venta, la memoria no. Sin el `finally`, el cajero que reintenta después de corregir la
caja recibiría *"Hay una venta idéntica en curso"* —un mensaje que no tiene nada que ver con la
causa real— hasta que la entrada venza.

### D2 · Una reserva en vuelo NO expira por tiempo

Los 5 s de `TIEMPO_MINIMO_SEGUNDOS` fueron pensados para el caso post-hoc: una venta **ya
confirmada**, y el cajero que reintenta. No sirven para decidir si una reserva **todavía en vuelo**
puede ser reemplazada.

Si se reusara el mismo umbral para las dos cosas y `saveCobro` + `saveAndSend` +
`saveVentaItemList` tardaran más de 5 s —contención de la base, pool saturado, replicación—, un
segundo request idéntico ganaría el `replace` y crearía la venta gemela **mientras la primera
sigue en curso**: exactamente el incidente de la caja 662, ahora disparado por lentitud en vez de
por falta de lock.

Por lo tanto:

| Estado de la entrada | ¿Puede reemplazarse por vencimiento? |
|---|---|
| Confirmada (tiene `ventaId`) | Sí, pasados los 5 s |
| En vuelo (sin `ventaId`) | **No.** Solo la suelta `liberar()` o el `@Scheduled` de 1 minuto |

El `@Scheduled` queda como única red de seguridad para una reserva colgada —hilo muerto entre
`reservar` y `confirmar`/`liberar`— y su ventana de 1 minuto es holgada frente a cualquier venta
lenta razonable.

Mensajes de error, según el estado de la entrada previa:

- con `ventaId` → `"Ya existe una venta similar creada recientemente (ID: %d)..."` (el de hoy)
- sin `ventaId` (todavía en vuelo) → `"Hay una venta idéntica en curso. Espere a que termine antes de reintentar."`

De paso se corrige un NPE latente: hoy `Comparator.comparing(ItemInfo::getProductoId)` revienta si
un ítem trae `productoId` o `presentacionId` en null, y eso mata la venta entera. La huella se
arma con formateo null-safe.

`limpiarCache()` (`@Scheduled` cada minuto) se mantiene, ahora sobre el mapa plano. Una reserva
que quedó colgada —hilo muerto entre `reservar` y `confirmar`/`liberar`— se limpia al minuto.

### Tabla de datos nuevos

**N/A**: esta fase no crea ninguna columna, campo de GraphQL ni valor de enum. Todo el estado es en
memoria, dentro del proceso del filial. **Sin migración Flyway y sin espejo en central.**

## 5 · Fases

### Fase 1 — filial (el fix)

1. Reescribir `VentaDuplicadaCacheService` con `reservar` / `confirmar` / `liberar`.
2. Cablear en `VentaGraphQL.saveVenta`: `reservar` antes de `saveCobro`, `confirmar` donde hoy
   está `agregarVentaAlCache`, `liberar` en el camino de error (incluye el `deshacerVenta` de
   `venta.getId() == null`).
3. Tests (`VentaDuplicadaCacheServiceTest`, JUnit 5, sin contexto Spring — igual que las 14 clases
   que ya tiene el repo):
   - **concurrencia**: N hilos con la misma huella largados con un `CountDownLatch`; exactamente
     uno reserva. Es el test que reproduce la caja 662 y el que hoy fallaría.
   - secuencial: reservar dos veces seguidas → la segunda es duplicado.
   - `liberar` permite volver a reservar de inmediato.
   - `confirmar` deja el `ventaId` disponible para el mensaje.
   - vencida la ventana, la huella vuelve a estar libre (reloj inyectable).
   - ítems con `productoId`/`presentacionId` null no tiran NPE.
   - **una excepción intermedia libera la reserva en el acto**: simular el fallo de caja cerrada
     entre `reservar` y `confirmar` y verificar que la huella queda libre de inmediato, no recién
     a los 5 s. Cubre D1.
   - **una reserva en vuelo no se puede reemplazar por vencimiento**: con el reloj adelantado más
     allá de la ventana, un segundo `reservar` sobre una entrada sin `ventaId` sigue dando
     duplicado. Cubre D2.

Gate: `./mvnw clean verify -B` (el mismo comando del CI).
Cierre: commit + push de la rama. **Sin PR** hasta que Franco pruebe y apruebe.

### Fase 2 — desktop (opcional)

Handler de `error` de primer nivel en el `subscribe` de `onSaveVenta`
(`venta-touch.component.ts:1384`), para que una venta fallida avise en vez de quedar muda. Patrón
del #300: `{ networkError: { show: true, propagate: true } }` + `subscribe({ next, error })`
(gotcha #190 del desktop).

Gate: `npm run check` (AOT, redirigido a archivo — el proceso queda vivo al terminar).

Si se cae esta fase, el trabajo queda en un solo repo y no cambia nada de la fase 1.

## 6 · Qué queda sin verificar

- **No se puede reproducir contra producción.** El test de concurrencia reproduce el mecanismo en
  el `VentaDuplicadaCacheService`, no el incidente punta a punta con dos requests GraphQL reales.
- **No se verificó contra qué versión corre hoy el filial de sucursal 4.** Requiere acceso a
  producción. Importa para saber desde cuándo la farmacia está expuesta, no para el fix.
- La fila de `operaciones.venta` en `configuraciones.replication_table` no se pudo leer (la copia
  local la tiene vacía). Irrelevante para esta fase porque no hay migración; habría que leerla si
  alguna vez se hace la clave de idempotencia.
- **NPE preexistente, fuera de alcance**: `VentaGraphQL.java:221` declara `Venta venta = null` y
  solo la asigna dentro de `if (cobro != null)`. Si `saveCobro` devolviera null sin lanzar, la
  línea 264 (`venta.getId()`) tira NPE antes de llegar a `deshacerVenta`. No se arregla en esta
  fase; el `finally` de D1 hace que, si pasa, la reserva igual se libere.
- **Cambio de mensaje visible para el cajero**: con reserva-primero, el rechazo ocurre antes de
  `saveCobro`, así que un reintento dentro de la ventana puede ver *"Hay una venta idéntica en
  curso"* donde antes veía *"Ya existe una venta similar creada recientemente (ID: n)"*. La
  semántica de negocio no cambia. Anotado para que QA no lo lea como regresión.
- **Falso positivo conocido, preexistente y no tocado**: dos ventas legítimamente idénticas del
  mismo cajero dentro de 5 s se rechazan. En una farmacia es posible. Lo resolvería la clave de
  idempotencia; este plan no lo cambia para no mezclar el fix de la carrera con un cambio de
  semántica.

## 7 · Promoción a producción

Este fix nace en `develop`, igual que el arreglo del desktop (`3029ca9c`). Al mergear —con prueba y
aprobación de Franco— sale a las **filiales alpha en ≤15 min sin aprobación humana**. La farmacia
no lo recibe: corre `release/beta`.

Queda entonces una brecha explícita: **la sucursal 4 sigue expuesta hasta que alguien promueva a
`release/beta`**, y ahí el guard del backend y el del desktop conviene que viajen juntos, porque
hoy los dos están en la misma situación (arreglados en `develop`, ausentes en beta). Las 18
filiales de bodega (`master`) van después.

Se evaluó ir por `hotfix/*` desde `master` y se descartó (decisión de Franco, 2026-09-18): es un
caso en seis semanas y el hueco lleva nueve meses abierto, así que la urgencia no justifica
saltear el laboratorio. **Promover a beta es una decisión de release, posterior a este plan y no
incluida en él.**

## 8 · Hallazgos de la auditoría del plan (paso 5)

Dos auditores, sin verse. Los cinco hallazgos se verificaron contra el código antes de aplicarlos.

| # | Eje | Hallazgo | Qué se hizo |
|---|---|---|---|
| 1 | B | El `catch` que el plan daba por existente no existe: el único `try` (`:272`) está después de guardar la venta | Aceptado → **D1**, con `try/finally` en vez de `catch` |
| 2 | A | El `throw` de caja cerrada (`:240`) sale del tramo reservado sin pasar por `deshacerVenta` | Aceptado → **D1**, es la evidencia de por qué va `finally` y no `catch` |
| 3 | B | Reusar los 5 s para expirar una reserva en vuelo reabre la carrera bajo lentitud | Aceptado → **D2** |
| 4 | A | El plan no decía cómo se promueve a la farmacia, que es donde pasó el incidente | Aceptado → sección 7 |
| 5 | B | El mensaje de error cambia para el cajero legítimo | Aceptado como nota en la sección 6 |

Correcciones menores: la cita del commit `64f4f51` decía 2026-12-26; es **2025**-12-26 (verificado
con `git log`). El NPE preexistente de `:264` quedó declarado fuera de alcance.

## 9 · Estado de los pasos del ciclo

| Paso | Estado |
|---|---|
| 1 Rama | hecho — `fix/pdv-venta-duplicada-concurrente` desde `develop` en filial; el desktop se ramifica al llegar a la fase 2 |
| 2 Skill de dominio | hecho — `frc-filial`, `frc-desktop`, `frc-financiero-expert` |
| 3 Análisis | hecho — código > gotchas > skill; el código corrigió a mi propia auditoría previa (había leído el desktop en una rama atrasada) |
| 4 Plan | este archivo |
| 5 Auditoría del plan | hecho — 2 agentes, 5 hallazgos, todos verificados y aplicados (sección 8) |
| 6 Presentar y commitear | en curso |
| 7–12 | pendientes |
