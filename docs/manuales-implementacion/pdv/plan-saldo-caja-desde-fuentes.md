# Plan — saldo de caja para gastos y retiros desde las tablas fuente (filial, issue #80)

Rama: `fix/caja-saldo-desde-fuentes` (desde `develop`). Pieza: **filial**. Sin migración, sin cambio
de GraphQL.

## Problema

Antes de un gasto, el filial valida que haya efectivo con
`MovimientoCajaRepository.totalEnCajaByCajaIdandMonedaId`:

```sql
select sum(mc.cantidad) from financiero.movimiento_caja mc
where mc.caja_id = ?1 and mc.moneda_id = ?2 and mc.activo = true
```

Medido en **producción** el 2026-11-07 (centrales bodega `:5552` y farmacia `:5551`, solo lectura,
filas creadas desde el 1/10/2026):

- **Todas las filas RETIRO y GASTO tienen `activo = NULL`**: bodega 2.993 y 1.473 (las 18
  sucursales), farmacia 1.274 y 15. Nadie lo setea en `RetiroDetalleService` ni en `GastoService`, y
  el `activo = true` las descarta. **El saldo con el que se valida un gasto nunca descuenta los
  retiros ni los gastos anteriores**: solo suma la apertura y las ventas.
- Las ventas canceladas no desactivan su fila VENTA en el filial (`VentaService` inyecta
  `MovimientoCajaService` y no lo usa): su efectivo sigue contando.
- Después del cierre, la fila CAJA_FINAL se suma encima de la CAJA_INICIAL.
- En la copia local de una filial (sucursal 24), el saldo guardado no coincide con el recalculado en
  1.061 de 1.087 cajas (mediana de la diferencia: 1.020.000 Gs).

## Qué cambió desde que se abrió la issue (julio)

| La issue dice | Hoy en `develop` |
|---|---|
| 5 llamadores validan | Corre de verdad **solo el de gasto** (`GastoService.saveAndSend`, al crear). `RetiroDetalleService.save` y `GastoDetalleService.save` validan, pero el retiro real entra por `saveAndSend`, que **no valida**; `saveGastoDetalle` no tiene uso en el desktop |
| `generarBalance` no lee `movimiento_caja` | Desde 25f567c (jun) la lee como **respaldo** de apertura y cierre (`aplicarTotales*DesdeMovimientos`) |
| Dejar de materializar `movimiento_caja` | **No se puede todavía**: `saveConteo` acepta un conteo **solo con totales** (mobile), sin `conteo_moneda`; ahí la fila CAJA_INICIAL/CAJA_FINAL es el único registro del monto. Además la leen `reabrirCaja` y `editarConteoCaja` |

## Arreglo

El saldo esperado sale de **`generarBalance`**, la misma cuenta del cierre:

```
saldo(moneda) = apertura + pagos en efectivo + vuelto (negativo) − retiros − (gasto − vuelto del gasto)
```

Ya excluye ventas canceladas, retiros con `estado = CANCELADO` y gastos `cancelado`, y toma la
apertura de `conteo_moneda` con respaldo en CAJA_INICIAL. Es además **lo que el desktop ya muestra
como disponible** en `adicionar-gasto-dialog` / `adicionar-retiro-dialog` (`−diferencia` del balance
de una caja abierta): backend y pantalla pasan a usar la misma cuenta.

Simulación en **producción** (cajas abiertas desde el 1/10/2026): el saldo de la cuenta nueva
justo antes de cada operación, con la apertura tomada como en `establecerTotalesApertura` (en
farmacia, **116 de 871 cajas** abren solo con totales, sin `conteo_moneda`):

| | Bodega | Farmacia |
|---|---|---|
| Gastos (Gs / R$) | 1.447 / 21, **0 rechazados** (margen mínimo 413.820 Gs) | 12 / 1, **0 rechazados** |
| Retiros Gs | 1.738, **1 rechazado** | 382, 0 |
| Retiros R$ | 1.164, **2 rechazados** (372 con margen exacto 0) | 790, **1 rechazado** |
| Retiros US$ | 59, 0 (58 con margen 0) | 99, 0 (83 con margen 0) |

