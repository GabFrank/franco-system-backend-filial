# Cancelar gasto de cajero

Fecha: 2026-08-06
Estado: aprobado, pendiente de plan de implementación

## Problema

Hoy no existe forma de cancelar un gasto de cajero. La única operación
disponible es `deleteGasto`, que borra la fila: no deja rastro de qué pasó ni de
quién lo hizo, y rompe la trazabilidad contable de la caja.

Cuando un cajero registra un gasto, el monto se **resta** del balance de su caja
(`PdvCajaService.generarBalance`). Al cancelarlo, esa plata volvió, así que el
balance tiene que **volver a sumarla**.

La cancelación debe poder hacerse **solo desde el servidor central**, y solo por
un usuario con rol ADM.

## Antecedente: cancelar retiro

Esta funcionalidad es el espejo directo del cancelamiento de retiro implementado
el 2026-08-02. Vale la pena leer esos tres commits antes de implementar:

| Repo | Commit | Qué hizo |
|---|---|---|
| central | `d7e85fa6` | mutation `cancelarRetiro`, `EstadoRetiro.CANCELADO`, migración que activa replicación central→filial de `financiero.retiro` |
| filial | `1587e50` | valor nuevo del enum + `.graphqls`, migración idempotente `ALTER TYPE ... ADD VALUE`, `continue` en `generarBalance` |
| frontend | `1f58e2da` | toggle Cancelar/Habilitar en el mat-menu, visible solo para `ROLES.ADMIN` |

Las tres decisiones de diseño que se heredan de ahí:

1. **El balance no se recalcula ni se persiste.** La plata vuelve porque
   `generarBalance` ignora los registros cancelados al recomputar.
2. **El filtro va en Java, no en la query**, donde se opera sobre entidades ya
   cargadas. Un `WHERE cancelado <> true` en SQL descartaría también las filas
   con `NULL`, que son todos los gastos históricos. Donde el filtro sí va en SQL
   (queries agregadas), se escribe `IS NOT TRUE`, que sí incluye los `NULL`.
3. **El balance se calcula en los dos backends por separado.**
   `PdvCajaService.generarBalance` existe en central (línea ~303) y en filial
   (línea ~386), y ambos hacen el mismo
   `totalGastoGs += (gasto.getRetiroGs() - gasto.getVueltoGs())`. Cancelar solo
   en el central dejaría al POS de la sucursal mostrando otro balance.

## Diferencia respecto del retiro

`Retiro` ya tenía columna `estado` con un enum de Postgres, así que alcanzó con
agregarle el valor `CANCELADO`. **`Gasto` no tiene columna de estado**: solo
`activo` y `finalizado`. Por eso acá hay que introducir la representación del
estado cancelado desde cero.

Ojo con `activo`: **sí lo lee lógica de negocio**. Las 4 queries agregadas de
`GastoRepository` ya filtran `AND g.activo = true`. `finalizado`, en cambio, solo
se persiste. El balance de caja no filtra por ninguno de los dos.

## Decisiones tomadas

### Modelo: columna `cancelado BOOLEAN` nullable

```sql
ALTER TABLE financiero.gasto ADD COLUMN IF NOT EXISTS cancelado BOOLEAN;
```

`NULL` = no cancelado. Cubre todos los gastos históricos sin backfill.

Se descartó reusar `activo` por dos razones: viaja en `GastoInput`, así que
cualquier `saveGasto` podría revertir la cancelación sin querer; y ya tiene
significado propio en las queries agregadas (`AND g.activo = true`), así que
sobrecargarlo con "cancelado" mezclaría dos conceptos en una sola columna.

Se descartó crear un enum `financiero.estado_gasto`: sería más parecido al
retiro y más extensible, pero obliga a sincronizar un tipo nuevo entre las N+1
DBs independientes. Un booleano aditivo es más barato y suficiente para el único
estado que hace falta hoy.

### Comportamiento: toggle, sin auditoría

