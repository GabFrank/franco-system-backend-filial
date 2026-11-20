# Plan #150 — la purga de fotos de cupón mira la misma carpeta que la captura

Issue: GabFrank/franco-system-backend-filial#150. Rama `fix/venta-tarjeta-purga-misma-carpeta`
desde `develop` (3853816).

## Problema

`frc.captura.ruta-imagenes` no está configurada en ningún `.properties` ni en `frc-cicd`, así que
rige el default, y cada clase tiene el suyo:

| clase | default | uso |
|---|---|---|
| `CapturaCuponService:110` | `cupones` | guarda `cupones/yyyy/MM/<id>.jpg` (ruta relativa al directorio de trabajo) en `captura_cupon.imagen_url` |
| `PurgaImagenesCuponService:50` | `capturas` | `dentroDeLaCarpeta()` exige que la ruta caiga bajo `capturas/` |

Con `dias_retencion_imagenes` configurado, toda candidata cae en «fuera de la carpeta de capturas»
y no se borra nada: el disco que comparte con Postgres no queda protegido.

## Decisión: el default es `cupones`

Es la carpeta donde **ya están** las fotos que existan en cualquier filial. Cambiar la captura a
`capturas` dejaría las fotos existentes fuera del alcance de la purga para siempre.

## Fase única — commit `fix(venta-tarjeta): ...`