Los 4 rechazos, uno por uno:

- **Bodega, caja 2155 / suc. 22:** dos retiros idénticos de 1.800.000 Gs, a **0,21 s** uno del otro
  (ids 2032 y 2033). Al cierre se contaron 1.259.500 Gs, que es lo que quedaba después del
  primero: el segundo es un **doble envío**. El rechazo es correcto.
- **Los otros tres (R$; bodega cajas 2123/s20 y 4581/s11, farmacia 244/s7):** en cada caja hay una
  venta en efectivo que **hoy** figura CANCELADA y que es anterior al retiro. La simulación usa el
  estado actual y no hay fecha de cancelación. Como el diálogo del desktop ya bloquea con esta
  misma cuenta, si el retiro pasó por ahí la venta todavía no estaba cancelada en ese momento, y la
  cuenta nueva lo habría dejado pasar. Dos de esos retiros quedaron verificados sin problema.

Los márgenes en 0 son retiros "de todo" al cierre: hace falta la tolerancia.

**Retiros duplicados por doble envío** (mismo detalle, misma caja, a menos de 5 s), desde el 1/8:
2 en bodega (suc. 1 caja 6964: 0,12 s; suc. 22 caja 2155: 0,21 s), ninguno en farmacia. Con esa
distancia las dos validaciones pueden correr a la vez y pasar las dos: la validación sola no los
frena.

## Fase 1 — fix + tests (un commit `fix(caja): ...`)

La validación pasa a los **resolvers GraphQL**, que ya inyectan `PdvCajaService` (o pueden hacerlo
sin ciclo). No va en los servicios: `GastoService`, `GastoDetalleService`, `RetiroDetalleService` y
`PdvCajaService` usan `@AllArgsConstructor`, así que Lombok mete también los campos `@Autowired` en
el constructor. `PdvCajaService` ya recibe `GastoService` y `RetiroDetalleService` por constructor:
inyectarlo de vuelta arma un ciclo entre constructores que `allow-circular-references` no resuelve,
y **el filial no arranca** (auditoría A).

1. `PdvCajaService`:
   - `static double saldoEnCaja(PdvCajaBalanceDto b, String denominacion)` = apertura + pagos en
     efectivo + vuelto − retiros − gastos netos para GUARANI / REAL / DOLAR (con caja abierta es
     `−diferencia`, lo mismo que muestra el desktop). Un balance `null` o una denominación que no
     sea de esas tres da 0.
   - `void exigirSaldo(PdvCajaBalanceDto b, String denominacion, Double monto, String mensaje)`:
     rechaza con `GraphQLException(mensaje)` si `monto > saldo + 0.005`. La tolerancia hace que el
     backend nunca sea más estricto que la pantalla por un error de redondeo en Rs/US$: los retiros
     "de todo" con margen exacto 0 son comunes (auditoría B).
2. `GastoGraphQL.saveGasto`: si el gasto es nuevo, un solo `pdvCajaService.generarBalance(caja)` y
   `exigirSaldo` para Gs, Rs y US$ **antes** de `service.saveAndSend`. Mensajes sin cambio. Si no
   llega la caja: «Caja no encontrada» (hoy es un NPE).
3. Se saca la validación de `GastoService.saveAndSend`: lo único que la llama es `saveGasto` (el
   otro llamador, `saveVueltoGasto`, edita y no valida).
4. `GastoDetalleGraphQL.saveGastoDetalle` (sin uso en los clientes): inyecta `PdvCajaService`, misma
   validación antes de `service.save`; se saca la de `GastoDetalleService.save`.
5. `RetiroDetalleService.save`: no tiene ningún llamador (el retiro entra por `saveAndSend`). Se
   borra el override en vez de portarle la validación.
