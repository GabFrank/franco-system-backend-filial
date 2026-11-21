# Plan — issue #151: la captura de cupón respeta `habilitado` y el PDV no ofrece el flujo contra central

Ramas: filial `fix/venta-tarjeta-captura-respeta-habilitado`, desktop
`fix/venta-tarjeta-pdv-sin-flujo-contra-central`, las dos desde `develop`. Dos PRs, sin orden entre
ellos: no hay migración, no hay tabla nueva ni columna que viaje por replicación, y cada lado se
defiende solo.

## Qué resuelve

1. **`habilitado=false` era solo interfaz.** Las tres puertas del filial que abren o procesan una
   captura no miran `configuracion_venta_tarjeta.habilitado`:
   - `CapturaCuponService.crear()` ← mutation `crearCapturaCupon` (desktop logueado);
   - `CapturaCuponService.procesar()` ← `POST /public/captura/{token}` (teléfono, sin sesión);
   - `CapturaCuponService.derivarMapa()` ← mutation `derivarMapaDeCaptura` (hoy sin llamador en
     desktop ni PWA, pero corre el OCR igual).
   Un token emitido con el módulo prendido seguía sirviendo los `minutosValidezCaptura` después de
   apagarlo, y cualquier cliente que mostrara el botón lo emitía con el módulo apagado.
2. **Cajas con `isLocal=false`.** `pago-touch` lee la configuración con `servidor=false`, pero sin
   link local esa consulta va al central, que la tiene y responde `habilitado=true`. El PDV arranca
   entonces el flujo completo contra un servidor que no tiene `crearCapturaCupon` ni
   `completarVentaTarjeta`, y «Registrar más tarde» llama al `saveVentaTarjeta` del central (setter
   de la app móvil vieja), que escribiría `venta_tarjeta` en el central — tabla que solo escribe el
   filial (BRANCH_TO_MAIN).

## Fases

### Fase 1 — filial: guarda única en el servicio

- `CapturaCuponService`: método privado `exigirModuloHabilitado()` que lee
  `configuracion.findOrDefault().getHabilitado()` y, si no es `TRUE`, lanza
  `IllegalStateException("la venta con tarjeta esta deshabilitada")`. Sin «en esta sucursal» (la
  fila es una sola, MAIN_TO_ALL) y sin «carga el cupon a mano»: en una terminal con
  `cargaManualPermitida=false` ese botón no se ve (auditoría eje A/B).
  - Se llama como **primera línea** de `crear()` y de `derivarMapa()`.
  - En `procesar()`, **después** del atajo `LISTO` (un reintento sobre algo que ya salió bien
    sigue devolviendo el resultado: el cupón ya se leyó con el módulo prendido) y **antes** de
    validar caja y OCR.
  - **Sin `catch`**, a diferencia de `minutosValidez()`: ahí fallar abierto era lo correcto (un
    número de minutos no puede impedir una foto); acá es la perilla de apagado. Si no se puede leer
    la configuración, `crear` tampoco podría guardar la captura (misma base).
  - `findOrDefault()` sin fila replicada devuelve `habilitado=false`: un filial recién migrado
    queda apagado, igual que ya lo trata `CuponOcrService`.
- Mensajes: el controller ya convierte `IllegalStateException` en `410` con el texto para el
  teléfono; la mutation lo propaga como error GraphQL que el diálogo del desktop muestra.
- **No** se toca el `GET` de la página de captura: sin token válido no hace nada y el `POST` ya
  rechaza.

Tests (JUnit 5 + Mockito, nuevo `CapturaCuponHabilitadoTest`, patrón de
`FacturaElectronicaHabilitadaTest`). **Caja abierta y `ocr.disponible()=true` mockeados, y se
compara el mensaje exacto**: con mocks por defecto `cajas.findById` vacío ya lanza «caja no está
abierta» y el test pasaría con el código viejo (auditoría eje B, MAJOR 2).
- `crear` con módulo apagado lanza el mensaje y **no** llama a `repository.save`;
- `crear` con la configuración por defecto (sin fila) lanza el mismo mensaje;
- `procesar` con token vigente (ESPERANDO) y módulo apagado lanza, no llama a `ocr.leer` ni a
  `save`, y no toca `intentos`, `estado` ni `usadoEn`;
- `procesar` sobre una captura en ERROR (token vivo) con módulo apagado lanza;
- `procesar` sobre captura ya `LISTO` devuelve el resultado aunque el módulo esté apagado;
- `derivarMapa` con módulo apagado lanza;
- `crear` con módulo prendido guarda la captura.
- Revertir la guarda y comprobar que fallan los cinco que esperan el rechazo.

Nota (preexistente, no se toca): cuando el `POST` responde 410 el controller no publica nada por la
subscription, así que el diálogo del desktop sigue «esperando» y el motivo lo ve solo el teléfono.
Pasa igual hoy con la caja cerrada.

### Fase 2 — desktop: el PDV no ofrece el flujo si la caja no es local

