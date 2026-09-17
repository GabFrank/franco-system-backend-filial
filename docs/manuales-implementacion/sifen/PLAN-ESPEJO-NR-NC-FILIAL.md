# Plan (filial) — Espejo DDL, partición de ids y guarda del scheduler para NR/NC electrónicas

_Pieza del filial dentro del plan maestro
`central/docs/manuales-implementacion/sifen/PLAN-NOTA-REMISION-NOTA-CREDITO.md` (rama
`feature/sifen-nota-remision-nota-credito` de `GabFrank/franco-system-backend-servidor`). Ahí
están la arquitectura, las decisiones (D1, D12, D13), el orden de despliegue (§7), los riesgos y la
auditoría. Este archivo cubre solo lo que cambia en el filial. Rama de trabajo:
`fix/sifen-espejo-documento-electronico-notas` (sale de `develop`). Decisiones confirmadas por
Gabriel el 2026-09-17._

Todo lo marcado `[ev: ...]` se verificó el 2026-09-17 contra el código de este repo.

---

## 1 · Por qué el filial se toca aunque NO emite notas

Las notas de remisión y de crédito las emite **solo central** (decisión D1). Pero central persiste
sus DE en `financiero.documento_electronico` con `factura_legal_id = NULL` y `nota_*_id` cargado, y
esa tabla **baja a todas las filiales** por `central_pub` (sin filtro) y `central_filialN_pub`
(filtrada por sucursal). En este repo la columna es `NOT NULL`
`[ev: src/main/resources/db/migration/V34__add_documento_electronico_fields.sql:8]` y la entidad
la declara `nullable=false` `[ev: src/main/java/com/franco/dev/domain/financiero/DocumentoElectronico.java:38-40]`.
Sin el espejo, la primera nota emitida en central mata el apply worker de todas las filiales del
canal ("null value in column factura_legal_id violates not-null") — el mismo mecanismo del
incidente `V192.5` / `V90.7`.

Además el scheduler SIFEN del filial toma `findByEstado(PENDIENTE)` **sin filtrar por sucursal ni
por tipo** en sus tres métodos, y está habilitado por default
`[ev: src/main/java/com/franco/dev/service/sifen/service/SifenSchedulerService.java:50,137,235,365]`:
un DE o un lote de nota replicado desde central sería reenviado a SIFEN desde la filial (doble
envío).

Y las secuencias de `documento_electronico`, `lote_de`, `evento_cancelacion_de` y
`evento_nominacion_de` son `BIGSERIAL` planas en los dos nodos: central y la filial N escriben
filas con `sucursal_id = N` en el mismo espacio de ids, y la colisión corta la suscripción. Central
ya reparte impares/pares para otras tablas (`V223.1`); acá se extiende a estas cuatro (D13).

## 2 · Qué cambia (Fase 0.C y 0.D del plan maestro)

### 2.1 Migración `V91.5__espejo_documento_electronico_notas.sql`
```sql
ALTER TABLE financiero.documento_electronico ALTER COLUMN factura_legal_id DROP NOT NULL;
ALTER TABLE financiero.documento_electronico
    ADD COLUMN IF NOT EXISTS nota_credito_id  BIGINT NULL,
    ADD COLUMN IF NOT EXISTS nota_remision_id BIGINT NULL;
```
Sin FK (el filial no tiene las tablas de notas) y sin `CHECK` (el filial nunca escribe estas
columnas; un `CHECK` solo agregaría un modo de falla al apply worker). Aditiva y retrocompatible:
el JAR anterior sigue escribiendo `factura_legal_id` siempre.

### 2.2 Migración `V91.7__particion_ids_documento_electronico_par.sql`
Espejo de `V223.1` de central pero a **pares**: para `financiero.documento_electronico_id_seq`,
`lote_de_id_seq`, `evento_cancelacion_de_id_seq`, `evento_nominacion_de_id_seq` (verificar los
nombres reales con `\ds financiero.*` en una filial):
`INCREMENT BY 2` y `setval` al próximo **par** mayor que `GREATEST(MAX(id), last_value)`, en un
`DO $$` que salta la secuencia si no existe. Sin trigger de rechazo (el filial recibe filas impares
de central por réplica; el trigger solo tiene sentido en central).

