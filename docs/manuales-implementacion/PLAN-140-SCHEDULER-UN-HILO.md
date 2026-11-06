# Plan #140 — las tareas `@Scheduled` comparten un solo hilo

Rama: `fix/scheduler-update-hilo-propio` (desde `origin/develop` 7502560). Pieza: filial. Sin
cambios en central, desktop ni PWA.

## Qué dice la issue y qué se verificó

La issue propone copiar el patrón del central #323 (executor propio + `Future.get(timeout)` +
guard) tarea por tarea, empezando por `UpdateService` porque «el que se cuelga es el que repara».
Esa premisa **no se cumple en el código actual**:

| Afirmación de la issue | Verificado |
|---|---|
| Un solo hilo de scheduler | **Sí.** `jstack` del filial de `/opt` (pid local) muestra un único `scheduling-1`, del `ThreadPoolTaskScheduler` auto-configurado de Boot 2.7.18. Ningún otro bean `TaskScheduler`/`SchedulingConfigurer` en `src/main`. |
| `UpdateService` descarga el JAR y reinicia | **No.** El cuerpo de `checkForNewRelease()` está entero comentado desde `b0f957c` (2024-09-23): es un no-op. |
| Sin `UpdateService` no llega la versión nueva | **No.** El auto-update es `frc-cicd/scripts/check-update.sh` en el host: baja el release, `systemctl restart` y health check por HTTP (`/actuator/health`, `/api/version`), que atienden hilos de Tomcat, no el del scheduler. |
| SIFEN puede retener el hilo «por minutos» | **Sí, acotado.** jsifenlib 0.2.4-frc.13 trae `httpConnectTimeout=15000` y `httpReadTimeout=45000` por pedido; sin cota la resolución DNS. Con muchos lotes son minutos. |
| `pg_dump` con `waitFor()` | **Sí**, una vez por día; también la subida a Google Drive en el mismo hilo. |

Lo que sí sigue siendo verdad: cualquiera de las 4 tareas reales que se trabe frena a las otras
tres — SIFEN, backup, purga de imágenes y limpieza del cache de ventas duplicadas. La última
importa en el PDV: una reserva «en vuelo» que quedó colgada solo la libera esa limpieza
(`VentaDuplicadaCacheService:78`, no expira al leer), así que mientras SIFEN retiene el hilo el
cajero puede quedar bloqueado repitiendo la misma venta.

## Por qué subir el pool y no el patrón de #323

La issue descarta subir el pool porque «convierte 5 tareas serializadas en concurrentes» y el
backup podría solaparse con el auto-update. Revisado tarea por tarea:

- **Una misma tarea nunca corre dos veces a la vez**, con cualquier tamaño de pool:
  `fixedRate` usa `scheduleAtFixedRate` (el JDK garantiza que no solapa), y `fixedDelay`/`cron`
  reprograman recién al terminar.
- **Entre tareas no hay estado compartido**: SIFEN escribe lotes/DE (con su propio flag);
  backup lanza un `pg_dump` (snapshot MVCC, no bloquea escrituras); la purga borra imágenes de
  cupón; la limpieza recorre un `ConcurrentHashMap` que ya comparte con los hilos HTTP.
- **El «reinicio en mitad del `pg_dump`»** no depende del pool: lo hace `check-update.sh` desde
  afuera del proceso, hoy mismo, con un hilo o con cinco.
- Conexiones: a lo sumo 3 tareas con base a la vez (SIFEN, purga, backup lanza un proceso
  aparte); Hikari por defecto tiene 10.

Con eso, el cambio mínimo que resuelve la issue es una property. El patrón de #323 agrega
executor + timeout + guard por tarea (~100 líneas cada una) para cubrir lo mismo.

## Fases

### Fase 1 — pool de 5 hilos + test (`fix(scheduler): ...`)

- `src/main/resources/application.properties`: `spring.task.scheduling.pool.size=5`
  (una por `@Scheduled`, incluido el no-op de `UpdateService`), con un comentario de una línea
  del porqué.
- Test nuevo `SchedulerPoolTest` (JUnit, sin contexto Spring): lee `application.properties` y
  cuenta los métodos `@Scheduled` de `com.franco.dev` leyendo el bytecode
  (`PathMatchingResourcePatternResolver` + `CachingMetadataReaderFactory`, sin cargar clases:
  `Class.forName` dispararía los estáticos de jsifenlib/Drive); exige que el conteo sea 5 (si
  diera 0 el escaneo está roto y el test pasaría siempre) y `pool.size >= conteo`. **Falla con el
  código viejo** (property ausente → default 1 < 5), y falla si alguien agrega una sexta tarea sin
  subir el pool. La property vive solo en `application.properties`; `application.yml` no define
  nada de `spring.task.*`.

