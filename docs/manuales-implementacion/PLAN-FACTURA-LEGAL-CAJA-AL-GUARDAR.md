# Plan — la factura del PDV guarda su caja

Rama: `fix/factura-legal-guardar-caja` (desde `develop`). Pieza: filial. Sin issue.

## Problema

Un cajero emite una factura legal desde el diálogo del PDV con «Imprimir desde esta PC». La factura se
emite pero el ticket no sale, y aparece «No autorizado: ... ANALISIS DE CAJA».

Camino:

1. Desktop `FacturaLegalService.onSaveFactura` → `saveFacturaLegal` (sin `printerName`), y después
   `ticketEscpos(FACTURA, facturaLegalId)`.
2. El desktop manda `cajaId` (`factura.caja = cajaService.selectedCaja`), pero
   `FacturaLegalGraphQL.saveFacturaLegal` lo descarta (`// TODO: Implementar mapeo de caja`).
   La factura del diálogo no tiene venta, así que queda con `caja_id` y `venta_id` en null.
3. `TicketEscposGraphQL.factura` → `exigirAccesoARegistroDeCaja(cajaDe(factura))` con caja null →
   `exigirRol(ANALISIS DE CAJA)`: no hay dueño contra quien comparar y el cajero no tiene el rol.

## Fase 1 — mapear la caja (un commit)

- `saveFacturaLegal`: `pdvCajaService.findById(cajaId).ifPresent(facturaLegal::setCaja)` en lugar
  del TODO. Una caja inexistente sigue como hoy (sin caja), no rechaza la emisión.
- Sin control de acceso nuevo al guardar: emitir no se restringe más que hoy.
- Test `FacturaLegalGraphQLCajaTest` (`@Mock PdvCajaService`, captura del `BuildRequest`): con `cajaId` la factura que llega al builder tiene esa caja;
  sin `cajaId` o con una caja inexistente queda en null. Revertir el fix → el primero falla.

## Datos

| Dato | Escribe | Lee |
|---|---|---|
| `factura_legal.caja_id` (columna existente) | `saveFacturaLegal` (nuevo) y `FacturaService` (venta, ya existía) | `TicketEscposGraphQL.cajaDe` (permiso al imprimir), `imprimirFacturasPorCaja`, réplica al central |

Sin migración, sin cambio de esquema GraphQL, sin cambio en el desktop.

## Efectos colaterales revisados

- `imprimirFacturasPorCaja(caja)` ahora incluye también las facturas del diálogo de esa caja. Ruta
  sin uso desde el desktop (llamador comentado en `adicionar-caja-dialog.component.ts`); su NPE con
  una factura sin cliente ya existía con ventas sin cliente y queda fuera de alcance.
- `saveFacturaLegal` no es transaccional y la caja se busca antes de crear persona/cliente y de
  consumir el timbrado: si `findById` falla, nada se escribió y el cajero reintenta sin duplicar.
- `factura_legal` replica filial → central con `caja_id`. La caja ya está en el central (las facturas
  de venta ya llevan caja), así que no hay FK colgada.
- El central mapea la caja EAGER por `(sucursal_id, caja_id)` sin `@NotFound`: una factura cuya caja
  falte en el central por un hueco de réplica de `pdv_caja` fallaría al cargarse. La exposición ya
  existe con las facturas de venta (que siempre llevan caja); el fix solo suma volumen. Se acepta.
- Solo llamador: el diálogo del desktop (PDV y lista de facturas). PWA y mobile no usan esta
  mutation. El diálogo no manda `ventaId`; la venta se vincula después con la misma `selectedCaja`,
  así que la caja de la factura y la de la venta coinciden en el flujo normal.
- `FacturaLegalRepository` dice que `caja_id` «casi nunca se persiste»: se actualiza el comentario.

## Pruebas

- `./mvnw clean verify -B`.
- Por pantalla, filial propio en 8082 (sucursal 24, SIFEN apagado) + desktop web: cajero de prueba
  con caja abierta, emitir factura desde el PDV con impresión por cliente → sale el ticket, sin aviso.
  Contraprueba: la misma factura reimpresa por un usuario sin el rol y que no es dueño → sigue
  rechazada.

