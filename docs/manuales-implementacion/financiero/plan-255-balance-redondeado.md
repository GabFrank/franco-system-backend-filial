# Plan #255: la diferencia de caja en reales y dólares sale redondeada a centavos

Issue: GabFrank/franco-system-backend-filial#255. Rama: `fix/caja-diferencia-redondeada` (desde `develop`).
Pieza: **filial** solamente.

## Problema

`PdvCajaService.generarBalance` suma montos en `double`: aperturas, cierres, pagos, vueltos, retiros, gastos, tarjeta y
transferencia, por moneda. En reales y dólares los montos tienen centavos, y la suma arrastra el error de punto
flotante. Caja 3016 (alpha, 10/11/2026): `diferenciaDs = -0.5700000000000021` (cierre 27,00 contra esperado 27,57).

Quién lo ve:
- `balancePorCajaId` / `getBalance` (API del desktop y del central): devuelve el número con ruido.
- `EventosCaja.atributosCierre` (eventos `caja.cerrada` y `caja.cierre_con_diferencia`, `ConteoGraphQL` l.~147): marca
  `con_diferencia = gs != 0 || rs != 0 || ds != 0`. Una caja que cuadra exacto en dólares puede quedar con
  `diferencia_ds` = 1e-14 y **marcarse con diferencia**: alerta falsa. Además guarda el número con ruido en el evento.
- El ticket de cierre (`ImpresionService` l.274) ya formatea con `%.2f`: no cambia.

## Enfoque

Un solo lugar: al final de `generarBalance`, redondear a 2 decimales (HALF_UP, por `BigDecimal.valueOf`) **solo
`diferenciaRs` y `diferenciaDs`**, ya calculadas con los totales crudos. Todos los lectores (API, eventos, ticket) leen
ese DTO.