## Datos nuevos

| Dato | Escribe | Lee |
|---|---|---|
| `spring.task.scheduling.pool.size` | `application.properties` | `TaskSchedulingAutoConfiguration` de Boot → hilos `scheduling-1..5`; `SchedulerPoolTest` |

## Pruebas

1. `./mvnw clean verify -B` (gate del CI), leído del log.
2. Revertir la property y confirmar que `SchedulerPoolTest` falla.
3. Runtime: levantar el filial de la rama en 8082 (perfil `dev`, `sifen.enabled=false`,
   `frc.purga-imagenes.enabled=true` en simulación, `frc.purga-imagenes.initial-delay=30000`,
   log DEBUG de `VentaDuplicadaCacheService`) con JDWP. Con `jstack` ver `scheduling-1..N`.
   Frenar la purga con un breakpoint que suspenda **solo su hilo** (`stop thread in` de jdb; el
   `stop at` común congela todos los hilos y daría un falso negativo) y, mientras está frenada,
   confirmar con `jstack` en loop que `limpiarCache` sigue corriendo en otro `scheduling-N`.
   Contraprueba: lo mismo con el JAR de `develop` muestra la limpieza parada detrás de la purga.

## Qué queda fuera

- `UpdateService` muerto: el `@Scheduled` no-op y el código comentado se podrían borrar, pero es
  otra limpieza; queda anotado acá.
- Cota de tiempo a SIFEN (DNS sin timeout): con pool propio ya no frena a nadie más; el DNS
  colgado solo frena al propio SIFEN, que es lo mismo que pasa hoy.
- El runbook `frc-cicd/runbook-stuck-filial-diagnosis.md` que cita la issue no existe en el clon
  de `frc-cicd`; `frc-cicd` es solo lectura en esta máquina.

- `procesandoLotes` de SIFEN es check-then-set no atómico: una ejecución manual por GraphQL
  (`ejecutarManualmente`) y la del scheduler pueden solaparse. Pasa igual con 1 hilo (la manual
  corre en un hilo HTTP); se cierra con `AtomicBoolean.compareAndSet`, en otra issue.
- `isFirstRun` del backup saltea el primer disparo de cron después de cada reinicio.

## Canal y despliegue

| Canal | Rama | Máquinas | Qué prueba |
|---|---|---|---|
| alpha | `develop` | 1 filial de laboratorio (mauro :8080), ≤15 min sin gate humano | que arranca y abre `scheduling-1..5` |
| beta | `release/beta` | 6 filiales de farmacia, facturan | la concurrencia real (SIFEN con carga) |
| stable | `master` | 18 filiales de bodega, la 4 en Windows (`check-update.ps1`) | — |

Después de cada salto: `jstack <pid> | grep '"scheduling-'` tiene que mostrar más de un hilo
(incluida la filial 4 al llegar a bodega); detecta además un host con config manual que pise la
property. Requiere reinicio (lo hace `check-update.sh`/`.ps1`). Un reinicio puede cortar varias
tareas a la vez: SIFEN retoma el lote en `procesarLotesAtrasados`, la purga borra archivo por
archivo y el backup deja un `.sql` parcial local sin subir; sin pérdida de datos. En la mayoría
de las filiales la purga y el backup están apagados por defecto, así que compiten 2–3 tareas.
Rollback: el JAR anterior trae la property ausente y vuelve a 1 hilo; no hay esquema ni datos
que deshacer.

## Auditoría del plan (paso 5)

| Eje | Veredicto | Hallazgo | Qué se hizo |
|---|---|---|---|
| A | aceptar con reservas | Media: canal descrito solo como alpha | tabla de 3 canales + chequeo `jstack` por salto |
| A | | Baja: `spring.config.location`/overlay manual podría pisar la property | ningún script lo usa; lo detecta el chequeo `jstack` |
| A | | Baja: purga y backup apagados por defecto | texto corregido |
| B | aceptar con reservas | Media: breakpoint de jdb congela todos los hilos; purga/backup no disparan en la ventana | prueba 3 reescrita (`stop thread`, `initial-delay`, DEBUG, contraprueba) |
| B | | Media: escaneo con `Class.forName` frágil, sin piso | escaneo por bytecode + conteo exacto 5 |
| B | | Baja: reinicio corta varias tareas a la vez | anotado en despliegue |
| B | | Baja: `procesandoLotes` no atómico, `isFirstRun` | fuera de alcance, anotado |

Sin contradicciones entre ejes. Ninguno encontró estado compartido entre tareas ni dependencia
del central/desktop en el orden del scheduler.

## Pasos del ciclo

- Paso 8 condicionales: N/A (no toca release ni replicación).
- Migración: N/A, no hay DDL.