### 2.3 Entidad
`DocumentoElectronico.facturaLegal`: `@JoinColumn(name = "factura_legal_id", nullable = true)`.
No se mapean las columnas nuevas (`ddl-auto=none`; Hibernate ignora columnas que no conoce).

### 2.4 Guarda del scheduler (`SifenSchedulerService`)
Predicado único `esPropio(DE) = de.getSucursalId().equals(sucursalPropia) && de.getFacturaLegal() != null`
(`sucursalPropia` = la misma property que ya usa el resto del filial), aplicado en:
- `crearYEnviarLotes` (`:137`): repo nuevo
  `findByEstadoAndSucursalIdAndFacturaLegalIsNotNullOrderByIdAsc(EstadoDE, Long)`.
- `consultarLotesPendientes` (`:235`): solo lotes `EN_PROCESO` cuyos DE cumplan **todos** el
  predicado.
- `procesarLotesAtrasados` (`:365`): idem sobre lotes `PENDIENTE_ENVIO`/`ERROR_ENVIO`/`ERROR_RED`.
  Filtrar solo por sucursal **no alcanza**: un lote de nota que central crea para la sucursal N y
  falla al enviarse llega acá con `sucursal_id = N` y `ERROR_ENVIO`.

### 2.5 Tests (`./mvnw clean verify -B`)
`SifenSchedulerServiceGuardaTest` con Mockito, un caso por método: un DE `PENDIENTE` sin factura y
otro de otra sucursal **no** entran al lote; un lote `EN_PROCESO` y otro `ERROR_ENVIO` cuyos DE no
tienen factura **no** se consultan ni se reenvían. Revertida la guarda, los tres fallan.

## 3 · Datos nuevos (quién escribe, quién lee)

| Columna | Escribe | Lee |
|---|---|---|
| `documento_electronico.factura_legal_id` nullable | (sin cambio: el filial siempre la carga) | `FacturaLegalBuilder`, scheduler (ahora con la guarda) |
| `nota_credito_id`, `nota_remision_id` | **nadie en el filial** (llegan por réplica desde central) | nadie en el filial; existen para que el apply worker acepte la fila |

Es la excepción explícita a la regla "un dato con una sola punta no se implementa": acá la única
punta local es la réplica, y la tabla de datos nuevos del plan maestro (§3.1) tiene las dos puntas
en central.

## 4 · Orden y despliegue

1. Rama `fix/sifen-espejo-documento-electronico-notas` desde `develop`; commits: `V91.5` +
   entidad, `V91.7`, guarda + test.
2. Dry-run de las dos migraciones contra un dump reciente de **una filial de cada red** (farmacia y
   bodega) antes del PR (el CI no valida Flyway y acá no hay ningún test que levante contexto).
3. PR a `develop` con las seis secciones. **Nota de despliegue**: "se propaga solo en ≤15 min y
   reinicia el servicio de cada filial del canal: alpha; después 6 de farmacia por `release/beta`;
   después 18 de bodega por `master`. Es prerrequisito del deploy de central con notas
   electrónicas: central no se despliega en un canal hasta que todas sus filiales estén en
   `V91.5`."
4. Este PR **va antes** que el de central y se promueve **antes** que central en cada canal
   (§7 y §12.2 del plan maestro).

## 5 · Rollback
JAR anterior contra el esquema nuevo: funciona (`factura_legal_id` nullable no cambia lo que el
JAR viejo escribe; las columnas nuevas se ignoran; la paridad de ids es transparente). Las
migraciones no se revierten (Flyway no hace down): no hace falta, son aditivas.

## 6 · Sin verificar
- Nombres exactos de las cuatro secuencias en una filial real (`\ds financiero.*`).
- Si `procesarLotesAtrasados` tiene algún criterio adicional de antigüedad que interactúe con la
  guarda (leer el método completo antes de tocarlo).
- Cómo obtiene hoy el filial su `sucursalId` en servicios (property vs `configuracion.local`):
  reutilizar el mismo mecanismo, no crear otro.