- **Los totales no se redondean** (auditoría B2): `saldoEnCaja` (#80, control de gastos y retiros) los suma de nuevo, y
  redondear cada total por separado puede correr el saldo varios medios centavos con datos de más de 2 decimales. En
  alpha hay 247 líneas de cobro y 153 movimientos de caja en reales con fracciones de centavo (vueltos y aumentos
  convertidos, 2023 a 03/2026, p. ej. −0,0033 R$). Los totales ya se muestran con `%.2f` / `number:'1.2-2'`.
- **La tolerancia de 0,005 de `superaSaldo` se queda**: `saldoEnCaja` suma doubles y 50,3 − 10,1 sigue dando
  40,199999…; sacarla rechazaría el retiro de todo el saldo.
- **Guaraníes no se tocan:** las sumas de enteros en double son exactas.
- **2 decimales fijos:** real y dólar tienen `decimales = 2` en `financiero.moneda` (verificado en alpha; la columna
  nace con `DEFAULT 0` en V88.3, así que no se lee de ahí). El balance identifica las monedas por denominación.
- Con los datos viejos con fracciones de centavo, una diferencia de −0,0033 R$ pasa a 0: no se puede contar en billetes.

## Fase única

- `PdvCajaService`: helper `centavos(Double)` null-safe y `setDiferenciaRs/Ds(centavos(...))` antes del `return` de
  `generarBalance`, dentro del `if` que arma el balance.
- **Tests** (`PdvCajaServiceBalanceTest`, fallan con el código viejo):
  - dólares: apertura 0, pagos 20,1 + 7,47, cierre 27 → `diferenciaDs` == −0,57 exacto (el viejo da
    −0,5700000000000003);
  - caja que cuadra: pagos 0,1 + 0,2 en dólares, cierre 0,3 → `diferenciaDs` == 0,0 exacto (el viejo da −5,55e−17) y
    `EventosCaja.atributosCierre(...).get("con_diferencia")` == false;
  - reales con un vuelto viejo de −0,00333… y cierre que cuadra en centavos → `diferenciaRs` == 0,0;
  - los tests existentes (guaraníes, saldo, tolerancia) siguen pasando sin cambios.

## Fuera de alcance (anotado)

- El central tiene el mismo cálculo (`PdvCajaService.java:468` del central) para su detalle de caja: mismo ruido.
  Issue aparte: GabFrank/franco-system-backend-servidor#604.
- Eventos ya guardados con el número con ruido: no se corrigen.

## Datos nuevos

Ninguno: sin columna, migración, campo GraphQL ni variable de entorno.

## Contrato y canales (eje A)

Solo cambia el valor de campos `Float` que ya existen (menos dígitos). El desktop y el central los muestran
formateados. Sale a las filiales alpha por `develop`; en bodega y farmacia el balance en dólares/reales también se usa
(cajas con dólares), y el cambio solo quita el ruido.

## Pruebas de runtime

Filial de la rama por jar en el 8092 contra la base del alpha (`SUCURSALID=2`, SIFEN apagado):
1. `balancePorCajaId` de la caja 3016 → `diferenciaDs = -0.57`.
2. Una caja de alpha con dólares que cuadre (o una caja de prueba abierta y cerrada con montos con centavos que den 0)
   → `diferenciaDs = 0` y el evento de cierre sin `con_diferencia`.
3. Regresión: balance de una caja solo en guaraníes, igual que con el filial del alpha (comparar las dos respuestas).

## Resultado de las pruebas de runtime (alpha, 11/11/2026)

Filial de la rama por jar en el 8092 contra `general@5552` (`SUCURSALID=2`, SIFEN apagado). Se comparó
`balancePorCajaId` (18 campos: aperturas, cierres, ventas, retiros, gastos y vueltos en Rs/Ds, total general y las
tres diferencias) contra el filial del alpha (8080, `develop` 5.6.0-alpha.19) para **las 2.386 cajas de la
sucursal 2**:

| Resultado | Cajas |
|---|---|
| Idénticas en los 18 campos (incluye todas las que son solo en guaraníes) | 2.196 |
| Cambian **solo** `diferenciaRs` / `diferenciaDs`, por menos de medio centavo (máximo 0,005) | 190 |
| …de esas, ruido o fracción de centavo que pasa a 0 (ya no se marcarían con diferencia) | 34 |

- Caja 3016 (la de la issue): `diferenciaDs` −0,5700000000000021 → **−0,57**.
- Caja 3009: −0,930232558139535 → −0,93.
- Ningún total cambia: el saldo para gastos y retiros (`saldoEnCaja`) queda igual.

**Cierre real por pantalla:** caja 3018 (MR, abierta desde el runbook), cerrada desde el PDV contra el filial de la rama
con conteo 302.500 Gs y 60 US$ (esperado 60,15; ventas en dólares 62,949999999999996 en double). Balance después del
cierre: filial actual `diferenciaDs` = −0,14999999999999591; filial de la rama **−0,15**; totales idénticos.

El evento de cierre no se pudo ver: en alpha el emisor de eventos no registra nada (`eventos.outbox_evento` vacía,
antes y después del cierre). `EventosCaja.atributosCierre` es una función del mismo DTO que la API devolvió, y su
`con_diferencia` está cubierto por el test `cajaQueCuadraEnDolaresNoQuedaConDiferencia`.

## Auditoría del diff (paso 8)

Diff de 8 líneas en `generarBalance`. Fijo 1 (autorización) y Fijo 2 (esquema): N/A, sin resolver, permisos, migración
ni `.graphqls`. Fijo 3 (contrato): mismos campos `Float`, con menos dígitos; el desktop los formatea a 2 decimales y
compara contra 0. Condicionales: no se disparan.

## Auditoría (paso 5, 11/11/2026)

| # | Hallazgo | Qué se hizo |
|---|---|---|
| A1 | Lectores: API, eventos, saldo de gastos/retiros, ticket, desktop (`analisis-diferencia`, `superaDisponible`) | Ninguno depende del valor crudo; las comparaciones contra 0 mejoran |
| A2 | Central: mismo cálculo, no lee balances del filial | Anotado fuera de alcance |
| A3 | `moneda.decimales` nace con `DEFAULT 0` en V88.3 | Verificado en alpha: real y dólar = 2; se usan 2 fijos |
| B1 | Persistencia: solo el evento, que nadie relee para decidir | Sin acción |
| B2 | Redondear los totales puede correr `saldoEnCaja` con datos de más de 2 decimales | **Cambio de enfoque: solo las diferencias** |
| B3 | No sacar la tolerancia de `superaSaldo` | Anotado |
| B5 | Tests con valores que reproduzcan el ruido | 20,1 + 7,47 / 27 y 0,1 + 0,2 / 0,3, verificados |

## Sin verificar

- Producción: cuántas cajas viejas en reales tienen diferencias de fracciones de centavo (pasan a 0).
