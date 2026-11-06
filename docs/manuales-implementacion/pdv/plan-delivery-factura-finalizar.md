# Plan — FINALIZAR con factura no emite otra si PARA ENTREGA ya facturó (#221)

Rama `fix/delivery-factura-finalizar` en **filial** (desde `develop` `df879a0`) y en **desktop** (desde `develop`
`1b2b3b1d`). Sin migración ni cambios de GraphQL.

## Problema

1. Delivery ABIERTO → **PARA ENTREGA**: `DeliveryGraphQL.facturaDeEntrega` crea la factura A si la política factura
   (con lock por venta desde #210).
2. **FINALIZAR** → cobro → **Factura (F12)**: `PagoTouchComponent` abre `AddFacturaLegalDialogComponent` con un
   `new Venta()` **sin id**, y el diálogo llama a `saveFacturaLegal` sin `ventaId`. El filial emite la factura B
   (número y DE) sin saber de qué venta es.
3. `onSaveVentaDelivery` → `vincularFacturaManual` → `vincularFacturaLegalAVenta(B, venta)` la liga sin mirar si la
   venta ya tiene A. Quedan dos facturas activas y `findVigenteDeVenta` devuelve B.

El filial solo no lo puede frenar a tiempo: sin `ventaId`, lo primero que ve de la venta es el vínculo, cuando B ya
está emitida. Por eso son dos piezas (decisión de Franco, 06/11).

## Cambio

### Fase 1 — filial

1. **`FacturaLegalService`**:
   - `claveFacturaVenta(sucursalId, ventaId)` (estático): la clave `factura-venta:<suc>:<venta>` de #210, en un solo
     lugar. `DeliveryGraphQL` pasa a usarla (mismo texto, su test sigue igual).
   - `exigirSinFacturaVigente(ventaId, sucursalId)`: si `findVigenteDeVenta` devuelve una
     factura, `GraphQLException` «La venta ya tiene la factura N° <n>: no se emite
     otra. Si el cliente la necesita a su nombre, primero cancelá la N° <n> en Facturas legales.». Sin
     `@Transactional`: una excepción de un método transaccional marcaría para rollback la transacción del builder.
2. **`FacturaLegalBuilder.build()`**: si la factura trae venta con id, **al principio** (antes de leer el timbrado y
   su `numeroActual`, así la espera no deja un número viejo: auditoría B, m1) toma el lock de la venta
   (`BloqueoTransaccionalService.tomar(..., "15s")`) con `venta.getSucursalId()` (la misma sucursal que usa
   `facturaDeEntrega`, así las claves coinciden) y llama a `exigirSinFacturaVigente` con esa sucursal. Es la
   convención del builder: validación antes del primer `save`, `GraphQLException`, que `noRollbackFor` no revierte.
   - Cubre F12 con el desktop nuevo y cualquier otro llamador con venta.
   - PARA ENTREGA ya tiene el mismo lock en su transacción (el builder se suma y el lock es reentrante) y ya descartó
     la existente, así que no cambia.
   - La factura silenciosa y «Venta + Ticket» de `saveVenta`: venta nueva, sin factura, sin nadie más con su clave.
     El lock no espera y el control pasa. Salvo una colisión de `hashtext` (32 bits) con otra venta en curso: ahí
     espera y, si vence, la transacción de `saveVenta` se aborta. Despreciable; va como `shortcut:` en el código
     (auditoría A, 4).
   - La API (`FacturaLegalApiService`) no pone venta: no cambia.
3. **`saveFacturaLegal`**: con `ventaId`, el mismo control **al principio**, **sin lock** (el método no es
   transaccional y `tomar` es `MANDATORY`) y con `venta.getSucursalId()`, antes de crear persona o cliente. El del
   builder es el que vale ante una carrera; este es para no escribir nada.
4. **El cliente se asigna a la venta después de `build()`**, no antes (auditoría B, m2): hoy `ventaService.save(v)`
   corre antes y fuera de transacción, y una falla del builder dejaba la venta del delivery con el cliente de una
   factura que no existe. Es lo mismo que hace `vincularFacturaLegalAVenta`.
5. **`vincularFacturaLegalAVenta` no cambia** (auditorías A-2 y B-M1). Rechazar ahí no evita la factura B de un
   desktop viejo, que ya está emitida, y la dejaría activa y sin venta, sin rastro. Con el desktop viejo todo sigue
   como hoy; con el nuevo la factura ya viene ligada desde la emisión y el vínculo es redundante.

### Fase 2 — desktop

1. `PagoTouchComponent` (F12): con `data.delivery`, `venta.id = data.delivery.venta?.id`.
2. `AddFacturaLegalDialogComponent.onSaveFactura`: `factura.venta = this.data?.venta`, así `toInput()` manda el
   `ventaId`. Otros llamadores: `list-factura-legal` no pasa venta (sin cambio) y PagoTouch sin delivery pasa
   `new Venta()` sin id (sin cambio).
3. El rechazo se ve con el motivo del filial (el genérico) y el diálogo libera el botón (#470). El cajero cierra y
   finaliza sin factura: el delivery queda cobrado con su factura A.
4. **Efecto lateral (auditoría A, 5):** en un delivery sin A, F12 liga B a la venta en el momento de emitir. Si el
   cajero después cierra el cobro sin finalizar, B ya está en la venta, y un F12 o un PARA ENTREGA posterior la
   encuentran: el primero se rechaza y el segundo la reimprime. Hoy quedaba suelta y se emitía otra.

**Caso que deja de pasar (auditoría A, 1):** A salió SIN NOMBRE en PARA ENTREGA y el cliente pide la factura a su
nombre al pagar. Hoy recibe B y la venta queda con dos facturas (doble IVA). Con el fix, el rechazo dice cómo
seguir: cancelar A en Facturas legales (`list-factura-legal`, botón Cancelar, con su rol) y recién ahí F12. Una
factura cancelada tiene `activo=false` y `findVigenteDeVenta` la ignora. Con DE, la cancelación queda `activo=false`
recién cuando SIFEN la aprueba.

No se oculta F12 cuando el delivery ya está facturado: la pantalla no tiene el dato y el rechazo del filial alcanza
(opción descartada por Franco).

## Tabla de datos nuevos

Ninguno. `FacturaLegalInput.ventaId` ya existe en el `.graphqls` y en el filial; lo nuevo es que el desktop lo llena
en un caso más. Lo escribe `AddFacturaLegalDialogComponent`; lo lee `saveFacturaLegal`, que ya lo leía.

## Compatibilidad y orden

- **Filial nuevo + desktop viejo:** igual que hoy, porque el desktop viejo no manda `ventaId`. El desktop se
  actualiza con el diálogo *Cerrar y actualizar*.
- **Desktop nuevo + filial viejo:** el filial viejo liga B a la venta al emitirla, que es lo mismo que hoy.
- No hay orden obligatorio. Primero el filial, porque es el que corta.

## Tests

- `FacturaLegalBuilderTest`: con venta y factura vigente → `GraphQLException`, sin `save` y con el lock tomado antes
  de buscar; sin vigente → se guarda. Los constructores de los dos tests del builder reciben el mock nuevo.
- `FacturaLegalServiceTest` (nuevo o el existente): `exigirSinFacturaVigente` con vigente, ninguna y anulada.
- `FacturaLegalBuilderTest`, además: con venta y sin vigente, orden `tomar` → `findVigenteDeVenta` → lectura del
  timbrado → `save` (`InOrder`).
- `FacturaLegalGraphQL`: `saveFacturaLegal` con `ventaId` facturada → rechazo sin tocar persona, cliente, venta ni
  builder, y sin pedir el lock; con `ventaId` sin factura, el cliente se asigna a la venta después de `build`.
- Revertir el fix y ver fallar los tests de rechazo.
- Batería `./mvnw clean verify -B` (en una copia fuera del workspace, por jdtls). Desktop: `npm run check`.

## Runtime (paso 9)

Filial propio en un puerto libre contra `general` local (sucursal 24, SIFEN apagado), política de la 24 en TODAS y
timbrado no electrónico en el PDV, como en #210. Desktop `ng serve -c web` del worktree contra ese filial, usuario MR.
Se restaura todo al final.

1. **Reproducir con `develop`:** delivery → PARA ENTREGA (factura A) → FINALIZAR → F12 → dos facturas activas para
   la venta. Si no se reproduce, no se sigue.
2. **Con los dos fixes:** otro delivery. PARA ENTREGA → A; FINALIZAR → F12 → rechazo con el motivo, sin factura nueva
   ni persona o cliente nuevos; cerrar el diálogo y finalizar sin factura → delivery cobrado, venta con A.
3. **Sin regresión:** delivery con PARA ENTREGA sin factura (política que no factura delivery) → FINALIZAR → F12 →
   emite y queda ligada a la venta. Una venta común con F12 → emite como siempre.
4. **Cancelar y volver a facturar:** sobre la venta del caso 2, cancelar A en Facturas legales (timbrado no
   electrónico) → F12 emite y queda ligada.
5. **F12 y cerrar sin finalizar:** delivery sin A → F12 → cerrar el cobro → reabrir → F12 rechazado; PARA ENTREGA
   reimprime B.
6. **Desktop viejo (`develop`) + filial nuevo:** el caso 1 da lo mismo que hoy.

## Fuera de alcance

- Las ventas que ya tienen dos facturas no se tocan.
- Desktops sin actualizar siguen pudiendo emitir B, como hoy.
- La espera vencida del lock en F12 (otra operación facturando la misma venta por más de 15 s) sale con el mensaje
  genérico «Error al guardar factura legal», sin factura. Es raro: F12 aparece después de PARA ENTREGA.

## Qué queda sin verificar y cómo

- La cancelación con DE (espera la aprobación de SIFEN): en local SIFEN está apagado; se prueba con timbrado no
  electrónico.
- Que `venta.sucursal_id` y la sucursal del timbrado coincidan en todas las filiales: si no, la clave del lock y la
  búsqueda no coinciden con la factura guardada. Viene de #208, ya declarado en #210.

## Auditoría (paso 5)

Plan aprobado por Franco el 06/11.

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| Mayor: A SIN NOMBRE y el cliente pide factura a su nombre en FINALIZAR → queda rechazado | A | el mensaje dice cómo seguir (cancelar A); caso en runtime 4; decide Franco |
| Mayor: rechazar el vínculo con desktop viejo deja B activa sin venta y sin rastro | A y B | `vincular` no cambia (punto 1.5) |
| Menor: número del timbrado leído antes del lock; la espera agranda la ventana | B | lock al principio de `build` (punto 1.2) |
| Menor: cliente asignado a la venta antes de `build`, fuera de transacción | B | se mueve después (punto 1.4) |
| Menor: sucursal y lock del control temprano ambiguos | A y B | explícito: `venta.getSucursalId()`, sin lock |
| Menor: colisión de `hashtext` en `saveVenta` | A | `shortcut:` en el código |
| Menor: F12 liga B al emitir en un delivery sin A | A | descrito (2.4) y caso en runtime 5 |
| Menor: faltan tests de orden, de vínculo y del control temprano sin lock | B | agregados |

Sin hallazgos en: GraphQL, enum, migración, variables (no cambian); central, mobile y PWA (no llaman a estas
mutations; el central va por la API REST, que no pone venta); deadlock con `lote-saldo` o `timbrado_detalle` (orden
consistente, venta nueva en `saveVenta`); contrato «GraphQLException = nada escrito» y turno del contador (se
respeta); vuelta atrás (sin datos ni esquema).

## Auditoría del diff (paso 8)

- Fijo 2 (esquema y migración): N/A, el diff no trae migración, `.graphqls`, entidad ni enum.
- Condicionales A y B: N/A, el diff no toca release ni replicación.
- Fijo 1 (autorización), Fijo 3 (contrato) y corrección, con un revisor: sin bloqueantes. Sin resolvers ni firmas
  nuevas; `Venta` tiene `@Id` simple en el filial, así que `findById` no trae la de otra sucursal; el desktop no
  interpreta el texto del error; la factura silenciosa no puede quedar rollback-only.
  - **Mayor:** `ventaService.save` del cliente, ahora después de `build`, no tenía try/catch. Un error ahí salía
    como rechazo con la factura ya emitida: el desktop libera el botón sin avisar y no se imprime. → try/catch con
    log, como la persona, y test que devuelve el timbrado.
  - **Menor:** la venta se guardaba con la foto leída al principio (persona, lock y DE en el medio). → se relee antes.
  - **Declarado:** la sucursal del builder es `venta.sucursal_id` y la de PARA ENTREGA, `delivery.sucursal_id`.
    Coinciden en la práctica (las dos salen de `sucursalId` del filial). Una venta con `sucursal_id` NULL no
    encuentra su factura, igual que en `facturaDeEntrega`; `VentaService.save` siempre la completa.
