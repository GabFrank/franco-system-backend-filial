# Plan #148 — `cajasWithFilters` y el filtro por `pdv_caja.estado`

## Problema

`PdvCajaRepository.findAllWithFilters` filtra `c.estado = :estado`. Ningún componente escribe
`pdv_caja.estado`: está NULL en todas las cajas (farmacia filial 1: 5.366/5.366, alpha 2.379/2.379,
`general` local 1.120/1.120). Con cualquier estado no nulo la query devuelve 0 filas sin avisar.

Hoy no hace daño: el desktop manda `estado: estado || null` y `list-caja` no tiene `<mat-select>`
para `estadoControl`.

## Decisión (Franco, 2026-11-20)

**Rechazar con error.** No se deriva el estado: de los 6 valores del enum solo `EN_PROCESO` y
`CONCLUIDO` salen de `activo`/`fecha_cierre`/`verificado`; `NECESITA_VERIFICACION`,
`EN_VERIFICACION` y la diferencia CON/SIN_PROBLEMA no tienen fuente (`tuvo_problema` también está
NULL, salvo 2 cajas locales). El filtro `verificado` ya cubre la verificación.

## Fase 1 — `fix(caja)`: rechazar el filtro por estado

- `PdvCajaRepository.findAllWithFilters`: quitar la condición `c.estado = :estado` y el parámetro.
- `PdvCajaService.findAllWithFilters`: si `estado != null`, `GraphQLException` («El filtro por
  estado de caja no está soportado: use verificado»); si no, delega sin `estado`.
- **Se mantiene** el argumento `estado: PdvCajaEstado` de `cajasWithFilters` en `pdv-caja.graphqls`
  y la firma del resolver: el desktop lo declara en sus tres queries (`cajasWithFilters`,
  `…Activo`, `…Turno` de `caja/graphql/graphql-query.ts`). Quitarlo rompe la validación GraphQL.
- Test `PdvCajaServiceFiltroEstadoTest` (Mockito, como `PdvCajaServiceAccesoTest`):
  - con estado no nulo tira `GraphQLException` y no consulta el repositorio;
  - con estado nulo delega al repositorio.
  - Comprobar que el primer caso **falla con el código viejo**.

- `CLAUDE.md` §«Caja abierta = `activo`, no `estado`»: reemplazar «`findAllWithFilters` todavia filtra
  por el» por el rechazo nuevo.

Sin migración, sin enum nuevo, sin cambio de esquema GraphQL.

## Tabla de datos nuevos

N/A: no nace ningún campo, columna ni clave.

## Fuera de alcance

- Central: `PdvCajaRepository.findAllWithFilters` (línea ~67) y `findAllForAnalisisDiferenciasNative`
  (~113) tienen el mismo filtro roto. Es otro repo; se informa a Franco.
- Columna `estado` y enum: quedan (quitarlos es un cambio de esquema de 2 versiones, sin beneficio).

## Pruebas

- `./mvnw clean verify -B` (en copia fuera de `dev-frc`, por jdtls).
- Runtime: filial propio en 8082 con SIFEN apagado.
  - Sin estado, por la pantalla: **«Últimas cajas» del POS (venta touch)**, el único llamador que va
    al filial (`servidor=false`, `ultimas-cajas-dialog.component.ts:57`). La «Lista de cajas» de
    financiero va al **central** (`caja.service.ts` `servidor = true` por defecto): no sirve para esto
    (hallazgo del eje B).
  - Con estado: ninguna pantalla lo manda; GraphQL directo al :8082 con `Authorization: Token`.
    Esperado: el mensaje del rechazo (lo propaga `GraphqlExceptionHandler`).

## Despliegue

Merge a `develop` → filiales alpha en ≤15 min. Sin migración: el rollback es volver el jar.