1. `CapturaCuponService`: constante pública `RUTA_IMAGENES = "${frc.captura.ruta-imagenes:cupones}"`
   (placeholder completo, con un comentario que explique por qué es compartida, citando #150).
2. Los dos constructores usan `@Value(CapturaCuponService.RUTA_IMAGENES)`.
3. Test nuevo `RutaImagenesCuponTest`: lee por reflexión el `@Value` del parámetro `rutaImagenes`
   de los dos constructores y exige que sean iguales y que el default sea `cupones`.
   - **Revertir el fix → el test falla** (`cupones` ≠ `capturas`). Se comprueba antes del commit.
4. (Auditoría eje B) Caso nuevo en `PurgaImagenesCuponServiceTest` con `rutaImagenes` **relativa**
   y una ruta armada como la arma `guardar()` (`Paths.get(base, "yyyy/MM", "<id>.jpg")`): el test
   existente solo usa rutas absolutas y no cubre el formato que se guarda en producción.
5. (Auditoría eje B) En simulación, `log.info` de **cada ruta** que borraría. El javadoc del
   servicio y del scheduler promete que la simulación «loguea exactamente lo que borraria», y hoy
   solo da conteos: el operador no puede revisar qué se llevaría antes de pasar a `simulacion=false`.

## Riesgo que el fix destapa (auditoría eje B) — fuera de alcance, issue aparte

Con la carpeta alineada, la purga pasa a poder borrar. Una foto solo queda protegida si
`completar()` recibió el `capturaToken` (`VentaTarjetaService.java:146`). Hay fotos de cupones
cobrados que llegan sin token:

- el OCR no separa ningún campo → el desktop descarta el token
  (`registrar-venta-tarjeta-dialog.component.ts:687`) y el cajero carga a mano;
- capturas anteriores a `e50c66c` (antes de que existiera la copia a `venta_tarjeta.imagen_url`).

Esas fotos son huérfanas para la purga. No lo agrega este fix (es el criterio con que se diseñó la
purga), y la purga sigue apagada por default y arranca en simulación; con el punto 5 la simulación
muestra cada ruta antes de borrar. Se abre un issue aparte (desktop: no soltar el token al pasar a
carga manual) en vez de mezclarlo acá.

## Tabla de datos nuevos

N/A: no nace ningún campo, columna ni clave. La propiedad ya existía y ya tenía escritor
(`CapturaCuponService.guardar`) y lector (`PurgaImagenesCuponService.dentroDeLaCarpeta`); solo se
alinea su default.

## Migraciones / replicación / multi-repo

N/A para el filial: no toca esquema. N/A para central y desktop: la carpeta y la purga son solo del
filial (central tiene `CapturaMuestraPurgaScheduler`, en memoria, sin disco).

## Tests y prueba de runtime

- `./mvnw clean verify -B` (en una copia fuera de `dev-frc`, por jdtls).
- Runtime, filial local en 8082 con perfil dev, SIFEN apagado, sucursal 24:
  1. Arrancar con `frc.purga-imagenes.enabled=true`, `simulacion=true`, `initial-delay` corto.
  2. Crear una captura real (`crearCapturaCupon` + `POST /public/captura/{token}` con un JPEG):
     la foto queda en `cupones/yyyy/MM/<id>.jpg`.
  3. Por SQL: `creado_en` de esa captura a 10 días atrás y `dias_retencion_imagenes = 1`.
  3b. (Auditoría eje B) Una segunda captura **completada** por `completarVentaTarjeta` con su
      `capturaToken`, también envejecida: tiene que quedar protegida.
  4. Esperado con el fix: `[SIMULACION] 2 candidatas, 1 protegidas por una venta, 1 borradas` y la
     línea con la ruta de la huérfana; los dos archivos siguen en disco. Comparar con el jar de
     `develop`: «se salteo ... fuera de la carpeta de capturas» y 0 borradas.
  4b. Con `simulacion=false`: la huérfana desaparece del disco, la protegida queda.
  5. Restaurar `dias_retencion_imagenes`, la venta de prueba, las capturas y las fotos.

## Resultados (2026-11-20)

- `./mvnw clean verify -B`: 1080 tests, 0 fallos (copia fuera de `dev-frc`). Con el default viejo
  restituido, `RutaImagenesCuponTest` falla: `expected cupones but was capturas`.
- Runtime, filial local 8082 / sucursal 24, dos capturas reales por `crearCapturaCupon` +
  `POST /public/captura/{token}` (2 huérfana, 3 completada con `completarVentaTarjeta(1080)` y su
  token), envejecidas 10 días, `dias_retencion_imagenes = 1`:
  - fix, simulación: `borraria cupones/2026/11/2.jpg` y `[SIMULACION] 2 candidatas, 1 protegidas
    por una venta, 1 borradas, 85 KB`; los dos archivos en disco.
  - `develop`, simulación: `se salteo cupones/2026/11/2.jpg porque esta fuera de la carpeta de
    capturas` y `0 borradas` — el bug reproducido.
  - fix, `simulacion=false`: `1 borradas`; queda solo `3.jpg` (la protegida).
  - Restaurado: venta 1080 y configuración idénticas a la foto previa (diff vacío), capturas 2 y 3
    borradas, fotos borradas; sin eventos en `outbox_evento`.
- Auditoría del diff (paso 8): Fijo 1, 2 y 3 N/A (no toca resolver, `.graphqls` ni migración);
  condicionales no disparados. Revisor de correctitud: aprueba; se aplicaron limpieza del test
  relativo en el `finally` y el texto «carpeta de imagenes» en el aviso; descartado un test extra
  de simulación relativa (mismo camino, cubierto en runtime).

## Despliegue

El filial se propaga solo (alpha ≤15 min tras merge a `develop`). Sin cambio de comportamiento
mientras nadie configure `dias_retencion_imagenes` ni prenda `frc.purga-imagenes.enabled`.

## Sin verificar

- Si alguna filial real tiene `frc.captura.ruta-imagenes` puesta por argumento o `.env`: no se
  revisaron los hosts. Si alguna la tiene, las dos clases ya leían el mismo valor y el fix no la
  afecta.
- (Auditoría eje A) El directorio de trabajo de la tarea `FRC-Filial-Server` en las filiales
  Windows: `check-update.ps1` solo hace `Start-ScheduledTask`; `start-filial.ps1` fija
  `C:\frc-filial`, `start-filial.bat` no. Si arranca en otro directorio, `cupones/` cae ahí —ya
  pasaba antes del fix, que no lo cambia— y la purga, que resuelve contra el mismo directorio,
  sigue alineada; si el directorio cambia entre captura y purga, `f.exists()` da falso y se
  saltea (no borra de más). La prueba de runtime es solo Linux.
