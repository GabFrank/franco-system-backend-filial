# Plan: el descuento de la venta en el ticket y en la factura silenciosa

Rama: `fix/venta-descuento-en-ticket-y-factura-silenciosa` (sale de `origin/develop` @ `d15fc2d`).
Pieza: filial. Un PR. Sin migraciones.

## Problema

El PDV guarda el descuento global de la venta como una línea de cobro
(`operaciones.cobro_detalle.descuento = true`, siempre en guaraníes). `venta.total_gs` queda bruto.
Dos lugares no leen esa línea:

1. **Ticket simple** — `VentaGraphQL.printTicket58mm` suma el descuento solo desde el parámetro
   `cobroDetalleList`. Los llamadores que no lo tienen mandan lista vacía o `null`, y el ticket sale
   con «Desc. 0» y «Final» = total bruto:
   - `reimprimirVenta` (lista vacía), cuando la venta no tiene factura;
   - `imprimirPagare` (lista vacía);
   - `DeliveryGraphQL.saveDeliveryEstado` (`PARA_ENTREGA`, sin factura) y `reimprimirDelivery` (`null`).

   En la primera impresión sí sale, pero suma **todas** las líneas de descuento del input, mientras que
   `CobroGraphQL.saveCobro` guarda solo la primera: si el PDV mandara dos, el ticket diría otra cosa que
   la base.
2. **Factura silenciosa** — la ruta `FACTURA_SILENCIOSA` de `saveVenta` arma el `FacturaLegalInput` con
   `totalFinal = venta.totalGs` y sin `descuento`. `FacturaLegalBuilder` recalcula el total desde los
   ítems y el descuento (null → 0), y `SifenService.java:1944` le pasa a SIFEN `factura.getDescuento()`.
   Resultado: factura y DE por el total bruto, cuando se cobró el neto.
   Evidencia: venta 80078 (base local alpha, 2026-09-23), total 6.000, descuento 500, factura 30044
   con `total_final` 6.000 y `descuento` NULL.

La ruta con factura electrónica (`FacturaService.crearFacturaLegalDesdeVenta`) ya lo hace bien: lee el
cobro de la base (factura 30010: 72.000 − 10.500 = 61.500). Con eso se arreglan el delivery facturado y
«Venta + Ticket».

## Decisión

Una sola regla para las tres puertas: **el ajuste de la venta sale del cobro guardado en la base**. El
input del PDV es solo el respaldo para cuando todavía no hay cobro con id, igual que hoy en
`crearFacturaLegalDesdeVenta`.

## Fases

### Fase 1 — `AjusteCobro`: una sola cuenta del descuento y el aumento

- Nuevo `service/operaciones/AjusteCobro.java`: valor inmutable con `descuento`, `aumento` y
  `getNeto()` (= descuento − aumento), y dos fábricas estáticas:
  `deDetalles(List<CobroDetalle>)` y `deInputs(List<CobroDetalleInput>)`. Toman `valor × cambio`,
  con `cambio` null → 1 (hay 343 descuentos de 2023-02 a 2024-01 con `cambio` NULL, todos en Gs; hoy
  `crearFacturaLegalDesdeVenta` tiraría NPE sobre ellos) y `valor` null → 0.
- `CobroDetalleService.ajusteDe(Cobro cobro, List<CobroDetalleInput> respaldo)`: si el cobro no es null y
  tiene id, `deDetalles(findByCobroId(id))`; si no, `deInputs(respaldo)`. Acepta `cobro` null y
  `respaldo` null: los caminos de delivery pasan los dos en null. [auditoría A, hallazgo 5]
- `FacturaService.crearFacturaLegalDesdeVenta` pasa a usar `ajusteDe(venta.getCobro(), cobroDetalleList)`.
  Sin cambio de comportamiento, salvo la NPE con `cambio` NULL.
- Tests (`AjusteCobroTest`, JUnit 5 puro, como `PoliticaFacturacionServiceTest`):
  descuento solo; aumento solo; los dos → neto; `cambio` null → 1; `valor` null → 0; lista vacía/null
  → 0; una línea de pago o de vuelto no suma.
- Commit: `fix(venta): calcular el ajuste del cobro en un solo lugar`.

### Fase 2 — el ticket lee el descuento del cobro guardado

- `printTicket58mm`: `descuento` sale de `cobroDetalleService.ajusteDe(cobro != null ? cobro :
  venta.getCobro(), cobroDetalleList).getDescuento()`, en lugar del bucle sobre el input. El bucle
  sigue leyendo el vuelto del input, como hoy.
- Se imprime solo el descuento, como hoy. El aumento sigue fuera del ticket (ver «Fuera de alcance»).
- Se corrige el comentario desactualizado de `DeliveryGraphQL` («Para delivery no hay CobroDetalle…
  sin descuentos»): `crearFacturaLegalDesdeVenta` lee el cobro de la base.
- Tests: la cuenta queda cubierta por `AjusteCobroTest`. El cableado (qué cobro se consulta) no tiene
  test unitario: `printTicket58mm` escribe directo a la impresora y el repo no tiene tests con
  contexto Spring. Se verifica a mano (ver abajo).
