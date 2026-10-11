# Factura legal: `activo` nace en NULL (issue #131)

Estado: **plan auditado (paso 5) y aprobado por Franco el 11/10/2026**. Rama `fix/factura-legal-activo-null` en filial, central y desktop.

## El problema

`FacturaLegal.activo` es un `Boolean` sin valor inicial en el entity del filial. La columna
`financiero.factura_legal.activo` tiene `DEFAULT true`, pero Hibernate manda el `null` explícito en
el `INSERT` y el default no se aplica.

Las tres vías que crean una factura pasan por `FacturaLegalBuilder.build()`:

| Vía | Quién arma el entity | ¿Pone `activo`? |
|---|---|---|
| PDV (silenciosa, «Finalizar con Factura») | `FacturaService.crearFacturaLegalDesdeVenta` | no |
| Carga manual desde el desktop | `FacturaLegalGraphQL.saveFacturaLegal` | no |
| API externa | `FacturaLegalApiService` (`:224`) | sí, `true` |

No es de larga data: en bodega el papel pasa de `true` a `NULL` el 29–30/10/2025, y en el alpha el
27/11/2025. Es una regresión de la reescritura de `saveFacturaLegal` (sept.–nov. 2025).

### Medición en el central de bodega (11/10/2026)

| Tipo | `activo` | Cantidad | Creadas |
|---|---|---|---|
| Papel | `NULL` | 7.298 | 29/10/2025 – 20/05/2026 |
| Papel | `true` | 1.409.974 | hasta 30/10/2025 |
| Papel | `false` | 45 | |
| Electrónica | `NULL` | 925.976 | 07/03/2025 – 11/10/2026 |
| Electrónica | `true` | 26 | |
| Electrónica | `false` | 3.072 | |

Farmacia no se midió, pero **sigue facturando en papel** (Franco, 11/10/2026): ahí los síntomas
del desktop están vivos hoy, no son históricos. El fix tiene que valer igual para papel y para
electrónica, y las pruebas de runtime de las tres fases cubren los dos tipos.

### Qué rompe hoy

- **Listado del central** (`FacturaLegalSpecification:64`, `equal(activo, :activo)`): el filtro
  «Activos» deja afuera las 933.274 facturas en `NULL`, o sea casi toda la facturación electrónica.
  El filtro arranca en «Todos», así que solo se nota al elegir «Activos».
- **Inutilización** (`FacturaLegalRepository:90`, `f.activo = true`): el paso 7 de
  `SifenEventoService.inutilizar…` no encuentra las facturas del rango y el evento queda sin
  documentos vinculados. Corre después de enviar y guardar el evento y su fallo es no crítico.
- **Desktop, solo facturas en papel** (`list-factura-legal.component.ts`,
  `edit-factura-legal-dialog.component.ts`): `factura.activo ? … : …` las muestra INACTIVA y
  `factura.activo === true` deshabilita «Cancelar». Las electrónicas usan el estado del DE.

## Decisión: sin backfill

Decidido con Franco el 11/10/2026. Las filas existentes quedan en `NULL` y **`NULL` pasa a
significar «activa»** en todos los lectores; solo `false` significa cancelada. Motivo: son ~933.000
filas que viajarían por replicación lógica desde 18 filiales, para un dato que se puede leer bien
sin tocarlo. El central ya lo trata así en tres lugares (`FacturaLegalRepository:209`, `:243`,
`:244`, `NotaCreditoService`, `FacturaSimilarService`, `cancelarFacturaLegal`).

Consecuencia: desde el fix conviven `true` (nuevas) y `NULL` (viejas) como sinónimos, para siempre.
Todo lector nuevo de `activo` compara contra `false`, nunca contra `true`.

**`activo` solo no alcanza para saber si una electrónica está anulada.** Varios caminos cancelan el
documento electrónico sin tocar la factura (central: `SifenEventoService:190, 226, 741, 753`,
`SifenService:907`; y `cancelarFacturaLegal` con `cancelarVenta=true`), así que hay electrónicas
canceladas con `activo` en `NULL`. El central ya lo sabe: el resumen fiscal define
`anulada = activo IS FALSE OR de.estado = 'CANCELADO'` (`FacturaLegalRepository:243`). El listado
adopta esa misma definición en la fase 2.

