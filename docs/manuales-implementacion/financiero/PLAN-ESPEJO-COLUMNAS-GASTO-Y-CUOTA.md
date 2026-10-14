# Plan: espejo de columnas que central publica y la filial no tiene

Rama: `fix/replicacion-gasto-solicitud-pago-id` (desde `origin/develop`, 2026-10-14). Pieza: filial.

## Qué pasó

El 2026-10-14 a las 11:54 la suscripción `central_filial_farmacia_4_sub` de la filial 4 de farmacia
(SUC. III, 100.64.0.7) dejó de aplicar cambios:

```
logical replication target relation "financiero.gasto" is missing replicated column: "solicitud_pago_id"
```

Central agregó `financiero.gasto.solicitud_pago_id` en `V221.3__gasto_desde_caja_mayor.sql` (aplicada
en farmacia el 2026-09-23) y la publica en `central_filial{N}_pub` con filtro `sucursal_id = N`. Ninguna
filial tenía la columna; la 4 se trabó con el primer UPDATE de central sobre un gasto de su sucursal
(gasto 30, creado 11:31, finalizado por central).

Se destrabó a mano el mismo día con `ADD COLUMN IF NOT EXISTS` en las 4 filiales activas de farmacia
(1, 3, 4 y la de sucursal 7). La filial 4 reenganchó y drenó.

Comparando columnas publicadas a la filial 4 (`central_pub`, `central_filial4_pub`,
`central_filial4_vt_pub`) contra las que tiene, aparece **un segundo caso igual, todavía latente**:

| Tabla | Columnas | Origen en central | Por qué no falló aún |
|---|---|---|---|
| `financiero.venta_credito_cuota` | `monto_cobrado numeric(18,4) DEFAULT 0`, `estado_cobro varchar(20)` | `V181.5__financiero_cpc_cuenta_cliente.sql` (2026-08-13) | las 13.101 cuotas de farmacia siguen en `0` / `NULL`: CPC todavía no cobró ninguna cuota de filial |

Bodega no publica ni `gasto` ni estas columnas: no está afectada hoy.

## Fase 1 — migración espejo `V105.1`

`src/main/resources/db/migration/V105.1__espejo_gasto_solicitud_pago_y_cuota_cobro.sql`:

```sql
SET LOCAL lock_timeout = '10s';
ALTER TABLE financiero.gasto ADD COLUMN IF NOT EXISTS solicitud_pago_id bigint;
ALTER TABLE financiero.venta_credito_cuota ADD COLUMN IF NOT EXISTS monto_cobrado numeric(18,4) DEFAULT 0;
ALTER TABLE financiero.venta_credito_cuota ADD COLUMN IF NOT EXISTS estado_cobro varchar(20);
```

- Tipos y defaults idénticos a central. `IF NOT EXISTS`: en las 4 filiales de farmacia `gasto` ya la
  tiene (fix manual) y es no-op.
- Sin el índice único `uk_gasto_solicitud_pago` de V221.3: lo mantiene central, donde nacen esos gastos
  (V221.3 no crea FK).
- Sin cambios de entity: la filial no lee ni escribe estas columnas. Solo existen para que el apply
  worker pueda aplicar lo que central manda.

Numeración: mayor en `origin/develop` = `V104.5` → `V105.1`. La filial tiene
`spring.flyway.out-of-order=true` (commit `3c68319`), así que un rebase que traiga un número mayor no la
saltea, pero se re-verifica igual antes del push.

### Datos nuevos (quién escribe / quién lee)

| Columna en filial | Escribe | Lee |
|---|---|---|
| `gasto.solicitud_pago_id` | apply worker de `central_filial{N}_sub` (valor de central, `GastoTesoreriaService`) | nadie en la filial; central vía la pub de la filial (siempre NULL en gastos de sucursal ≠ 0) |
| `venta_credito_cuota.monto_cobrado` / `estado_cobro` | apply worker (valor del CPC de central) | nadie en la filial; central vía la pub de la filial |

No es configuración muerta: el "lector" que importa es el apply worker, que hoy falla sin ellas.

### Efecto lateral: la pub de la filial empieza a mandar estas columnas a central

`filial{N}_pub` no tiene lista de columnas, así que al crearlas se publican. Un UPDATE nacido en la
filial manda el valor que tenga la filial. Riesgo de pisar a central: solo si central tiene un valor que
todavía no llegó a la filial. Hoy no hay ninguno (gasto: los 16 con `solicitud_pago_id` son sucursal 0,
fuera del filtro; cuota: todas en default). Desde que la columna existe, central→filial la mantiene al
día.

### Tests

N/A test unitario: es DDL sin código Java que lo use; el filial no tiene tests de contexto que corran
Flyway (matriz 0.2). La verificación es:
1. `./mvnw clean verify -B` (paso 9) para que el build no se rompa.
2. Dry-run (paso 10): levantar el filial local con perfil `dev` contra `general@localhost:5551` y ver en
   el log que aplica `105.1` limpio; correrlo dos veces no aplica nada.
