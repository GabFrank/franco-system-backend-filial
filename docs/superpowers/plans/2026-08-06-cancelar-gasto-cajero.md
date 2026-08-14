# Cancelar Gasto de Cajero — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permitir que un usuario ADM cancele un gasto de cajero desde el servidor central, devolviendo el monto al balance de la caja correspondiente en central y filial.

**Architecture:** Se agrega una columna `cancelado BOOLEAN` nullable a `financiero.gasto` en ambas DBs. El central expone la mutation `cancelarGasto`, que hace toggle del flag. La fila baja a la filial por replicación lógica. Ningún balance se recalcula ni se persiste: `PdvCajaService.generarBalance` de cada backend simplemente saltea los gastos cancelados al recomputar, igual que ya hace con retiros y ventas cancelados.

**Tech Stack:** Java 8 / Spring Boot 2.1.15, GraphQL (`graphql-java-kickstart`), Flyway, PostgreSQL con replicación lógica; Angular 15 + Apollo Client.

Spec de referencia: `docs/superpowers/specs/2026-08-06-cancelar-gasto-cajero-design.md`

## Global Constraints

- **Tres repos, tres PRs.** Filial (`franco-system-backend-filial`), central (`franco-system-backend-servidor`), frontend (`frc-sistemas-integrados-angular`).
- **Toda rama sale de `origin/develop` recién fetcheada**, en los tres repos. En un worktree la base por default es `master`: chequear y `git reset --hard origin/develop` **antes** de escribir código.
- **Orden de merge obligatorio: filial → (esperar ~15 min + verificar) → central → frontend.** Si el central replica `cancelado` antes de que la filial tenga la columna, el apply worker de la suscripción falla y la replicación queda trabada acumulando WAL.
- **Nunca pushear sin confirmación explícita del usuario.** Un merge a `develop` dispara release alpha y las filiales se auto-actualizan cada 15 minutos.
- **Migraciones solo aditivas.** Sin `DROP`, `RENAME` ni cambio de tipo. Nunca modificar una migración ya aplicada.
- **Numeración Flyway: siguiente entero, sufijo `.1`.** Filial → `V86.1` (la última en `develop` es `V85.1`). Central → `V157.1` y `V158.1` (la última en `develop` es `V156.1`). Dos migraciones = dos enteros consecutivos, nunca `.2`.
- **Tipos de commit permitidos:** `feat`, `fix`, `refactor`, `docs`, `chore`. Nunca `style`, `test`, `perf` ni `ci`.
- **Semántica del flag:** `NULL` = no cancelado. En Java el chequeo es `Boolean.TRUE.equals(...)`; en SQL es `IS NOT TRUE`. Nunca `= false` ni `<> true` — descartarían los `NULL`, que son todos los gastos históricos.
- **PRs siempre draft, target `develop`, nunca mergear.**

## File Structure

### Filial (`franco-system-backend-filial`)
| Archivo | Responsabilidad |
|---|---|
| `src/main/resources/db/migration/V86.1__add_cancelado_gasto.sql` | Crear (nuevo) — columna `cancelado` |
| `src/main/java/com/franco/dev/domain/financiero/Gasto.java` | Modificar — campo `cancelado` |
| `src/main/java/com/franco/dev/service/financiero/PdvCajaService.java` | Modificar — saltear cancelados en `generarBalance` |
| `src/main/resources/graphql/financiero/gasto.graphqls` | Modificar — exponer `cancelado` en `type Gasto` |

### Central (`franco-system-backend-servidor`)
| Archivo | Responsabilidad |
|---|---|
| `src/main/resources/db/migration/V157.1__add_cancelado_gasto.sql` | Crear (nuevo) — columna `cancelado` |
| `src/main/resources/db/migration/V158.1__replicar_gasto_central_a_filial.sql` | Crear (nuevo) — activar replicación central→filial |
| `src/main/java/com/franco/dev/domain/financiero/Gasto.java` | Modificar — campo `cancelado` |
| `src/test/java/com/franco/dev/service/financiero/GastoServiceTest.java` | Crear (nuevo) — test del toggle |
| `src/main/java/com/franco/dev/service/financiero/GastoService.java` | Modificar — método `cancelarGasto` |
| `src/main/java/com/franco/dev/graphql/financiero/GastoGraphQL.java` | Modificar — resolver de la mutation |
| `src/main/resources/graphql/financiero/gasto.graphqls` | Modificar — campo + mutation |
| `src/main/java/com/franco/dev/service/financiero/PdvCajaService.java` | Modificar — saltear cancelados en `generarBalance` |
| `src/main/java/com/franco/dev/repository/financiero/GastoRepository.java` | Modificar — filtro en las 4 queries agregadas |