`cancelarGasto(id, sucId)` alterna `cancelado = !cancelado`, igual que
`cancelarRetiro` y `cancelarVenta`. Si el ADM se equivoca de fila, lo revierte él
mismo. No se registran `cancelado_por` ni `cancelado_en` — se evaluó y se decidió
mantener la paridad exacta con el retiro y el PR mínimo.

### Alcance: balance + reportes

La cancelación se refleja en:

- El balance de caja, en central y filial.
- Los reportes agregados de gastos del central: `gastosPorCategoria`,
  `gastosPorMes` y sus variantes `SinSucursal`.

Queda **fuera**, por investigación previa:

- `GraficoAggregationService` no necesita cambios: delega en
  `gastoService.gastosPorCategoria` / `gastosPorMes`, así que arreglando las
  queries del repositorio los gráficos quedan cubiertos solos.
- `EnteService` va por `PreGastoService.getFinancialSummary`, que agrega
  `PreGasto`, no `Gasto`.

## Arquitectura

```
[Frontend list-gastos]  --cancelarGasto(id, sucId)-->  [Central]
                                                          |
                                              gasto.cancelado = true
                                                          |
                                        replicación lógica central -> filial
                                              (WHERE sucursal_id = N)
                                                          |
                                                       [Filial]
                                                          |
                                       generarBalance ignora el cancelado
                                          -> el monto vuelve al balance
```

## Cambios por repo

### Backend central (`franco-system-backend-servidor`)

- `domain/financiero/Gasto.java`: campo `private Boolean cancelado;`
- `service/financiero/GastoService.cancelarGasto(Gasto)`: `@Transactional`,
  toggle `cancelado = !cancelado`.
  **Persiste con `repository.save()`, NO con `this.save()`**: el override de
  `save()` publica `GastoRealizadoEvent`, que dispara la push notification de
  gasto realizado. Cancelar no es realizar un gasto. Es exactamente el mismo
  detalle documentado en `RetiroService.cancelarRetiro`.
- `graphql/financiero/GastoGraphQL.java`: método `cancelarGasto(Long id, Long sucId)`,
  que resuelve el gasto y lanza `GraphQLException` si no existe.
- `resources/graphql/financiero/gasto.graphqls`:
  - `cancelado: Boolean` en `type Gasto`
  - `cancelarGasto(id:ID!, sucId: ID):Boolean!` en `extend type Mutation`
- `service/financiero/PdvCajaService.generarBalance` (línea ~303): saltear el
  gasto cancelado antes de acumular, con comentario explicando por qué el filtro
  va en Java.
- `repository/financiero/GastoRepository.java`: agregar `AND (g.cancelado IS NOT TRUE)`
  a las 4 native queries: `gastosPorCategoria`, `gastosPorCategoriaSinSucursal`,
  `gastosPorMes`, `gastosPorMesSinSucursal`.
- Migración `V157.1__add_cancelado_gasto.sql`: la columna.
- Migración `V158.1__replicar_gasto_central_a_filial.sql`:

  ```sql
  UPDATE configuraciones.replication_table
  SET replicate_central_to_branch_with_filter = true
  WHERE table_name = 'financiero.gasto';
  ```

  La fila ya existe como `BRANCH_TO_MAIN` desde `V112` (línea 65).
  `gasto_detalle` no se toca: la cancelación solo modifica la cabecera.

### Backend filial (`franco-system-backend-filial`)

- `domain/financiero/Gasto.java`: campo `private Boolean cancelado;`
- `service/financiero/PdvCajaService.generarBalance` (línea ~386): saltear el
  gasto cancelado, con el mismo comentario.
- `resources/graphql/financiero/gasto.graphqls`: `cancelado: Boolean` en
  `type Gasto`. **Sin mutation** — la cancelación es exclusiva del central.
- Migración `V86.1__add_cancelado_gasto.sql` (la última en `develop` es `V85.1`).

El filial no tiene `service/grafico/` ni las queries agregadas, así que la parte
de reportes no aplica de este lado.

### Frontend (`frc-sistemas-integrados-angular`)

En `modules/financiero/gastos`:

- `graphql/cancelarGasto.ts` + entrada en `graphql/graphql-query.ts` + método en
  el service.