3. Que central→filial no se trabe con las columnas presentes ya está probado en producción en la filial 4
   (mismo DDL, aplicado a mano).

## Despliegue

Solo filial, sin espejo en central (central ya las tiene). Camino: merge a `develop` → filiales alpha
(≤15 min, sin gate) → `release/beta` → **las 6 de farmacia** → `master` → las 18 de bodega. En las 4 de
farmacia activas `gasto` es no-op; `venta_credito_cuota` se crea al llegar la versión a beta. Hasta
entonces el riesgo de la cuota sigue abierto en farmacia (ver «Sin verificar»).

Precondición, verificada el 2026-10-14 (solo lectura): los centrales de **farmacia y bodega** tienen
las tres columnas (V181.5 y V221.3 aplicadas). Sin eso, la `filial{N}_pub` (sin lista de columnas)
empezaría a mandar columnas que el central no tiene y la sub filial→central se trabaría al revés.

Rollback: el JAR anterior funciona con el esquema nuevo (`ddl-auto=none`, sin `INSERT` nativo ni
`SELECT *` sobre estas tablas, las entities no las mapean). El rollback del JAR **no** quita las
columnas: si alguna sub filial→central se trabara por ellas, el arreglo es agregarlas en el central
(`ALTER TABLE ... ADD COLUMN IF NOT EXISTS`, mismos tipos), no volver de versión.

## Auditoría del plan (paso 5)

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| Bodega podría trabar filial→central si su central no tiene las columnas | B (alta) | Verificado en prod: central bodega y farmacia tienen las 3 columnas. Queda como precondición arriba |
| La copia local muestra `gasto` en la sub de bodega 24: bodega sí lo publicaría | A (alta) | Refutado en prod: ni `central_pub` ni `central_bodega_filial{N}_pub` publican `gasto` ni `venta_credito_cuota`. La sub local apunta a `localhost` |
| El plan decía «stable» para farmacia | A (media) | Corregido: farmacia va por `release/beta` |
| «Rollback: no hace falta» | B (media) | Reescrito: el JAR viejo anda; el esquema no se revierte y el arreglo de una traba es en el central |
| La filial puede pisar `monto_cobrado` del central con 0 | A/B (baja) | Hoy no hay vía: la filial solo inserta cuotas (`VentaCreditoGraphQL.java:149-170`; `cobrarVentaCredito` devuelve null). Cuotas con cobro: 0 en farmacia (13.101) y 0 en bodega (92.881). **Invariante: la filial no actualiza `venta_credito_cuota`** |
| REPLICA IDENTITY FULL y match de filas | B (baja) | Ambas tablas FULL en los dos lados, pero tienen PK `(id, sucursal_id)` y el apply worker busca por PK |
| Lock de `ADD COLUMN` | B (baja) | PG ≥11 no reescribe con default constante; se agrega `SET LOCAL lock_timeout = '10s'` para no colgar el arranque |
| Otras columnas del central sin espejo | A (baja) | Comparado publicación vs catálogo en vivo: filial 4 de farmacia → solo las de este plan; filial 24 de bodega → ninguna |
| Test que atrape esto | B | N/A JUnit. El chequeo útil es comparar `pg_publication_tables` del que publica con `information_schema.columns` del que suscribe; se corrió a mano en los dos casos de arriba |

## Auditoría del diff (paso 8)

Fijo 1 y Fijo 3: N/A para filial porque el diff es un `.sql` y este plan, sin resolver, `.graphqls` ni
entity [ev: `git diff --name-only origin/develop...HEAD`]. Fijo 2 + Condicional B en un auditor:

| Hallazgo | Qué se hizo |
|---|---|
| El comentario y el plan nombraban una FK de V221.3 que no existe, y el índice como `ux` | Corregido: el índice es `uk_gasto_solicitud_pago` y no hay FK |
| «Central publica a cada filial» generaliza a bodega | Aclarado: solo farmacia |
| `lock_timeout`: nada reintenta solo | Aclarado en el comentario: PG revierte la migración y el próximo reinicio la reintenta |
| El invariante «la filial no actualiza cuotas» no tiene test | Javadoc en `VentaCreditoCuota.java` |

## Sin verificar

- Que el primer cobro CPC de una cuota de filial efectivamente trabe la réplica (se infiere del mismo
  mecanismo que `gasto`; no se reprodujo). Mientras la versión no llegue a stable, se puede adelantar el
  `ALTER` de `venta_credito_cuota` a mano en las 4 filiales de farmacia, igual que con `gasto`.
- Las filiales de bodega: no publican estas columnas hoy; la migración se aplica igual y es inocua.