### Frontend (`frc-sistemas-integrados-angular`)
Todo bajo `src/app/modules/financiero/gastos/`:
| Archivo | Responsabilidad |
|---|---|
| `graphql/cancelarGasto.ts` | Crear (nuevo) — clase `Mutation` de Apollo |
| `graphql/graphql-query.ts` | Modificar — documento gql + `cancelado` en `filterGastosQuery` |
| `models/gastos.model.ts` | Modificar — campo `cancelado` |
| `service/gasto.service.ts` | Modificar — método `onCancelarGasto` |
| `pages/list-gastos/list-gastos.component.ts` | Modificar — `esAdmin`, `onCancelarGasto`, prioridad CANCELADO en los getters |
| `pages/list-gastos/list-gastos.component.html` | Modificar — ítem de menú |

> **Nota sobre los snippets del frontend:** se leyeron del working tree actual del usuario, que está en una rama anterior al merge de `feature/retiros-cancelar-retiro`. Antes de editar, verificar contra `origin/develop` — en particular que `list-gastos.component.ts` siga teniendo la forma que muestra el plan.

---

### Task 1: Filial — columna, entity y filtro en el balance

**Files:**
- Create: `src/main/resources/db/migration/V86.1__add_cancelado_gasto.sql`
- Modify: `src/main/java/com/franco/dev/domain/financiero/Gasto.java`
- Modify: `src/main/java/com/franco/dev/service/financiero/PdvCajaService.java` (loop de gastos, ~línea 386)
- Modify: `src/main/resources/graphql/financiero/gasto.graphqls`

**Interfaces:**
- Consumes: nada (primera tarea).
- Produces: columna `financiero.gasto.cancelado BOOLEAN` y getter `Gasto.getCancelado(): Boolean` (generado por Lombok `@Data`). La Task 2 replica el mismo nombre de columna y de campo — tienen que coincidir exactamente o la replicación lógica falla.

- [ ] **Step 1: Crear la rama desde `origin/develop`**

```bash
git fetch origin develop
git checkout -b feature/gastos-cancelar-gasto origin/develop
git log --oneline HEAD..origin/develop   # tiene que salir vacío
```

- [ ] **Step 2: Confirmar que `V86.1` está libre**

```bash
ls src/main/resources/db/migration | sed 's/^[Vv]//;s/__.*//' | sort -t. -k1,1n -k2,2n | tail -3
```

Esperado: la mayor es `85.1`. Si es otra, usar el entero siguiente con sufijo `.1` y ajustar el nombre del archivo del Step 3.

- [ ] **Step 3: Escribir la migración**

Crear `src/main/resources/db/migration/V86.1__add_cancelado_gasto.sql`:

```sql
-- Marca un gasto de cajero como cancelado. La cancelacion se ejecuta en el central
-- y baja por replicacion; la filial solo necesita conocer la columna y descontarla
-- de su propio generarBalance.
--
-- Esta migracion tiene que estar aplicada en la filial ANTES de desplegar el central
-- con la mutation cancelarGasto: si el central replica una columna que la filial no
-- tiene, el apply worker de la suscripcion falla y la replicacion queda trabada
-- acumulando WAL.
--
-- Nullable a proposito: NULL = no cancelado, y asi los gastos historicos no
-- necesitan backfill. Por eso el chequeo es Boolean.TRUE.equals(...) en Java y
-- IS NOT TRUE en SQL, nunca "= false".
ALTER TABLE financiero.gasto ADD COLUMN IF NOT EXISTS cancelado BOOLEAN;
```

- [ ] **Step 4: Agregar el campo a la entity**

En `src/main/java/com/franco/dev/domain/financiero/Gasto.java`, después de `private Boolean finalizado;`:

```java
    private Boolean finalizado;

    /**
     * NULL = no cancelado. Lo setea el central via cancelarGasto y baja por
     * replicacion. La filial solo lo lee.
     */
    private Boolean cancelado;
```

- [ ] **Step 5: Exponer el campo en el schema GraphQL**

En `src/main/resources/graphql/financiero/gasto.graphqls`, dentro de `type Gasto`, después de `finalizado: Boolean`:

```graphql
    activo: Boolean
    finalizado: Boolean
    cancelado: Boolean
```

No agregar nada a `input GastoInput` ni a `extend type Mutation`: la filial no cancela, solo lee.

- [ ] **Step 6: Saltear los gastos cancelados en el balance**

En `src/main/java/com/franco/dev/service/financiero/PdvCajaService.java`, el loop actual es:

```java
            for (Gasto gasto : gastoList) {
                totalGastoGs += (gasto.getRetiroGs() - gasto.getVueltoGs());
                totalGastoRs += (gasto.getRetiroRs() - gasto.getVueltoRs());
                totalGastoDs += (gasto.getRetiroDs() - gasto.getVueltoDs());
            }
```

Reemplazarlo por:

```java
            for (Gasto gasto : gastoList) {
                // Un gasto cancelado no descuenta de la caja: la plata volvio. El flag lo
                // setea el central y baja por replicacion, igual que retiro.estado (ver el
                // filtro de RetiroDetalle mas arriba en este mismo metodo).
                // La comparacion va en Java y no en la query a proposito: en SQL un
                // "cancelado <> true" descartaria tambien las filas con cancelado NULL,
                // que son todos los gastos historicos.
                if (Boolean.TRUE.equals(gasto.getCancelado())) {
                    continue;
                }
                totalGastoGs += (gasto.getRetiroGs() - gasto.getVueltoGs());
                totalGastoRs += (gasto.getRetiroRs() - gasto.getVueltoRs());
                totalGastoDs += (gasto.getRetiroDs() - gasto.getVueltoDs());
            }
```

- [ ] **Step 7: Compilar**

```bash
./mvnw -o clean package -DskipFlyway=true
```

Esperado: `BUILD SUCCESS`. Si falla por dependencias offline, reintentar sin `-o`.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/db/migration/V86.1__add_cancelado_gasto.sql \
        src/main/java/com/franco/dev/domain/financiero/Gasto.java \
        src/main/java/com/franco/dev/service/financiero/PdvCajaService.java \
        src/main/resources/graphql/financiero/gasto.graphqls
git commit -m "feat(financiero): ignorar gastos cancelados en el balance de caja"
```

---

### Task 2: Central — mutation `cancelarGasto`, balance y reportes

**Files:**
- Create: `src/main/resources/db/migration/V157.1__add_cancelado_gasto.sql`
- Create: `src/main/resources/db/migration/V158.1__replicar_gasto_central_a_filial.sql`
- Create: `src/test/java/com/franco/dev/service/financiero/GastoServiceTest.java`
- Modify: `src/main/java/com/franco/dev/domain/financiero/Gasto.java`
- Modify: `src/main/java/com/franco/dev/service/financiero/GastoService.java`
- Modify: `src/main/java/com/franco/dev/graphql/financiero/GastoGraphQL.java`
- Modify: `src/main/resources/graphql/financiero/gasto.graphqls`
- Modify: `src/main/java/com/franco/dev/service/financiero/PdvCajaService.java` (loop de gastos, ~línea 312)
- Modify: `src/main/java/com/franco/dev/repository/financiero/GastoRepository.java`

**Interfaces:**
- Consumes: el nombre de columna `cancelado` y el nombre de campo `cancelado` definidos en Task 1. Tienen que coincidir exactamente.
- Produces:
  - `GastoService.cancelarGasto(Gasto gasto): Boolean` — hace toggle y devuelve `true`.
  - Mutation GraphQL `cancelarGasto(id: ID!, sucId: ID): Boolean!` — la consume la Task 3.
  - Campo GraphQL `cancelado: Boolean` en `type Gasto` — lo consume la Task 3.

- [ ] **Step 1: Crear la rama desde `origin/develop`**

En el repo `franco-system-backend-servidor`:

```bash
git fetch origin develop
git checkout -b feature/gastos-cancelar-gasto origin/develop
git log --oneline HEAD..origin/develop   # tiene que salir vacío
```

- [ ] **Step 2: Confirmar que `V157.1` y `V158.1` están libres**

```bash
ls src/main/resources/db/migration | sed 's/^[Vv]//;s/__.*//' | sort -t. -k1,1n -k2,2n | tail -3
```

Esperado: la mayor es `156.1`. Si es otra, usar los dos enteros siguientes con sufijo `.1` y ajustar los nombres de archivo.

- [ ] **Step 3: Escribir la migración de la columna**

Crear `src/main/resources/db/migration/V157.1__add_cancelado_gasto.sql`:

```sql
-- Marca un gasto de cajero como cancelado. Solo el central escribe esta columna
-- (mutation cancelarGasto); la filial la recibe por replicacion y la usa para
-- descontar el gasto de su propio generarBalance.
--
-- Nullable a proposito: NULL = no cancelado, y asi los gastos historicos no
-- necesitan backfill. Por eso el chequeo es Boolean.TRUE.equals(...) en Java y
-- IS NOT TRUE en SQL, nunca "= false".
ALTER TABLE financiero.gasto ADD COLUMN IF NOT EXISTS cancelado BOOLEAN;
```

- [ ] **Step 4: Escribir la migración de replicación**

Crear `src/main/resources/db/migration/V158.1__replicar_gasto_central_a_filial.sql`:

```sql
-- Cancelar gasto se ejecuta en el central, pero el balance de caja tambien se
-- calcula en la filial (PdvCajaService.generarBalance). Para que la filial vea la
-- cancelacion, financiero.gasto tiene que bajar del central a la filial dueña.
--
-- La fila ya existe en replication_table desde V112 como BRANCH_TO_MAIN. Activando
-- replicate_central_to_branch_with_filter, LogicalReplicationService agrega la tabla
-- a central_<db>_filialN_pub con WHERE (sucursal_id = N) y refresca las
-- suscripciones. Mismo esquema que financiero.retiro (ver V155.1) y operaciones.venta.
--
-- gasto_detalle no se toca: la cancelacion solo modifica la cabecera.
--
-- REQUISITO DE ORDEN: la filial ya tiene que tener aplicada su migracion de la
-- columna 'cancelado' (V86.1 en franco-system-backend-filial) antes de que esto
-- corra en produccion. Si no, el apply worker de la suscripcion falla y la
-- replicacion queda trabada acumulando WAL.
UPDATE configuraciones.replication_table
SET replicate_central_to_branch_with_filter = true
WHERE table_name = 'financiero.gasto';
```

- [ ] **Step 5: Agregar el campo a la entity**

En `src/main/java/com/franco/dev/domain/financiero/Gasto.java`, después de `private Boolean finalizado;`:

```java
    private Boolean finalizado;

    /**
     * NULL = no cancelado. Un gasto cancelado no descuenta del balance de la caja
     * ni suma en los reportes agregados de gastos.
     */
    private Boolean cancelado;