- `models/gastos.model.ts`: campo `cancelado`, y agregarlo al query
  `filterGastos`.
- `pages/list-gastos`: toggle Cancelar/Habilitar en el menú de acciones, con
  `*ngIf="esAdmin"`. `esAdmin` se resuelve en `ngOnInit`
  (`mainService.usuarioActual?.roles?.includes(ROLES.ADMIN) ?? false`), **no en
  el template**: llamar `roles.includes(...)` desde el HTML lo re-evalúa en cada
  ciclo de change detection.

**Reutilizar la columna de estado existente.** La lista ya tiene la columna
`estadoSolicitud` (`list-gastos.component.ts:43`), rotulada "Estado", que
renderiza un badge con color + ícono + etiqueta tomados de
`gasto.preGasto.estado`. Para un gasto común de cajero (sin `preGasto`) hoy
muestra `'-'` — justo los gastos que se van a cancelar.

El cambio va **dentro de los tres getters existentes**
(`getEstadoSolicitudColor`, `getEstadoSolicitudIcono`, `getEstadoSolicitudEtiqueta`),
dándole prioridad a `cancelado` sobre el estado de la solicitud. Beneficio
concreto: **no se agrega ninguna llamada a función nueva en el HTML**, que es lo
que prohíbe la regla de performance del CLAUDE.md del frontend. Una columna nueva
sí habría sumado llamadas.

Efecto colateral aceptado: en un gasto originado en un `preGasto`, el badge pasa
a mostrar "CANCELADO" en lugar del estado de la solicitud. Ese estado sigue
visible en `list-pre-gastos`, y el botón "Ir a solicitud de gasto" de la misma
celda no se toca.

## Orden de despliegue

**La migración del filial va primero.** Si el central empieza a replicar una
columna que la filial todavía no tiene, el apply worker de la suscripción falla y
la replicación queda trabada acumulando WAL.

Como el filial se auto-actualiza cada 15 minutos, en la práctica:

1. Mergear el PR del filial a `develop`.
2. Esperar a que las filiales tomen la versión (~15 min) y verificar que la
   migración se aplicó.
3. Recién ahí mergear el central.
4. El frontend puede ir en cualquier momento después del central.

## Riesgo y rollback

Riesgo: **medio**. Toca el cálculo de balance de caja, que es dinero real, y
activa replicación de una tabla nueva en sentido central→filial.

- Las dos migraciones son aditivas (`ADD COLUMN` idempotente, `UPDATE` sobre una
  tabla de configuración). No hay `DROP` ni `RENAME`, así que un rollback de JAR
  no deja la DB en un estado que el código viejo no pueda leer: el código
  anterior simplemente ignora la columna `cancelado`.
- El riesgo real no es la columna sino el **orden**: central antes que filial
  traba la replicación. Ver la sección anterior.
- Un gasto ya cancelado que vuelve a código viejo reaparece sumando en el
  balance. Es el mismo comportamiento que tendría el retiro y es reversible
  volviendo a subir la versión.

## Verificación

- `./mvnw clean package` en ambos backends.
- `npm run check` (build AOT) en el frontend.
- Prueba manual con los tres procesos arriba: central 8081 sin perfil `dev`,
  filial 8082 con perfil `dev`, frontend con `npm start`. Abrir una caja con
  gastos, cancelar uno desde el central, y confirmar que el balance sube tanto
  en el central como en el POS de la filial, y que el badge de la lista pasa a
  CANCELADO.

## Fuera de alcance

- **Validación del rol ADM en el backend.** En el retiro la restricción quedó
  solo en el frontend; la mutation es invocable por cualquier usuario autenticado
  que arme el GraphQL a mano. Se replica tal cual por consistencia. Endurecer
  ambos lados es un trabajo separado que aplicaría también a `cancelarRetiro` y
  `cancelarVenta`.
- **Gastos originados en un `PreGasto`.** Se evaluó bloquear su cancelación y se
  decidió no hacerlo: se permiten igual que cualquier otro. Si aparece
  inconsistencia en el flujo de rendición, se trata aparte.
- Auditoría de quién canceló y cuándo.