6. Borrar `MovimientoCajaService.totalEnCajaPorCajaIdAndMonedaId`, `findTotalVentaByCajaIdAndMonedaId`
   y sus dos queries del repositorio.
7. **Se sigue escribiendo `movimiento_caja`** igual que hoy: la leen el respaldo del cierre,
   `reabrirCaja` y `editarConteoCaja`.

### Tests (Mockito, sin Spring)

- `PdvCajaServiceBalanceTest`, sobre `generarBalance` real:
  - **el caso del bug:** apertura 100.000, retiro 60.000, gasto anterior 10.000 → saldo 30.000. Con la
    query vieja daba 100.000 (las filas RETIRO/GASTO tienen `activo NULL`); un gasto de 50.000 tiene
    que rechazarse.
  - ignora retiro cancelado, gasto cancelado y venta cancelada; descuenta el vuelto del gasto.
  - reales: saldo `12.3 + 7.7` y un monto igual al que muestra la pantalla pasa (tolerancia).
  - balance `null` y denominación desconocida (PESO) → 0.
- `GastoGraphQL` (como `CajaDetalleAccesoTest`, `@InjectMocks`): gasto nuevo igual al saldo se
  guarda; mayor tira el mensaje de siempre y **no** llama a `saveAndSend`; edición no valida; sin caja
  da «Caja no encontrada».
- Los tests existentes con `@InjectMocks GastoService` / `GastoGraphQL` (`GastoServiceEdicionTest`,
  `ReimpresionEsperaTest`, `CajaDetalleAccesoTest`) tienen que seguir pasando.

### Verificación de arranque

`mvn` empaqueta igual con un ciclo de beans; lo único que lo detecta es arrancar. Criterio de la
fase: el jar arranca contra la base local y responde `/api/version`.

### Prueba por pantalla

Filial propio en 8082 (sucursal 24, `sifen.enabled=false`), desktop web contra él, usuario MR:

1. Abrir caja, venta en efectivo con vuelto, un retiro.
2. Gasto dentro del saldo → se guarda y el balance del cierre cuadra.
3. Gasto igual al disponible que muestra el diálogo → se guarda.
4. Rechazo del backend: abrir el diálogo de gasto (toma el disponible), hacer un retiro desde otra
   pestaña, volver y guardar un gasto que ya no entra → el filial lo rechaza con el mensaje.
5. Gasto en reales con centavos.
6. Tiempo de `generarBalance` en una caja grande (p95 = 282 ventas por caja en bodega), medido en el
   log. **No** se reemplaza por una consulta agregada aunque sea lenta: rompería la igualdad con el
   cierre, que es el punto del arreglo. Si pasa de ~1 s, se frena y se consulta.

## Retiro real (`saveRetiro`)

**Retiros: Franco decidió que sí validan** (2026-11-07). Va en un **commit aparte** de la misma fase:
- en `RetiroGraphQL.saveRetiro`, **antes de guardar la cabecera**: un balance y `exigirSaldo` por
  moneda con la suma de los detalles del input. Si se validara después del `save`, el auto-flush
  haría que el balance contara el propio retiro y rechazaría todo retiro "de todo" (auditoría B);
- tests: retiro igual al saldo pasa, dos detalles en monedas distintas, mayor rechaza sin guardar.

## Bloqueo por caja contra el doble envío (aprobado por Franco, 2026-11-07)

`PdvCajaService.bloquearSaldoCaja(cajaId)`: `BloqueoTransaccionalService.tomar("caja-saldo:" + cajaId,
"15s")`, el mismo mecanismo que los lotes. Si la espera vence: «Hay otra operación en curso en esta
caja. Reintentá en unos segundos.» Se toma **antes** de leer el balance:

- **Gasto nuevo**: `saveGasto` envuelve en un `TransactionTemplate` solo el bloqueo, el balance, la
  validación y `service.saveAndSend`. La impresión queda afuera, así que un error al imprimir no
  deshace el gasto y el bloqueo se suelta al guardar. De paso, el gasto y sus filas de
  `movimiento_caja` quedan en una sola transacción.
