# Plan — dejar de escribir `movimiento_caja` (filial, issue #80, parte 2)

Rama: `fix/caja-sin-movimiento-caja`, **desde la rama de #243** (`fix/caja-saldo-desde-fuentes`): toca los mismos
servicios. Base del PR: `develop`; hasta que #243 se mergee el PR muestra también sus commits.
Pieza: **filial**. Sin migración, sin cambio de GraphQL.

## Por qué ahora se puede

#243 sacó el último lector que importaba (el saldo de gastos y retiros). Lo que queda leyendo la tabla:

| Lector | Qué lee | Con este cambio |
|---|---|---|
| `PdvCajaService.aplicarTotales{Apertura,Cierre}DesdeMovimientos` | CAJA_INICIAL / CAJA_FINAL, **solo si** el conteo no tiene `conteo_moneda` (respaldo) | se mantiene: lee las filas históricas y las del caso raro (ver abajo) |
| `PdvCajaService.reabrirCaja`, `ConteoGraphQL.editarConteoCaja` | desactivan CAJA_FINAL / CAJA_* del conteo reemplazado | se mantienen (sin filas, no hacen nada) |
| `GastoService.saveAndSend` (edición) | reescribe las filas GASTO del gasto | se quita: ninguna lectura usa GASTO |
| `CobroGraphQL.deleteCobro` | desactiva filas VENTA | se mantiene (inofensivo) |
| GraphQL `movimientoCaja`, `movimientoCajas`, `saveMovimientoCaja` | — | se mantienen por contrato; ningún cliente las usa (desktop, PWA, mobile: 0) |

La issue suponía que un conteo hecho «solo con totales» (sin `conteo_moneda`) deja la fila CAJA_INICIAL como
único registro del monto. Medido en producción (07/11, todo 2026): de ~41.800 conteos de apertura y cierre,
**9** tienen monto en `movimiento_caja` sin `conteo_moneda` (el último el 26/09 en farmacia, 12/07 en bodega).
Los ~1.100 restantes sin `conteo_moneda` son conteos **en cero** (filas CAJA_* con 0, verificado en la filial 1
de farmacia). El desktop y la app Android mandan siempre la lista de billetes.

## Arreglo (un commit `fix(caja): ...`)

Se deja de escribir:
1. VENTA: `CobroGraphQL.saveCobro` y `CobroDetalleGraphQL.saveCobroDetalleList`.
2. GASTO: `GastoService.save` / `saveAndSend` (alta y edición) y `GastoDetalleService.save`.
3. RETIRO: `RetiroDetalleService.saveAndSend`.
4. CAJA_INICIAL / CAJA_FINAL: `ConteoGraphQL.crearMovimientosDeConteo` escribe la fila de **una moneda** solo
   si el total del input de esa moneda es distinto de 0 **y** el input no trae billetes de esa moneda con
   cantidad > 0. Se evalúa con la lista del input (`saveConteoMoneda` corre después, en la misma
   transacción), no con la base. Es el mismo respaldo por moneda de `elegirTotalMoneda`: un conteo con
   billetes de Gs y un total en R$ sin billetes sigue dejando la fila de R$ (auditoría B). En la edición
   (`editarConteoCaja`) se mantiene la desactivación de las filas del conteo reemplazado (cajas históricas).
5. Se sacan los campos que quedan sin uso (`MovimientoCajaService` en `GastoService`, `GastoDetalleService`,
   `RetiroDetalleService`; `MovimientoCajaGraphQL` en los cobros y en `ProgramarPrecioGraphQL` si no se usa)
   y se verifica con `grep "saveMovimientoCaja\|movimientoCajaService.save" src/main` que no queda ningún
   escritor fuera del conteo.