- Commit: `fix(ticket): mostrar el descuento de la venta al reimprimir y en delivery`.

### Fase 3 — la factura silenciosa lleva el descuento

- Se extrae el armado del input de `FACTURA_SILENCIOSA` a un método estático package-private
  `inputFacturaSilenciosa(Venta, List<VentaItem>, Long usuarioId, boolean credito, AjusteCobro)`, que
  además carga `descuento = max(ajuste.getNeto(), 0)` y `totalFinal = bruto − descuento`. El builder distribuye el
  descuento en los parciales (`ParcialesCalculator`) y SIFEN lo prorratea (fix de #135).
- **Precio del ítem: `vi.getPrecio()`** (lo que cobró el PDV), igual que `crearFacturaLegalDesdeVenta`,
  con respaldo a `precioVenta.precio − valorDescuento` si fuera null (en 2026: 0 de 7.028 ítems). Hoy
  usa `precioVenta.precio`, que difiere de `venta_item.precio` en 134 de 7.027 ítems de 2026: en esas
  ventas la factura no cuadra con lo cobrado aunque no haya descuento. [auditoría B, hallazgo 2]
- **Guarda: si el neto es mayor o igual al bruto, no se factura.** El armado tira `GraphQLException`
  («el descuento cubre el total de la venta»), que el `catch (RuntimeException armado)` existente
  convierte en devolución de turno. La venta se guarda sin factura, sin tocar la transacción. Sin esta
  guarda, un descuento del 100% llega a `SifenService.java:2062`, que tira `IllegalArgumentException`
  dentro de `crearDocumentoElectronico` (`@Transactional` REQUIRED, l.191). Eso deja rollback-only la
  transacción de `saveVenta` y **se pierde la venta ya cobrada**: los `catch` del builder y de
  `saveVenta` no la salvan. `DescuentoDialogComponent` no tiene tope. [auditoría B, hallazgo 1]
- **El aumento no entra en la factura silenciosa** (neto acotado a ≥ 0). Si el builder recibe un
  descuento negativo, sube `total_final` (`ParcialesCalculator.java:89-96`). Pero SIFEN ignora un
  descuento global ≤ 0 (`SifenService.java:1439`), así que la factura y el DE quedarían con totales
  distintos. Hoy la silenciosa ya sale bruta con aumento, así que no cambia nada. [auditorías A-2 y B-3]
- El `try/catch` del armado que devuelve el turno no cambia.
- Tests (`VentaGraphQLFacturaSilenciosaTest`), pasando el resultado por `ParcialesCalculator.calcular`
  como hace el builder, que recalcula el total y descarta el `totalFinal` del input:
  - 500 sobre 6.000 → `descuento` 500, total 5.500;
  - sin descuento → `descuento` 0, total bruto;
  - ítem con `precio` distinto de `precioVenta.precio` → se factura `precio`;
  - descuento igual al total → `GraphQLException`;
  - con aumento → `descuento` 0, total bruto (lo mismo que hoy).
  **Revertir el fix y ver que el primer caso falla.**
- Commit: `fix(factura): la factura silenciosa lleva el descuento de la venta`.

## Datos nuevos

No nace ninguna columna ni clave. Se agrega un **escritor** a un campo que ya existe:

| Dato | Escritor nuevo | Lectores (ya existen) |
|---|---|---|
| `financiero.factura_legal.descuento` en la ruta silenciosa | `VentaGraphQL.inputFacturaSilenciosa` → `FacturaLegalGraphQL.saveFacturaLegal` | `FacturaLegalBuilder` (parciales y `total_final`), `SifenService` (DE), `printTicket58mmFactura` y `reimprimirFacturaLegal` |

## Verificación

- `./mvnw -o clean verify -B`, leído del log.
- Manual, contra el filial local (perfil `dev`, 8082) y el desktop con `ng serve -c web`:
  1. Venta con descuento F12 y «Venta + Ticket» con la política que no factura → ticket con Desc.
  2. Reimprimir esa venta desde «Últimas ventas» → mismo descuento (hoy sale 0).
  3. Reimprimir la venta 24242 (sucursal 2, sin factura, descuento de 2.000 con `cambio` NULL) → sale
     el descuento y no revienta.
  4. Venta sin ticket con la política de intervalo (`FACTURACOUNTDOWN`) y descuento → la factura
     silenciosa queda con `descuento` y `total_final` neto (consulta a `financiero.factura_legal`).
  5. Delivery con descuento pasado a «para entrega» sin factura → ticket con Desc.

Sin impresora térmica en esta máquina, los pasos 1, 2 y 5 se verifican por el log del filial o con la
impresora que tenga configurada el usuario. **Queda sin verificar** si no hay impresora: en ese caso se
reporta como verificado solo por lectura de código y por el test de `AjusteCobro`.

## Fuera de alcance

- **Aumento en la ruta con factura electrónica**: `crearFacturaLegalDesdeVenta` pasa el neto (puede ser
  negativo) y SIFEN no lo prorratea. Es un defecto previo, igual al que se evita en la silenciosa.
- **La misma venta perdida en la ruta con factura electrónica**: un descuento del 100% en
  «Venta + Ticket» ya llega hoy a la `IllegalArgumentException` de `SifenService.java:2062`. Es previo a
  este fix y conviene un issue aparte: validar antes de escribir o `noRollbackFor` en
  `crearDocumentoElectronico`.

- **Aumento (recargo) en el ticket**: `printTicket58mm` lo calcula pero no lo imprime ni lo suma al Final.
  Es otra decisión de formato del ticket.
- **Facturas ya emitidas sin el descuento**: 4.197 en la base local (mayo 2025 – marzo 2026) y la 30044.
  Un DE aprobado no se edita: corregirlas es nota de crédito o rectificación con la contadora. Se trata
  aparte, con `frc-factura-iva-fix-expert`.
- El descuento por ítem (`venta_item.descuento_unitario`): no se usa desde 2024-01.

## Registro del ciclo

- Paso 1: rama creada desde `origin/develop`, upstream desvinculado para que el push no vaya a `develop`.
- Paso 2: `frc-filial`, `frc-fullstack`, `frc-factura-iva-fix-expert`.
- Paso 5: auditoría del plan con 2 agentes (ejes A y B), sin verse entre ellos. Ver «Hallazgos de la auditoría».
- Paso 6: plan aprobado por Franco el 2026-09-24, incluyendo el PR del central para A-1.
- Desktop: N/A, porque el PDV ya manda y guarda el descuento bien; todo el defecto está en el filial.
- Central: **no es N/A** (auditoría A, hallazgo 1). El ticket y la factura no se generan en el central,
  pero `factura_legal` se replica y `FacturaLegalService.convertToDto` (Excel de facturas,
  `generarExcelFacturas`) vuelve a restar el descuento a parciales que ya vienen netos. Ver
  «Hallazgos de la auditoría».

## Hallazgos de la auditoría (paso 5)

Cada hallazgo se verificó contra el código o la base antes de aplicarlo.

| # | Eje | Sev. | Hallazgo | Qué se hizo |
|---|---|---|---|---|
| B-1 | B | alta | Descuento ≥ total en la silenciosa → `IllegalArgumentException` en SIFEN dentro de la transacción de la venta → se pierde la venta cobrada | Verificado (`SifenService.java:191, 2062`; el diálogo de descuento no tiene tope). Guarda en fase 3 + test |
| B-2 / A-3 | A y B | media | La silenciosa factura `precioVenta.precio − valorDescuento`, no lo cobrado (`vi.getPrecio()`): 134 ítems de 2026 difieren | Verificado. Fase 3 usa `vi.getPrecio()` |
| A-2 / B-3 | A y B | media/baja | Aumento: factura y DE quedarían con totales distintos | Neto acotado a ≥ 0 en la silenciosa. El caso en la ruta electrónica queda fuera de alcance |
| A-4 | A | baja | El builder descarta `totalFinal` del input: el test no probaría lo guardado | Los tests pasan por `ParcialesCalculator.calcular` |
| A-5 | A | baja | Delivery pasa `cobro` null | `ajusteDe` acepta null |
| B-4 | B | baja | El cableado del ticket queda sin test | Aceptado. Se reporta como verificado a mano o no verificado |
| A-1 | A | **alta** | Excel de facturas del central: `porcentajeDesc = descuento / totalFinal` restado a parciales que ya son netos → gravadas e IVA descontados dos veces | Verificado: las 765 facturas locales con descuento tienen parciales netos, así que **ya pasa hoy** en toda factura electrónica con descuento. Este fix lo extiende a las silenciosas. **Decide el usuario**: PR en el central (ver abajo) o issue aparte |

Verificado sin riesgo: no cambia ningún contrato GraphQL, enum, env var ni migración. Los clientes
viejos no se enteran. `findByCobroId` dentro de la transacción de `saveVenta` ve los detalles recién
guardados (flush AUTO; la ruta electrónica ya lo hace). `printTicket58mmFactura` solo recalcula el
descuento cuando viene 0/NULL, así que no resta dos veces. Hay 486 cobros con más de un descuento, y
sumarlos todos es lo que cierra con el total.

### A-1: decisión del usuario (2026-09-24) — va un PR en el central, con el mismo nombre de rama

Plan del central: `franco-system-backend-servidor/docs/manuales-implementacion/PLAN-EXCEL-FACTURAS-DESCUENTO-DOBLE.md`.

En `convertToDto`, en lugar de restar `descuento / totalFinal`, escalar cada parcial por
`totalFinal / (p0 + p5 + p10)`. Si los parciales ya son netos, el factor es 1. Si alguna factura vieja
quedó con parciales brutos (el bug 2 histórico del filial), el factor los lleva al neto. Así se
arreglan los dos casos sin depender de cómo se grabó cada factura. Tests: parciales netos → sin
cambio; parciales brutos → escalados; sin parciales → 0. Canal: el Excel lo arma el central, y el
arreglo del filial y el del central son independientes entre sí (no hay orden de despliegue forzado).