- **Retiro nuevo**: `saveRetiro` ya es `@Transactional`. El bloqueo dura hasta el commit, que llega
  después de imprimir (como hoy). Un retiro de otra caja no espera.

El segundo envío espera a que el primero confirme y recién ahí lee el balance (READ COMMITTED). Si
ya no alcanza el efectivo, se rechaza: es el caso de la caja 2155. **Límite:** si alcanza para los
dos, el duplicado pasa igual. Deduplicar el envío es otro arreglo, en el desktop.

Tests: el bloqueo se pide antes del balance (orden verificado con `InOrder`), y con un retiro de
otra caja no se pide. La espera real se prueba por pantalla: dos pestañas guardando a la vez en la
misma caja, la segunda espera y queda rechazada.

## Fuera de alcance (anotado, no se toca)

- Dejar de escribir `movimiento_caja` y sacarla de la réplica (central #165): primero hay que
  guardar los totales del conteo sin `conteo_moneda` en otro lado.
- `activo = NULL` en RETIRO/GASTO, y `GastoService.saveAndSend` reusando el mismo `MovimientoCaja`
  para las 6 filas (un gasto con más de un monto deja **una sola** fila: 3 casos, 0 referencias con
  más de una fila). Deja de importar para la validación; la tabla queda solo como respaldo de conteos.
- Los 6 valores de `PdvCajaTipoMovimiento` sin productor: el enum viaja por la réplica; borrarlo no
  da nada.
- `totalDelivery` en `generarBalance` (`=+`, nunca se aplica): el cierre ya cuadra sin él (el envío
  entra en el pago del cobro).

- Concurrencia: dos gastos o retiros a la vez en la misma caja pueden pasar los dos, igual que hoy
  (ningún código toma lock). La ventana entre leer y guardar crece de una consulta a cientos de ms.

## Auditoría del plan (paso 5)

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| Ciclo de constructores (`@AllArgsConstructor` incluye los `@Autowired`): el filial no arranca | A, alta | validación en los resolvers; verificación de arranque como criterio |
| `generarBalance` hace ~2 consultas por venta | A/B, media | se mide (punto 6); sin consulta agregada, para no romper la igualdad con el cierre |
| Tests con `@InjectMocks` existentes | A, media | listados; `saldoEnCaja(null)` = 0 |
| Caja sin apertura o sin caja en el input | A, baja | 0 casos en los datos; «Caja no encontrada» y test |
| Redondeo de `double` en Rs/US$ | B, media | tolerancia 0,005 y test en reales |
| Validar el retiro después del `save` cuenta el propio retiro | B, media | si se aprueba: antes de la cabecera, commit aparte |
| El test de regresión del primer borrador fallaba por el mock en 0, no por el bug | B, media | reemplazado por el caso apertura 100.000 / retiro 60.000 / gasto 50.000 |
| Concurrencia sin lock | B, baja | queda como hoy; anotado arriba |
| Datos nulos rompen `generarBalance` con error genérico | B, baja | sin cambio: el desktop ya falla cerrado sobre el mismo balance |
| Contrato con los clientes | A | sin impacto: ningún cliente usa `saveGastoDetalle`/`saveRetiroDetalle` ni depende de los mensajes |

## Eje A / B

- **Contrato:** sin cambio de GraphQL, enum, tabla ni variable de entorno. Desktop y PWA no se tocan.
- **Canal:** `develop` → filiales alpha a los 15 min; luego beta (6 farmacia) y master (18 bodega).
- **Rollback:** solo código; la versión anterior funciona igual (no hay esquema nuevo).
- **Riesgo:** un gasto que hoy pasa y con la cuenta nueva no entra. Medido: 0 casos en 4 meses.

## Sin verificar

- Fecha real de cancelación de las 3 ventas de los rechazos en R$ (no se guarda en ninguna tabla).
- Tiempo de `generarBalance` en el hardware de una filial (se mide en local, punto 5).