## Fases

Tres repos, una fase por repo. No hay cambio de esquema, de `.graphqls` ni de contrato. El orden
de despliegue sí importa: ver «Orden y canales».

### Fase 1 — filial: el alta nace en `true`

- `FacturaLegalBuilder.build()`: antes del primer `facturaLegalService.save`, si
  `facturaLegal.getActivo() == null` → `setActivo(true)`. Un `false` explícito se respeta.
- `FacturaLegal.java`: `private Boolean activo = true;`. Cubre un camino futuro que no pase por el
  builder. No afecta la lectura: Hibernate pisa el campo con el valor de la fila, `NULL` incluido.
- Tests (`FacturaLegalBuilderTest`):
  - `facturaConActivoNull_naceActiva`: con `fl.setActivo(null)` **explícito** y la aserción sobre
    el argumento del primer `save`. Sin el `null` explícito el test pasaría por el default del
    entity aunque faltara la guarda del builder. Falla con el código de hoy.
  - `facturaConActivoFalse_seRespeta`.
- Sin migración. `factura_legal` es `BRANCH_TO_MAIN` y la forma de la fila no cambia.

### Fase 2 — central: los lectores estrictos y la guarda de cancelación

- `FacturaLegalSpecification`, con la definición de anulada del resumen fiscal (subconsulta
  `EXISTS` sobre `DocumentoElectronico` por `facturaLegal` y `estado = CANCELADO`):
  - `activo = true` → `activo IS NOT FALSE AND NOT EXISTS(DE cancelado)`;
  - `activo = false` → `activo IS FALSE OR EXISTS(DE cancelado)`.
  Sin la subconsulta, «Activos» pasaría a mostrar las electrónicas canceladas que quedaron en
  `NULL`, que hoy no aparecen solo por accidente.
- `FacturaLegalRepository.findByTimbradoDetalleIdAndSucursalIdAndNumeroFacturaBetween`:
  `f.activo = true` → `(f.activo IS NULL OR f.activo = true)`.
- **Guarda en `cancelarFacturaLegal`**: si la factura es electrónica y su DE existe con estado
  distinto de `APROBADO`, devuelve `ERROR: …` antes de `validarPlazoDeCancelacion` y de
  `cancelarDE`. Hoy no hay ningún corte por estado: `validarPlazoDeCancelacion` devuelve `null` si
  no hay `fechaRecepcionSifen`, y `cancelarDE` solo bloquea si ya hay una cancelación aprobada. Si
  SIFEN responde 0300 o 0600 sin protocolo, el evento queda PENDIENTE y el resolver sigue hasta
  cancelar la venta o marcar la factura inactiva sobre un documento que nunca se aprobó.
- Tests:
  - guarda: unitario junto a `CancelarFacturaLegalPlazoTest` (PENDIENTE, EN_LOTE y RECHAZADO →
    error sin llamar a `cancelarDE`; APROBADO sigue). **Corre en CI.**
  - filtro e inutilización: `FacturaLegalActivoQueriesIT` al estilo de `JornadaOrdenQueriesIT`
    (`true`, `NULL`, `false` y `NULL` con DE cancelado). **No corre en CI**: esos IT se piden a mano
    con una propiedad de sistema y una base real. Se corre contra la base local antes y después
    del cambio y la salida va en el PR.
- Sin migración, sin cambio en el `.graphqls`.

### Fase 3 — desktop: `NULL` es activa, y la electrónica no mira `activo`

- `list-factura-legal.component.ts`
  - `getEstadoDisplay` / `getEstadoClass` (papel): `factura.activo !== false`.
  - `calcularPuedeCancelarFactura`: electrónica → solo `documentoElectronico.estado === APROBADO`;
    papel → `factura.activo !== false`.
- `edit-factura-legal-dialog.component.ts:140`: `this.factura.activo !== false`.
- Tests: casos en `list-factura-legal.component.spec.ts` para `calcularPuedeCancelarFactura` y
  `getEstadoDisplay` (papel `NULL`/`true`/`false`; electrónica APROBADO/PENDIENTE con `activo`
  `true` y `NULL`). El CI no corre Karma: el gate real es `npm run check` más la prueba en browser.

