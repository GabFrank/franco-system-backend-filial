# Plan — fix #77: el filial guarda `inicio_sesion` siempre con su propia sucursal

Issue: GabFrank/franco-system-backend-filial#77. Relacionadas: desktop#180 (PR desktop #297, guard del
cliente), central#287 / central PR #289 y filial PR #124 (reparto de ids impar/par).
Rama: `fix/replicacion-inicio-sesion-sucursal-central` (desde `origin/develop` `a4804fe`).

## Problema

`InicioSesionGraphQL.saveInicioSesion` persiste la sucursal que manda el cliente, sin validar. Cuando
llega `sucursalId = 0` (identidad de central), el filial guarda `(id, 0)`. Esas filas suben por
`filialN_pub` y chocan en central (`inicio_sesion_pk`), cortando **toda** la suscripción filial → central.

## Hechos verificados (paso 3)

| Hecho | Evidencia |
|---|---|
| El resolver es pass-through: `sucursal` = lo que manda el cliente, o nada | `InicioSesionGraphQL.java:48-57` (develop) |
| `saveAndSend` solo guarda; la propagación está comentada | `InicioSesionService.java:48-56` |
| La sesión abierta se **lee** con la sucursal de la property, no la del cliente → una sesión `(id, 0)` nunca se encuentra y cada login crea otra fila | `UsuarioResolver.java:52-54` |
| `sucursalActual()` = `findById(Long.valueOf(property))`: `null` si no existe, `NumberFormatException` si falta la property | `SucursalService.java:50-51`; `gotchas-filial.md` §sucursalId |
| Producción fija `sucursalId` por overlay/`.env` (farmacia 1..5; sin overlay el JAR cae en 24, no en 0) | `frc-cicd/runbooks/application-properties-overlay.md` |
| PK real `(id, sucursal_id)`, `sucursal_id NOT NULL`, `REPLICA IDENTITY FULL`; la entidad declara `@Id` simple | catálogo de 7 bases locales; `InicioSesion.java:33-43`; `V32__identity_full.sql` |
| Ids: filial **pares** (`V94.1`, incluye `inicio_sesion_id_seq`), central **impares** (central PR #289) | `V94.1__particion_ids_filial_pares.sql:35` |
| `inicio_sesion` viaja en los dos sentidos: `filialN_pub` (subida) y `central_filialN_pub WHERE sucursal_id = N` (bajada) | catálogo local; central `V0__initial_schema.sql:14011` |
| Único escritor de `InicioSesion` en el filial: este resolver | `grep InicioSesion src/main/java` |
| Clientes que llaman `saveInicioSesion`: desktop (sucursal de `/login`), mobile Android (`usuario.inicioSesion.sucursal?.id`, apunta a central por defecto), PWA (contra central) | `frc-mobile/.../push-notifications.service.ts:87` |
| Bases de filial locales: 0 filas con `sucursal_id = 0` y **0 ids repetidos**; los repetidos solo existen en bases de central (bodega 744, alpha 25), cuya entidad sí mapea PK compuesta | scripts de solo lectura sobre 5551/5552/5553 |
| `CrudService.findById(null)` devuelve `null`, no `Optional.empty()` | `CrudService.java:33-38` |
| Hoy un update pisa con `null` los campos que el input no manda (`ModelMapper` + `merge`) | `InicioSesionGraphQL.java:49-56` |

## Decisión

En `saveInicioSesion`, **la sucursal la decide el servidor** y las filas de otra sucursal no se tocan:

1. `sucursal = sucursalActual()`, capturando `NumberFormatException`. Si es `null` o su id es `0` →
   `GraphQLException("Servidor sin sucursal configurada")`, sin persistir.
2. **Alta** (`input.id == null`): nueva entidad con `sucursal` propia; `input.sucursalId` se ignora.
3. **Actualización** (`input.id != null`): se busca la fila con
   `repository.findByIdAndSucursalId(input.id, sucursalPropia.id)` (query derivada, no `findById`).
   - Si existe: se copian **solo los campos no nulos** del input sobre la fila (corrige el pisado a
     `null`) y se conserva su sucursal.
   - Si no existe (fila de otra sucursal, legacy `(id, 0)`, id ajeno): `GraphQLException("Sesión no
     encontrada en esta sucursal")`, sin persistir. No se reescribe ni se republica una fila ajena.

Consecuencias:
- Cierra la fuente de filas `(id, 0)` venga de cualquier cliente o versión (incluidos desktops sin
  actualizar, que desktop#180 no puede cubrir).
- Alinea escritura y lectura (`UsuarioResolver` lee con la sucursal propia): el cliente vuelve a
  encontrar su sesión abierta y deja de crear una fila por login.
- Con desktop #297 (manda `null`), el filial acepta la sesión con su sucursal.
- Un desktop viejo que manda `0` **no** recibe error: la sucursal se ignora.

Descartado:
- **Capa 1 de #77 (PK compuesta en la entidad)**: la colisión `(504, 0)` entre filiales desaparece si
  ningún filial escribe con sucursal 0; la de una misma sucursal central↔filial la resuelve el reparto
  impar/par ya desplegado. Cambiar el mapeo JPA de una tabla replicada es riesgo sin beneficio adicional.
- **Capa 3 (`/login` devolviendo 0)**: vive en `security/TokenController.java`, que `CLAUDE.md` pide no
  tocar; con esta guarda el valor deja de llegar a la base.
- **Rechazar si `input.sucursalId` difiere**: dejaría sin registro de sesión a clientes viejos que mandan 0.
- **Update de fila legacy conservando su sucursal 0** (plan v1): volvería a publicar un UPDATE de una
  fila que central no tiene (auditoría B, R3).

## Fases

**Fase 1 (única)** — commit `fix(replicacion): guardar el inicio de sesion con la sucursal del filial`
- `InicioSesionRepository.java`: `Optional<InicioSesion> findByIdAndSucursalId(Long id, Long sucursalId)`.
- `InicioSesionGraphQL.java`: resolución descrita en «Decisión».
- `src/test/java/com/franco/dev/graphql/configuraciones/InicioSesionGraphQLTest.java` (nuevo, JUnit 5 +
  Mockito, sin contexto Spring; mocks inyectados con `ReflectionTestUtils.setField` porque el resolver
  usa `@Autowired` en campos):
  1. alta con `sucursalId = 0` → se guarda con la sucursal del filial;
  2. alta con `sucursalId = null` → sucursal del filial;
  3. update de fila propia → conserva sucursal y no pisa con `null` los campos que el input no manda;
  4. update con id que no existe en la sucursal propia → `GraphQLException`, nunca `saveAndSend`;
  5. `sucursalActual()` `null`, id `0` o `NumberFormatException` → `GraphQLException`, nunca `saveAndSend`.
  - Revertir el fix y comprobar que 1, 2 y 4 fallan con el código viejo.
- `./mvnw -o clean verify -B` (línea base: 107 tests, 0 fallos).

## Datos nuevos

Ninguno. Sin columnas, migraciones ni cambios de schema GraphQL (`sucursalId` sigue en el input; se
ignora). La query derivada nueva lee columnas existentes.

## Auditoría del plan (paso 5)

| Eje | Hallazgo | Sev. | Qué se hizo |
|---|---|---|---|
| A | Si desktop #297 llega a alpha antes que este fix, cada login de un desktop nuevo contra un filial viejo da `NOT NULL` (login no se bloquea) | media | nota de despliegue: mergear el filial primero o en la misma ventana |
| A | Filial con sucursal inválida pasa de guardar basura en silencio a mostrar error en cada login | media | buscado; nota de despliegue para revisar `SUCURSALID` de las filiales. Sin evidencia de ninguna así hoy |
| A | Update con id presente pero fila inexistente no tenía test | baja | diseño lo rechaza; test 4 |
| A | Replicación central→filial no pasa por el resolver; mobile apunta a central por defecto | — | confirma alcance |
| B | `findById(id)` rompe con ids repetidos (entidad con `@Id` simple) | alta → baja | medido: 0 repetidos en bases de filial. Se busca por `(id, sucursal)`; el `merge` final sigue por `id` como hoy (limitación preexistente, anotada) |
| B | Update pisa con `null` los campos que el input no manda | alta | se copian solo campos no nulos; test 3 |
| B | Update de fila legacy `(id, 0)` conservando 0 republica un UPDATE que central no tiene | media | rediseñado: solo se actualizan filas de la sucursal propia; test 4 |
| B | `sucursalActual()` tira `NumberFormatException` sin property | media | se captura y responde el mismo error; test 5 |
| B | Resolver con `@Autowired` en campos: el test no puede usar constructor | baja | `ReflectionTestUtils.setField` |
| B | «desktop viejo + filial nuevo produce error» | — | descartado: la sucursal del input se ignora, no hay error |

## Impacto

- Migraciones: ninguna. Schema GraphQL: sin cambios.
- Replicación: deja de producir filas `(id, 0)` y de republicar filas ajenas; no toca publicaciones.
- Canal: merge a `develop` → filiales alpha en ≤15 min, con reinicio del servicio.
- Rollback: revertir el JAR vuelve al pass-through. Sin estado nuevo.

## Sin verificar / fuera de alcance

- Las 687 filas `(id, 0)` ya en conflicto en farmacia (filial 1 y 3): tratamiento operativo
  (`ALTER SUBSCRIPTION ... SKIP` o reasignación), no código. No están en local.
- Por qué el `/login` de esas filiales devolvió 0: no lo explica la config (sin overlay cae en 24).
- `merge` por `id` simple ante ids repetidos en una base de filial: preexistente; hoy 0 casos medidos.
- Prueba de runtime: filial dev en 8082 + mutation `saveInicioSesion` con `sucursalId: 0` → fila con
  `sucursal_id = 24`; update de esa fila solo con `horaFin` conserva `token`; con `--sucursalId=0` → error.