Las filas históricas no se tocan. La tabla y su entidad quedan (las lee el respaldo y la réplica al central;
sacarla de la réplica es central #165).

## Tests

- Alta de gasto, retiro y cobro en efectivo: `movimientoCajaService` sin llamadas (`verifyNoInteractions`).
- Conteo con billetes: sin movimientos; conteo sin billetes y total > 0: tres movimientos como hoy; conteo
  sin billetes en cero: sin movimientos.
- Conteo con billetes de Gs y `totalRs` ≠ 0 sin billetes de R$ → solo la fila de R$ (decide el criterio).
- Conteo con lista de billetes en cantidad 0 y total > 0 → fila de esa moneda.
- `GastoServiceEdicionTest`: sus **dos** tests (edición y alta) pasan a `verifyNoInteractions(movimientoCajaService)`.
- Balance con billetes en una moneda y fila histórica CAJA_* en otra → toma las dos, como hoy.
- Balance: `PdvCajaServiceBalanceTest` sigue pasando (el respaldo no cambia).

## Prueba por pantalla

Filial local 8082, desktop web, caja de MR:
1. Abrir caja con billetes, venta en efectivo, retiro, gasto, cerrar: **ninguna fila nueva** en
   `movimiento_caja` de esa caja; el ticket y el balance del cierre dan lo mismo que con `develop`.
2. Reabrir la caja (ADMIN) y volver a cerrar; editar un conteo: sin errores.
3. Caja histórica con filas CAJA_* y sin billetes (si no hay en local, sembrarla por SQL): el balance toma
   la apertura de la fila, como hoy.
4. Caja que cruza el despliegue: abierta con `develop` (con filas), venta y retiro con esta rama, cierre con
   esta rama → el balance y el control de efectivo cuadran.
5. Anular un cobro (si hay pantalla en el filial) sin errores.

## Eje A / B

- Contrato: sin cambio de GraphQL, tabla ni enum. Central: no lee la tabla; deja de recibir filas nuevas por la
  réplica (menos volumen).
- Rollback: solo código, **y nunca por debajo de #243**. A la versión de #243 no pasa nada (vuelve a escribir;
  los días sin filas no los lee nadie). A una versión **anterior** a #243, la validación vieja suma una tabla
  sin filas nuevas: en las cajas abiertas después de este cambio da 0 y **rechaza todo gasto y retiro**. Por eso:
  - este PR se mergea recién cuando #243 esté desplegado en todas las filiales del canal;
  - se libera en un release propio, posterior al de #243, para que el rollback automático de `check-update`
    (vuelve a la versión anterior) caiga en la de #243;
  - nota de release: «rollback mínimo: la versión de #243».
- Central: deja de recibir filas nuevas por la réplica (menos volumen). Siguen leyendo la tabla, sin uso real:
  `VentaService.cancelarVenta` (desactiva filas VENTA; sin filas nuevas no hace nada) y `GastoDetalleService.save`
  del central (valida con la suma; ningún cliente lo usa). Anotado para central #165.

## Auditoría del plan (paso 5)

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| Rollback por debajo de #243 bloquea gastos y retiros | A, B (mayor) | regla de rollback, merge después del despliegue de #243, release propio |
| Criterio global de conteo pierde el total de una moneda sin billetes | B (mayor) | criterio por moneda, con el input |
| Lista con billetes en 0 y total > 0 | B | cubierto por el criterio por moneda; test |
| `GastoServiceEdicionTest` tiene dos tests sobre la tabla | B | los dos a `verifyNoInteractions` |
| Lectores del central sin uso (`cancelarVenta`, `GastoDetalleService.save`) | A | anotados para #165 |
| Caja que cruza el despliegue | A | prueba por pantalla 4 |
| Campos que quedan sin uso; grep final de escritores | B | punto 5 |
| `CobroDetalleGraphQL` usaba el `sucursalId` como caja | B | desaparece; dicho en el commit |

## Sin verificar

- Los 9 conteos con monto solo en `movimiento_caja`: medido en el central, que tiene huecos de réplica de
  `conteo_moneda`; en las filiales pueden ser menos. Ninguno después de septiembre.

## Resultado de las pruebas (2026-11-07)

- Batería del filial: 911/911.
- Filial de esta rama en el 8082 (sucursal 24), desktop web, PRUEBACAJA2. Antes de empezar, la última fila
  de `movimiento_caja` era la 70196.
  1. Caja 1195: apertura con billetes (50.000 Gs, R$ 20), venta de 2.000, cierre con billetes (52.000 Gs,
     R$ 20) → diferencia 0 en las dos monedas, ninguna fila nueva. Venta, retiro y gasto en las cajas 1185 y
     1191 y cierre de la 1191 con billetes: tampoco.
  2. Reapertura de la 1195 (`reabrirCaja` del filial, con ADMIN temporal) y nuevo cierre por pantalla con
     55.000 → diferencia +3.000; `editarConteoCaja` del cierre a 52.000 → diferencia 0. Sin errores ni filas
     nuevas. La reapertura se llamó directo al filial: la pantalla pasa por el central, y el central local no
     tiene las cajas del filial (sin réplica).
  3. Caja 1186 (abierta el 27/09 con el código anterior, apertura solo en `movimiento_caja`): el balance toma
     los 200.000 de la fila.
  4. Caja 1196 con apertura sin billetes, como la dejaba el código anterior (escribe su fila de respaldo,
     70197), venta de 2.000 y retiro de 10.000 por pantalla, cierre con billetes por 62.000 → diferencia 0;
     la única fila nueva es la de la apertura.
  5. N/A: el filial no tiene pantalla para anular cobros; `cancelarVenta` rechaza a propósito (lo hace el
     central).