**Por qué la electrónica deja de mirar `activo`**: hoy `factura.activo === true` habilita
«Cancelar» sin mirar el estado del DE. Con `NULL` no importaba (solo 26 electrónicas en `true`).
Después de la fase 1 **todas** las electrónicas nuevas nacen en `true`, así que un desktop sin esta
fase ofrecería «Cancelar» sobre facturas PENDIENTE, EN_LOTE o RECHAZADO. Es el efecto colateral del
fix y esta fase lo cierra.

## Datos nuevos

Ninguno. No nace columna, campo ni clave. Cambia el valor con que se escribe una columna existente:

| Dato | Escribe | Lee |
|---|---|---|
| `factura_legal.activo = true` en el alta | `FacturaLegalBuilder` (filial) | `FacturaLegalSpecification`, `FacturaLegalRepository`, `cancelarFacturaLegal`, `NotaCreditoService`, `FacturaSimilarService` (central); `list-factura-legal`, `edit-factura-legal-dialog` (desktop) |

mobile y mobile-pwa no leen facturas legales (`git grep facturaLegal` vacío en los dos).

## Orden y canales

El orden es **por canal**, y tiene una dependencia real: **el central con la guarda tiene que estar
desplegado en un canal antes de que el filial llegue a ese canal.** El desktop no sirve de barrera
porque el usuario puede postergar el update; la guarda del central sí.

1. Central: merge a `develop` y `gh workflow run Deploy` del canal (el merge solo no despliega).
2. Desktop: mismo canal.
3. Filial, al final: el merge a `develop` llega solo a las filiales alpha en ≤15 min;
   `release/beta` a las 6 de farmacia; `master` a las 18 de bodega. En cada promoción se repite el
   orden: central del canal primero.

`factura_legal` es `BRANCH_TO_MAIN` y no cambia de forma, así que la replicación no impone orden.
Sin variables de entorno ni carpetas nuevas.

## Rollback

- Filial: un JAR anterior vuelve a escribir `NULL`. Es inofensivo: `NULL` ya significa activa. Las
  filas escritas en `true` quedan en `true`.
- Central y desktop: revertir devuelve los síntomas de hoy, sin tocar datos.
- No hay `UPDATE` ni `DELETE` sobre producción en ninguna fase.

## Auditoría del plan (paso 5, 11/10/2026)

Dos auditores, sin verse. Ningún bloqueante.

| Eje | Hallazgo | Qué se hizo |
|---|---|---|
| A | El central no corta la cancelación de una electrónica no aprobada; con 0300/0600 deja venta cancelada o factura inactiva | Guarda en `cancelarFacturaLegal`, fase 2, con test en CI |
| A | El orden tiene que ser por canal y el desktop no es garantía | Sección «Orden y canales» reescrita |
| A | No hay más lectores estrictos de `factura_legal.activo` en los tres repos, ni `.jrxml`; mobile y PWA no leen facturas | Confirma el alcance |
| B | «Activos» pasaría a incluir electrónicas canceladas con `activo` en `NULL` | El filtro usa la definición de anulada del resumen fiscal |
| B | El test del builder no fallaba con el código de hoy por el default del entity | Test con `setActivo(null)` explícito |
| B | El IT de queries del central no corre en CI | Declarado; corrida manual con evidencia en el PR |
| B | El fix del filial alcanza: `build()` es el único alta y ningún `save` posterior pisa el valor | Confirma la fase 1 |

## Sin verificar

1. **Cuántas electrónicas canceladas tienen `activo` en `NULL`** en bodega. No cambia el diseño
   (el filtro ya las contempla); dimensiona cuánto se habría roto sin la subconsulta.
2. **Farmacia**: no se midió nada. Mismo conteo, base `farmacia`.
3. **La batería del filial con el default en el entity** (que Hibernate pise el default al hidratar
   una fila en `NULL` está inferido, no probado): se ve al correr `./mvnw clean verify -B` y al
   leer una factura vieja en la prueba de runtime de la fase 1.
4. **Costo de la subconsulta `EXISTS`** en el listado paginado contra ~2,3 M de facturas: se mide
   con `EXPLAIN ANALYZE` en la base local en la fase 2.
5. **Qué commit quitó el `true`** en 2025. No cambia el fix.