### Resultado (2026-11-13)

- `./mvnw clean verify -B`: 1019 tests, 0 fallas (corrido en una copia fuera de `dev-frc`, por jdtls).
  Con el fix revertido, `conCajaLaFacturaQuedaAtadaAEsaCaja` falla.
- Por pantalla: filial con el fix en 8082 (sucursal 24, SIFEN apagado, timbrado no electrónico 114
  activado un rato para el PDV 25) + desktop `develop` en web, `imprimeEstaPc()` forzado en memoria y
  `imprimir()` reemplazado por un registro de bytes. Cajero PRUEBACAJA, caja 1197: «Finalizar con
  Factura» → factura 33157 con `caja_id=1197`, `ticketEscpos(FACTURA)` devuelve 7531 bytes, sin aviso.
- Contraprueba con la misma sesión: con `caja_id` y `venta_id` en null (el momento del primer pedido
  con el código viejo) sale «No autorizado: se requiere el rol ANALISIS DE CAJA.»; solo con la caja,
  pasa. PRUEBACAJA2 (no dueño, sin el rol) sigue rechazado: «solo el dueño o el rol ANALISIS DE CAJA».
- Orden: los tests corrieron antes de la auditoría del diff (paso 8 después del 9).
- Datos de prueba restaurados (timbrado, usuarios, caja cerrada); la factura 33157 queda emitida en local.

## Auditoría del plan (paso 5)

- Eje A (contrato): aceptado con reservas — EAGER del central sin `@NotFound` (aceptado, ya existía),
  `FacturaLegalApiService` sin caja (anotado abajo), delivery con caja distinta (no ocurre: el
  diálogo no manda venta).
- Eje B (reversibilidad): aceptado con reservas — NPE de `imprimirFacturasPorCaja` (ruta sin uso,
  ya existía), test con `@Mock PdvCajaService`.

## Auditoría del diff (paso 8)

Fijo 2 (esquema, migración y espejo): N/A, el diff no toca migraciones, `.graphqls` ni entities
[ev: `git diff --name-only`]. Fijos 1 y 3, un auditor, riesgo bajo; el detalle de seguridad va por privado:

- **M1 (aplicado):** con `ventaId`, el `cajaId` del cliente pisaba la caja de la venta en `cajaDe`, y el
  dueño de la caja de la venta perdía la reimpresión. Ahora con venta manda `venta.caja` (como
  `FacturaService`); el `cajaId` solo se usa sin venta. Test `conVentaMandaLaCajaDeLaVenta`. Para
  facturas con venta el permiso queda igual que antes (`cajaDe` ya caía a `venta.caja`); cambia solo
  que `caja_id` se persiste.
- Un id de caja inexistente deja la factura sin caja y no rechaza la emisión.
- Fijo 3: sin cambio de esquema; desktop viejo y nuevo ya mandan `cajaId`; PWA no usa la mutation;
  rollback del JAR inocuo.

## Sin verificar / fuera de alcance

- Por pantalla, la rama con venta (`ventaId`, «Finalizar con factura» de un delivery): cubierta por
  test y por equivalencia con el código viejo, no ejercitada en la UI.

- Facturas ya emitidas sin caja: siguen pidiendo el rol al reimprimirse por `ticketEscpos`. Se
  pueden completar por SQL si hace falta (no se toca en este PR).
- Diálogo abierto sin caja seleccionada (`selectedCaja` null): sigue igual que hoy (pide el rol).
- Facturas emitidas desde el central (`isServidor` → `saveFacturaLegalToFilial` →
  `FacturaLegalApiService`): siguen sin caja (mismo TODO). No pasan por el PDV.
- `selectedCaja` vieja o de otro usuario: la factura queda atada a esa caja. Ningún reporte suma
  `factura_legal` por caja, así que no hay impacto contable.