- `pago-touch.component.ts`, en la suscripción a `onGetConfiguracion(false, …)`:
  `ventaTarjetaHabilitada = config.habilitado === true && this.mainService.isLocal()`, con el
  comentario del porqué. TARJETA queda como forma de pago normal — el mismo comportamiento que con
  el módulo apagado —, `terminalObligatoria` deja de exigir terminal (ya va con `&&`), y no se abre
  ni el escaneo ni «Sacar foto».
- `configTarjeta` sigue en `ok`: no es un fallo de configuración, es una caja que no puede operar el
  módulo; no hay que bloquear el cobro.
- Las otras puertas ya lo cubren: `ventas-tarjeta-caja-dialog` y `list-venta-tarjeta` entran en
  `modoLectura` con `!isLocal()`.

### Fase 3 — desktop: completar un pendiente con el módulo apagado

`registrar-venta-tarjeta-dialog` abre la captura apenas aparece (`ngOnInit`, `if (ofreceCamara)
onSacarFoto()`) y oculta la carga a mano si la terminal tiene `cargaManualPermitida=false`; para
una terminal `MAQUINA` así, la foto es el único camino. Con la guarda de la fase 1, un pendiente
que quedó de antes de apagar el módulo abriría con un error y sin botón (auditoría eje B, MAJOR 1).
Se llega desde `ventas-tarjeta-caja-dialog` y `list-venta-tarjeta`, que no miran `habilitado`.

- El diálogo consulta `ConfiguracionVentaTarjetaService.onGetConfiguracion(false)` al abrir. Con
  `habilitado !== true`: `ofreceCamara=false`, `ofreceCargaManual=true`, y no se pide captura. Con
  `true` o si la consulta falla: igual que hoy (se abre la foto). El filial no valida la carga a
  mano del lado del servidor, así que alcanza con el desktop.
- `escanear-cupon-dialog` no se toca: solo lo abre el PDV, y el PDV no lo abre con el módulo
  apagado (lee `habilitado` al iniciar el cobro). Si lo apagan con un cobro en curso, el error de
  la guarda aparece y queda «Registrar más tarde».

Tests: N/A para desktop porque Karma está roto (`require.context`) y el CI no corre specs
[ev: frc-pos-expert «Karma está roto»]. Gate: `npm run check` + prueba por pantalla.

## Datos nuevos

Ninguno. No hay columna, campo GraphQL, enum ni configuración nueva: se lee `habilitado`, que ya
escribe el central (ABM «Configuración» de Gestión de POS) y ya leen `CuponOcrService`, el PDV y
Utilitarios.

## Prueba de runtime (local)

Filial propio en `:8082` (sucursal 24, `sifen.enabled=false`), central local en `:8081`, desktop
`ng serve -c web`. Se respalda y restaura `financiero.configuracion_venta_tarjeta` de la base del
filial (se cambia por SQL: el filial no la escribe por la app).

1. `habilitado=false` → `crearCapturaCupon` por GraphQL → error con el mensaje.
2. `habilitado=true` → `crearCapturaCupon` devuelve token y URL; `POST` de un JPEG real →
   `200`/`422` según lo que lea el OCR.
3. Con un token vigente, pasar a `habilitado=false` → `POST` → `410` con el mensaje; la captura no
   cambia de estado.
4. PDV con `isLocal=true` y módulo prendido → agregar TARJETA abre el escaneo de terminal.
5. PDV con `isLocal=false` → agregar TARJETA no abre nada y la venta se puede finalizar.
6. Con un cobro PENDIENTE de una terminal `MAQUINA` con `cargaManualPermitida=false`: módulo
   prendido → el diálogo abre la foto; módulo apagado → abre con la carga a mano, se completa, y
   la caja cierra.
7. Restaurar la configuración, la terminal y el `localStorage`.

## Despliegue y rollback

- Filial: merge a `develop` sale a las filiales alpha en ≤15 min. Rollback = revertir el commit;
  no hay estado que deshacer.
- Desktop: diálogo «Cerrar y actualizar». Un desktop viejo contra un filial nuevo sigue igual que
  hoy salvo que, con el módulo apagado, una captura ya no se abre (lo que se quería).

- La perilla de apagado real sigue siendo `habilitado` en el central, y llega a cada filial por
  replicación: una filial que no replica (p. ej. la 25) no se entera del cambio.

## Sin verificar

- Cuántas cajas en producción tienen `isLocal=false` y cobran con tarjeta. Desde esta versión sus
  cobros con tarjeta dejan de pedir terminal y cupón y no crean `venta_tarjeta`; el cobro sigue en
  `cobro_detalle` como TARJETA. Antes «Registrar más tarde» iba al `saveVentaTarjeta` del central
  (existe: `venta-tarjeta.graphqls:78`) y pudo escribir filas en el `venta_tarjeta` del central,
  contra el sentido BRANCH_TO_MAIN. Se verificaría con un `SELECT` de solo lectura en los dos
  centrales buscando filas sin par en ninguna filial; un desktop viejo con `isLocal=false` lo
  sigue haciendo hasta actualizarse.