```

- [ ] **Step 6: Escribir el test que falla**

Crear `src/test/java/com/franco/dev/service/financiero/GastoServiceTest.java`:

```java
package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.repository.financiero.GastoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GastoServiceTest {

    private GastoRepository repository;
    private ApplicationEventPublisher publisher;
    private GastoService service;

    @BeforeEach
    void setUp() {
        repository = mock(GastoRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        service = new GastoService(repository, publisher);
    }

    @Test
    void cancelarGasto_gastoNuncaCanceladoQuedaCancelado() {
        Gasto gasto = new Gasto();
        gasto.setCancelado(null);

        Boolean resultado = service.cancelarGasto(gasto);

        assertTrue(resultado);
        assertEquals(Boolean.TRUE, gasto.getCancelado());
        verify(repository).save(gasto);
    }

    @Test
    void cancelarGasto_gastoCanceladoSeRehabilita() {
        Gasto gasto = new Gasto();
        gasto.setCancelado(true);

        service.cancelarGasto(gasto);

        assertEquals(Boolean.FALSE, gasto.getCancelado());
        verify(repository).save(gasto);
    }

    @Test
    void cancelarGasto_noPublicaGastoRealizadoEvent() {
        Gasto gasto = new Gasto();

        service.cancelarGasto(gasto);

        // Cancelar no es realizar un gasto: no tiene que disparar la push
        // notification. Por eso el service persiste con repository.save() y no
        // con this.save(), cuyo override publica GastoRealizadoEvent.
        verify(publisher, never()).publishEvent(any());
    }
}
```

- [ ] **Step 7: Correr el test y verificar que falla**

```bash
./mvnw -o test -Dtest=GastoServiceTest
```

Esperado: FALLA a nivel de compilación, con `cannot find symbol: method cancelarGasto(Gasto)`.

- [ ] **Step 8: Implementar `cancelarGasto`**

En `src/main/java/com/franco/dev/service/financiero/GastoService.java`, agregar el import:

```java
import org.springframework.transaction.annotation.Transactional;
import graphql.GraphQLException;
```

y el método, después del override de `save`:

```java
    /**
     * Cancela o rehabilita un gasto, igual que RetiroService.cancelarRetiro: es un
     * toggle cancelado <-> no cancelado.
     *
     * No recalcula ningun balance. El monto vuelve a la caja porque
     * PdvCajaService.generarBalance ignora los gastos cancelados, y la filial hace
     * lo mismo cuando el flag le llega por replicacion.
     *
     * Persiste con repository.save() a proposito, y no con this.save(): el override
     * de save() publica GastoRealizadoEvent, que dispara la push notification de
     * gasto realizado. Cancelar no es realizar un gasto.
     */
    @Transactional
    public Boolean cancelarGasto(Gasto gasto) {
        try {
            gasto.setCancelado(!Boolean.TRUE.equals(gasto.getCancelado()));
            repository.save(gasto);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            throw new GraphQLException("No se pudo cancelar el gasto");
        }
    }
```

- [ ] **Step 9: Correr el test y verificar que pasa**

```bash
./mvnw -o test -Dtest=GastoServiceTest
```

Esperado: `Tests run: 3, Failures: 0, Errors: 0`.

- [ ] **Step 10: Agregar el resolver de la mutation**

En `src/main/java/com/franco/dev/graphql/financiero/GastoGraphQL.java`, justo después del método `deleteGasto`:

```java
    public Boolean cancelarGasto(Long id, Long sucId) {
        Gasto gasto = service.findByIdAndSucursalId(id, sucId);
        if (gasto != null) {
            return service.cancelarGasto(gasto);
        } else {
            throw new GraphQLException("No se pudo cancelar el gasto");
        }
    }
```

- [ ] **Step 11: Actualizar el schema GraphQL**

En `src/main/resources/graphql/financiero/gasto.graphqls`, en `type Gasto`, después de `finalizado: Boolean`:

```graphql
    activo: Boolean
    finalizado: Boolean
    cancelado: Boolean
```

y en `extend type Mutation`, después de `deleteGasto`:

```graphql
extend type Mutation {
    saveGasto(entity:GastoInput!, printerName: String, local: String):Gasto!
    deleteGasto(id:ID!, sucId: ID):Boolean!
    cancelarGasto(id:ID!, sucId: ID):Boolean!
}
```

No agregar `cancelado` a `input GastoInput`: si viajara en el input, un `saveGasto` podría revertir la cancelación sin querer.

- [ ] **Step 12: Saltear los gastos cancelados en el balance**

En `src/main/java/com/franco/dev/service/financiero/PdvCajaService.java`, reemplazar el loop de gastos por:

```java
            for (Gasto gasto : gastoList) {
                // Un gasto cancelado no descuenta de la caja: la plata volvio. Mismo
                // mecanismo que el filtro de RetiroDetalle mas arriba en este metodo.
                // La comparacion va en Java y no en la query a proposito: en SQL un
                // "cancelado <> true" descartaria tambien las filas con cancelado NULL,
                // que son todos los gastos historicos.
                if (Boolean.TRUE.equals(gasto.getCancelado())) {
                    continue;
                }
                totalGastoGs += (gasto.getRetiroGs() - gasto.getVueltoGs());
                totalGastoRs += (gasto.getRetiroRs() - gasto.getVueltoRs());
                totalGastoDs += (gasto.getRetiroDs() - gasto.getVueltoDs());
            }
```

- [ ] **Step 13: Excluir los cancelados de las 4 queries agregadas**

En `src/main/java/com/franco/dev/repository/financiero/GastoRepository.java` hay 4 native queries que ya filtran `AND g.activo = true`. Agregar `AND (g.cancelado IS NOT TRUE) ` inmediatamente después de esa línea en **cada una** de las cuatro: `gastosPorCategoria`, `gastosPorCategoriaSinSucursal`, `gastosPorMes`, `gastosPorMesSinSucursal`.

Por ejemplo, `gastosPorCategoria` queda:

```java
        @Query(value = "SELECT " +
                        "COALESCE(tg.descripcion, 'Sin Categoría'), " +
                        "SUM(g.retiro_gs - COALESCE(g.vuelto_gs, 0)), COUNT(g.id) " +
                        "FROM financiero.gasto g " +
                        "LEFT JOIN financiero.tipo_gasto tg ON g.tipo_gasto_id = tg.id " +
                        "WHERE g.creado_en BETWEEN :inicio AND :fin " +
                        "AND g.sucursal_id = :sucId " +
                        "AND g.activo = true " +
                        "AND (g.cancelado IS NOT TRUE) " +
                        "GROUP BY tg.descripcion " +
                        "ORDER BY SUM(g.retiro_gs) DESC", nativeQuery = true)
```

`IS NOT TRUE` y no `<> true`: con `<> true` Postgres devuelve `NULL` para las filas con `cancelado` nulo, el `WHERE` las descarta, y desaparecerían todos los gastos históricos del reporte.

`GraficoAggregationService` no se toca: delega en `gastoService.gastosPorCategoria` / `gastosPorMes`, así que queda cubierto por este cambio.

- [ ] **Step 14: Compilar y correr toda la suite**

```bash
./mvnw -o clean package -DskipFlyway=true
```

Esperado: `BUILD SUCCESS`, con `GastoServiceTest` en verde y sin regresiones en el resto.

- [ ] **Step 15: Commit**

```bash
git add src/main/resources/db/migration/V157.1__add_cancelado_gasto.sql \
        src/main/resources/db/migration/V158.1__replicar_gasto_central_a_filial.sql \
        src/test/java/com/franco/dev/service/financiero/GastoServiceTest.java \
        src/main/java/com/franco/dev/domain/financiero/Gasto.java \
        src/main/java/com/franco/dev/service/financiero/GastoService.java \
        src/main/java/com/franco/dev/graphql/financiero/GastoGraphQL.java \
        src/main/resources/graphql/financiero/gasto.graphqls \
        src/main/java/com/franco/dev/service/financiero/PdvCajaService.java \
        src/main/java/com/franco/dev/repository/financiero/GastoRepository.java
git commit -m "feat(financiero): permitir cancelar gastos desde el central"
```

---

### Task 3: Frontend — acción Cancelar y badge de estado

**Files:**
- Create: `src/app/modules/financiero/gastos/graphql/cancelarGasto.ts`
- Modify: `src/app/modules/financiero/gastos/graphql/graphql-query.ts`
- Modify: `src/app/modules/financiero/gastos/models/gastos.model.ts`
- Modify: `src/app/modules/financiero/gastos/service/gasto.service.ts`
- Modify: `src/app/modules/financiero/gastos/pages/list-gastos/list-gastos.component.ts`
- Modify: `src/app/modules/financiero/gastos/pages/list-gastos/list-gastos.component.html`

**Interfaces:**
- Consumes: mutation `cancelarGasto(id: ID!, sucId: ID): Boolean!` y campo `cancelado: Boolean` de `type Gasto`, ambos definidos en Task 2.
- Produces: nada (última tarea).

- [ ] **Step 1: Crear la rama desde `origin/develop`**

En el repo `frc-sistemas-integrados-angular`:

```bash
git fetch origin develop
git checkout -b feature/gastos-cancelar-gasto origin/develop
git log --oneline HEAD..origin/develop   # tiene que salir vacío
```

Si se trabaja en un worktree, preparar los **dos** `node_modules` con hardlinks antes de correr nada:

```bash
MAIN=/ruta/al/checkout/principal
cp -al $MAIN/node_modules      ./node_modules
cp -al $MAIN/app/node_modules  ./app/node_modules
```

Sin `app/node_modules` falta `electron-updater` y `npm start` muere con `TS2307` antes de levantar Electron.

- [ ] **Step 2: Agregar el documento gql y el campo al query de la lista**

En `src/app/modules/financiero/gastos/graphql/graphql-query.ts`, agregar al final:

```typescript
export const cancelarGasto = gql`
  mutation cancelarGasto($id: ID!, $sucId: ID) {
    data: cancelarGasto(id: $id, sucId: $sucId)
  }
`;
```

Y en `filterGastosQuery`, dentro de `getContent`, agregar `cancelado` junto a los demás campos escalares del gasto (por ejemplo justo después de `sucursalId`):

```graphql
      getContent {
        id
        sucursalId
        cancelado
```

Sin esto el `cancelado` nunca llega al front y el badge no cambia nunca.

- [ ] **Step 3: Crear la clase Mutation de Apollo**

Crear `src/app/modules/financiero/gastos/graphql/cancelarGasto.ts`:

```typescript
import { Injectable } from '@angular/core';
import { Mutation } from 'apollo-angular';
import { cancelarGasto } from './graphql-query';

export interface Response {
  data: boolean;
}

@Injectable({
  providedIn: 'root',
})
export class CancelarGastoGQL extends Mutation<Response> {
  document = cancelarGasto;
}
```

- [ ] **Step 4: Agregar el campo al modelo**

En `src/app/modules/financiero/gastos/models/gastos.model.ts`, en la clase `Gasto`, después de `finalizado: boolean;`:

```typescript
    activo: boolean;
    finalizado: boolean;
    cancelado: boolean;
```

**No** agregarlo a `GastoInput` ni a `toInput()`: no viaja en el input del backend, y mandarlo haría fallar la validación del schema.

- [ ] **Step 5: Agregar el método al service**

En `src/app/modules/financiero/gastos/service/gasto.service.ts`, agregar el import:

```typescript
import { CancelarGastoGQL } from '../graphql/cancelarGasto';
```

inyectarlo en el constructor (agregar como último parámetro, después de `saveGastoRendicionGQL`):

```typescript
    private saveGastoRendicionGQL: SaveGastoRendicionGQL,
    private cancelarGastoGQL: CancelarGastoGQL
```

y agregar el método junto a los demás:

```typescript
  onCancelarGasto(id: number, sucId: number, servidor = true): Observable<boolean> {
    return this.genericService.onCustomMutation(this.cancelarGastoGQL, { id, sucId }, servidor);
  }
```

**`onCustomMutation`, NO `onCustomQuery`.** `onCustomQuery` ejecuta
`apollo.query({ query: gql.document, ... })` (`generic-crud.service.ts:118`), y
Apollo rechaza un documento `mutation` ahí con *"Running a Query requires a
graphql Query, but a Mutation was used instead"* — el botón no haría nada. La
referencia correcta es `retiro.service.ts:44`, que usa `onCustomMutation`.

- [ ] **Step 6: Resolver el rol y la acción en el componente**

En `src/app/modules/financiero/gastos/pages/list-gastos/list-gastos.component.ts`, agregar los imports:

```typescript
import { MainService } from '../../../../../main.service';
import { ROLES } from '../../../../personas/roles/roles.enum';
```

> Verificar las rutas relativas contra `list-retiro.component.ts` en `origin/develop`, que ya hace exactamente esto; ajustar la cantidad de `../` si la profundidad difiere.

Agregar la inyección y la propiedad:

```typescript
  private gastoService = inject(GastoService);
  private sucursalService = inject(SucursalService);
  private tabService = inject(TabService);
  public mainService = inject(MainService);

  esAdmin = false;
```

En `ngOnInit`, como primera línea del método:

```typescript
  ngOnInit(): void {
    // Se resuelve aca y no en el template: llamar roles.includes(...) desde el HTML
    // lo re-evalua en cada ciclo de change detection.
    this.esAdmin = this.mainService.usuarioActual?.roles?.includes(ROLES.ADMIN) ?? false;

    if (this.data?.tabData?.data?.caja?.id) {
```

Agregar el handler, junto a `onIrACaja`:

```typescript
  onCancelarGasto(gasto: Gasto) {
    if (!gasto?.id) return;
    this.gastoService.onCancelarGasto(gasto.id, gasto.sucursalId).subscribe(res => {
      if (res) this.onFiltrar();
    });
  }
```

- [ ] **Step 7: Dar prioridad a CANCELADO en los tres getters de estado**

Sigue en el mismo archivo. La columna `estadoSolicitud` ya renderiza el badge a partir de estos tres getters, así que el cambio va adentro de ellos y **no se agrega ninguna llamada nueva desde el HTML**.

En `getEstadoSolicitudColor`, como primeras líneas del cuerpo:

```typescript
  getEstadoSolicitudColor(gasto: Gasto): string {
    if (gasto?.cancelado) {
      return '#ef5350';
    }
    const estadoColor = gasto?.preGasto?.estadoColor;
```

En `getEstadoSolicitudIcono`:

```typescript
  getEstadoSolicitudIcono(gasto: Gasto): string {
    if (gasto?.cancelado) {
      return 'cancel';
    }
    const estadoIcono = gasto?.preGasto?.estadoIcono;
```

En `getEstadoSolicitudEtiqueta`:

```typescript
  getEstadoSolicitudEtiqueta(gasto: Gasto): string {
    if (gasto?.cancelado) {
      return 'CANCELADO';
    }
    return gasto?.preGasto?.estadoEtiqueta || gasto?.preGasto?.estado || '-';
  }
```

El rojo `#ef5350` y el ícono `cancel` son los mismos que ya usa el estado `RECHAZADO` en este archivo.

- [ ] **Step 8: Agregar el ítem de menú**

En `src/app/modules/financiero/gastos/pages/list-gastos/list-gastos.component.html`, el `mat-menu` de la columna `acciones` hoy es:

```html
          <mat-menu #menu="matMenu">
            <button mat-menu-item (click)="onAdd(gasto, i)">Editar</button>
          </mat-menu>
```

Reemplazarlo por:

```html
          <mat-menu #menu="matMenu">
            <button mat-menu-item (click)="onAdd(gasto, i)">Editar</button>
            <button
              mat-menu-item
              *ngIf="esAdmin"
              (click)="onCancelarGasto(gasto)"
            >
              {{ gasto.cancelado ? 'Habilitar' : 'Cancelar' }}
            </button>
          </mat-menu>
```

- [ ] **Step 9: Verificar el build AOT**

```bash
npm run check
```

Esperado: sin errores de compilación. Se puede cancelar el proceso apenas empiezan a aparecer los warnings de dependencias CommonJS (`canvg`, `luxon`, `leaflet`, etc.) — para entonces la fase que detecta errores ya pasó.

- [ ] **Step 10: Commit**

```bash
git add src/app/modules/financiero/gastos/graphql/cancelarGasto.ts \
        src/app/modules/financiero/gastos/graphql/graphql-query.ts \
        src/app/modules/financiero/gastos/models/gastos.model.ts \
        src/app/modules/financiero/gastos/service/gasto.service.ts \
        src/app/modules/financiero/gastos/pages/list-gastos/list-gastos.component.ts \
        src/app/modules/financiero/gastos/pages/list-gastos/list-gastos.component.html
git commit -m "feat(financiero): agregar accion de cancelar gasto en la lista"
```

---

### Task 4: Verificación end-to-end y entrega

**Files:** ninguno (solo verificación).

**Interfaces:**
- Consumes: los tres branches de las Tasks 1-3.
- Produces: la aprobación del usuario, que es el gate para pushear.

- [ ] **Step 1: Levantar los tres procesos**

En tres terminales, cada uno en su repo:

```bash
# central — SIN perfil dev, puerto 8081
./mvnw -o spring-boot:run -DskipFlyway=true

# filial — CON perfil dev, puerto 8082
./mvnw -o spring-boot:run -Dspring-boot.run.profiles=dev -DskipFlyway=true \
  -Dspring-boot.run.arguments=--sifen.scheduler.enabled=false

# frontend
npm start
```

El flag `--sifen.scheduler.enabled=false` evita que el scheduler de SIFEN escriba en la DB local durante la prueba. Preguntarle al usuario si prefiere levantarlo sin ese flag.

**No matar** los procesos de `/opt/` (central alpha en 8083, filial en 8080) para liberar puertos: no son de esta sesión.

- [ ] **Step 2: Confirmar que las migraciones se aplicaron**

En los logs de cada backend, buscar que Flyway haya aplicado `V86.1` (filial) y `V157.1` / `V158.1` (central). Confirmar `Started FrancoSystemsApplication` en ambos.

- [ ] **Step 3: Elegir un caso real de la DB**

Consultar la DB local para elegir una caja que tenga gastos de verdad — no inventar un ejemplo:

```sql
SELECT g.id, g.sucursal_id, g.caja_id, g.retiro_gs, g.vuelto_gs, g.cancelado
FROM financiero.gasto g
WHERE g.retiro_gs > 0
ORDER BY g.creado_en DESC
LIMIT 10;
```

- [ ] **Step 4: Probar el flujo completo**

1. Abrir la lista de gastos en el desktop con un usuario ADM.
2. Anotar el balance actual de la caja del gasto elegido.
3. Cancelar el gasto desde el menú de 3 puntos.
4. Verificar que el badge de la columna Estado pasa a **CANCELADO** en rojo.
5. Verificar que el balance de esa caja **subió** exactamente en `retiroGs - vueltoGs`, tanto en la vista del central como en el POS de la filial.
6. Verificar que con un usuario **no** ADM el ítem de menú no aparece.
7. Volver a tocar la acción ("Habilitar") y confirmar que el balance vuelve al valor original.

- [ ] **Step 5: Esperar la aprobación explícita del usuario**

**No pushear ni abrir PRs hasta que el usuario haya probado y aprobado.** Aprobar el plan no es aprobar el resultado, y un `<task-notification>` no es una aprobación.

- [ ] **Step 6: Push y PRs draft, en orden**

Con la aprobación dada, y respetando el orden de merge:

```bash
# 1. filial
git push -u origin feature/gastos-cancelar-gasto
# PR draft → develop, título: "feat(financiero): ignorar gastos cancelados en el balance de caja"

# 2. central (recién después de que el filial esté mergeado y propagado)
# PR draft → develop, título: "feat(financiero): permitir cancelar gastos desde el central"

# 3. frontend
# PR draft → develop, título: "feat(financiero): agregar accion de cancelar gasto en la lista"
```

Cada descripción de PR tiene que incluir: qué resuelve, cómo probarlo, impacto en DB, impacto en rollback y riesgo. El del central además tiene que decir explícitamente que **depende de que la migración del filial ya esté aplicada en producción**.

- [ ] **Step 7: Bajar los procesos**

Cuando el usuario avise, bajar central, filial y frontend, y verificar que no quedaron huérfanos:

```bash
ss -ltn | grep -E '4200|8081|8082'   # tienen que estar libres
ss -ltn | grep -E '8080|8083'        # los de /opt/ tienen que seguir vivos
```
