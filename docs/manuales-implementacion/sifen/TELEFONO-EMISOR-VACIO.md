# SIFEN: `dTelEmi` vacío cuando el `timbrado_detalle` no tiene teléfono (issue #139)

Estado: **plan aprobado por Franco el 11/10/2026; en implementación**. Rama `fix/sifen-telefono-emisor-vacio`, desde `develop`
(`789d569`). Solo filial.

## El problema

`SifenService.construirDatosEmisor` (`SifenService.java:1701`) copia el teléfono del emisor tal
cual desde el detalle del timbrado:

```java
gEmis.setdTelEmi(factura.getTimbradoDetalle().getTelefono());
```

`TgEmis.setupSOAPElements` de jsifenlib escribe el elemento **siempre**
(`gEmis.addChildElement("dTelEmi").setTextContent(this.dTelEmi)`): con `null`, `""` o espacios sale
`<dTelEmi/>` y SIFEN rechaza el lote con «0160 XML malformado: [El valor del elemento: dTelEmi es
invalido]». No hay forma de omitirlo desde el filial.

Pasó en producción el 23/09/2026 con una nota de remisión del **central** (depósito 13: detalle sin
teléfono, cabecera con teléfono). El central lo corrigió en el PR #330 con
`SifenTimbradoHelper.telefonoEmisor(detalle)`: detalle → cabecera `timbrado` → `null`. El filial
repite el patrón sin respaldo. No se vio fallar todavía.

## Decisiones

1. **Respaldo detalle → cabecera**, igual que el central. `Timbrado.telefono` ya está mapeado en el
   filial y la columna existe (`V35`, tipo alineado en `V79.3`). **Sin migración.**
2. **Sin teléfono en ningún lado: cortar con error claro** (Franco, 11/10/2026).
3. **El corte va en `FacturaLegalBuilder.build()`, antes del primer write, como
   `GraphQLException`. No va en `SifenService`.** Motivo:
   - `build()` es `@Transactional(noRollbackFor = GraphQLException.class)` y participa en la
     transacción de `VentaGraphQL#saveVenta`. `crearDocumentoElectronico` es `@Transactional`
     común y se une a esa misma transacción.
   - Una excepción que sale de `crearDocumentoElectronico` marca la transacción **rollback-only**
     aunque el builder la ataje (`FacturaLegalBuilder.java:173`): el commit termina en
     `UnexpectedRollbackException` y **se pierde la venta ya cobrada**. Es el mecanismo que
     documenta el javadoc de `build()` (commit `d4a1e52`).
   - Una `GraphQLException` lanzada en el bloque de validaciones de `build()` no escribe nada, no
     consume número de timbrado y no marca la transacción. `PoliticaFacturacionService
     .fallaAntesDeEscribir` la reconoce y devuelve el turno del contador.
4. **`SifenService:1701` usa el helper y no lanza.** Si el helper devuelve `null` ahí (solo
   alcanzable por `reconstruirDEDesdeFactura` de un DE viejo sin XML), el comportamiento es el de
   hoy: SIFEN rechaza. Agregar un `throw` en ese punto sería meter una excepción nueva dentro de
   la transacción de la venta.

### Qué ve el usuario cuando falta el teléfono en los dos lados

| Vía | Hoy | Con el fix |
|---|---|---|
| Factura silenciosa del PDV (`VentaGraphQL:340`) | venta OK, factura con número, SIFEN la rechaza (0160) | venta OK **sin factura**, número no consumido, el turno vuelve, error en el log |
| «Venta + Ticket» (`FACTURA_E_IMPRESION`, `VentaGraphQL:293`) | ídem, y sale el ticket-factura | venta OK **sin factura y sin ticket, sin mensaje al cajero**; el turno no vuelve. Ver «Venta + Ticket» abajo |
| Delivery (`DeliveryGraphQL:245`) | ídem | el delivery ya pasó a `PARA_ENTREGA` (`:231`, sin transacción) y después sale «Problema al generar factura electrónica: Problema al crear factura legal»; sin factura. Preexistente para cualquier falla de validación |
| Carga manual desde el desktop (`saveFacturaLegal`) | ídem | no se guarda la factura ni se consume número (la Persona/Cliente que `saveFacturaLegal` crea antes de `build()` sí queda, como hoy). El filial devuelve el mensaje, pero **el desktop lo pisa** con «No se pudo confirmar la factura. Verifique en la lista…» (`add-factura-legal-dialog.component.ts:954`); no reintenta |
| API externa (`FacturaLegalApiService`, la usa el central) | ídem | `verificarDisponibilidadTimbradoDetalle` responde «no disponible» con el motivo y `crearFacturaLegal` devuelve **400** antes de escribir (fase 3). El corte del builder queda de respaldo. El central lo propaga sin reintentar; su desktop lo muestra como «Error desconocido» (preexistente) |

Con teléfono en la cabecera (el caso real del 23/09) no cambia nada visible: la factura sale con
el teléfono de la cabecera.

**El fallo pasa de visible a invisible.** Hoy una factura sin teléfono deja un DE rechazado
consultable en la base; con el corte no queda ninguna fila, solo un `log.error` en la filial. Por
eso la consulta de producción es **precondición de promoción** (ver «Despliegue») y el mensaje del
corte lleva un texto fijo y grepeable.

### «Venta + Ticket»: decisión pendiente de Franco

`FacturaService.crearFacturaLegalDesdeVenta` (`:397-405`) se traga cualquier excepción de `build()`
y devuelve `null`; `VentaGraphQL:293-296` le pasa ese `null` a `printTicket58mmFactura`, que da
`NullPointerException` (`FacturaLegalGraphQL:916`). El catch externo (`:354`) devuelve la venta. La
venta no se pierde, pero no se imprime nada y, como la causa raíz es el NPE y no una
`GraphQLException`, el turno no vuelve. **Es preexistente** (pasa hoy con timbrado vencido o rango
agotado); este fix agrega un caso más que cae ahí.

Opciones: (a) dejarlo fuera de alcance, con issue aparte, apoyado en la precondición de promoción
(cero timbrados sin teléfono en los dos lados); (b) agregar en esta rama el chequeo de `null` que
ya tiene delivery, para que el turno vuelva; (c) además caer al ticket simple.

**Decidido (Franco, 11/10/2026): (a).** Tocar esa ruta cambia el comportamiento de todas las
fallas de validación, no solo de esta. Queda como issue aparte.

## Fases

### Fase 1 — helper y uso en el DE

- `service/sifen/util/SifenTimbradoHelper.java` (nuevo): solo `telefonoEmisor(TimbradoDetalle)` y
  su `recortado(String)`, copiados del central. Los otros métodos del helper del central
  (`codigoEstablecimiento`, `fechaFirmaSegura`) son de las notas y no se portan.
- `SifenService.java:1701` → `gEmis.setdTelEmi(SifenTimbradoHelper.telefonoEmisor(factura.getTimbradoDetalle()))`.
  `construirDatosEmisor` pasa de `private` a package-private para poder testearlo.
- Tests:
  - `SifenTimbradoHelperTest` (nuevo), los 4 casos del central: el detalle manda y se recorta;
    detalle vacío o en blanco usa la cabecera; sin teléfono en ningún lado es `null`, nunca `""`;
    sin detalle o sin cabecera es `null`.
  - `SifenEmisorTelefonoTest` (nuevo): `new SifenService(null × 9)` + `construirDatosEmisor` con
    detalle sin teléfono y cabecera con teléfono → `getdTelEmi()` es el de la cabecera. **Falla
    con el código actual** (devuelve `null`).

### Fase 2 — corte antes de escribir

- `FacturaLegalBuilder.build()`, al final del bloque de validaciones (después de
  `!sifenHabilitado && timbradoElectronico`, antes de `setTimbradoDetalle`):

  ```java
  if (sifenHabilitado && timbradoElectronico
          && SifenTimbradoHelper.telefonoEmisor(timbradoDetalle) == null) {
      throw new GraphQLException("TIMBRADO_SIN_TELEFONO: el timbrado " + numero + " (detalle " + id
              + ") no tiene telefono cargado ni en el detalle ni en la cabecera: SIFEN rechazaria la factura");
  }
  ```

  **Sin `request.isGenerarDE()`**: `FacturaLegalApiService:283` arma el `BuildRequest` con
  `generarDE=false` y crea el DE por su cuenta (`:308`) bajo la misma condición
  (`esElectronica && isSifenEnabled()`). Con `isGenerarDE()` en la condición, la vía API quedaba
  sin corte.

- Tests en `FacturaLegalBuilderTest`:
  - vía API: `BuildRequest` con el `TimbradoDetalle` pre-resuelto y `generarDE=false`, electrónico
    sin teléfono → `GraphQLException`. **Falla con el código actual.**
  - electrónico sin teléfono en ningún lado → `GraphQLException`, y `verify(never())` sobre
    `facturaLegalService.save`, `timbradoDetalleService.save` y
    `sifenService.crearDocumentoElectronico`. **Falla con el código actual.**
  - electrónico con teléfono solo en la cabecera → se emite.
  - papel sin teléfono → se emite (la validación no aplica).
  - Los tests electrónicos existentes (`timbradoElectronico_llamaSifen_actualizaCDC`,
    `sifenFalla_facturaPersisteSinCDC`, `facturaElectronicaConActivoNull_naceActiva`) arman el
    timbrado sin teléfono: hay que cargárselo en el fixture.

### Fase 3 — ajustes de la auditoría del diff

- `FacturaLegalApiService.verificarDisponibilidadTimbradoDetalle`: mismo chequeo, antes del rango.
  Sin esto la vía API respondía 500 «Error inesperado» con stacktrace (`FacturaLegalController:88`
  solo trata `IllegalStateException` como 400). Test `FacturaLegalApiServiceDisponibilidadTest`,
  visto fallar sin el cambio.
- El mensaje del corte vive en `SifenTimbradoHelper.mensajeSinTelefono` (un solo texto para el
  builder y la API). El prefijo `TIMBRADO_SIN_TELEFONO` no tiene lector programático: es para el log.
- El test de la vía API del builder afirma también el prefijo.

### Verificación

- `./mvnw clean verify -B` (lo mismo que corre el CI), leído del log.
- Runtime en local (filial `:8082`, perfil `dev`, base local). ⚠️ `application-dev.properties`
  trae `sifen.ambiente=PROD`, `sifen.enabled=true` y `sifen.scheduler.enabled=true`: **antes de
  levantar, confirmar con `frc-fullstack` qué impide que un DE local llegue a SIFEN real y correr
  con `sifen.scheduler.enabled=false`**. Si no se puede asegurar, este paso no se corre y se anota.
  1. Detalle sin teléfono, cabecera con teléfono: emitir una factura y leer `<dTelEmi>` en
     `documento_electronico.xml_original`.
  2. Sin teléfono en ninguno de los dos: factura silenciosa (la venta se guarda sin factura,
     `numero_actual` no avanza), «Venta + Ticket», carga manual y vía API. En ninguna debe aparecer
     «Transaction silently rolled back».
  3. Restaurar los teléfonos y borrar los DE de prueba antes de reactivar el scheduler.

## Datos nuevos

Ninguno: no hay columna, campo GraphQL, enum ni property nuevos. `financiero.timbrado.telefono`
ya existe; lo escribe el central (ABM de timbrados) y llega por replicación; pasa a leerlo
`SifenTimbradoHelper`.

## Alcance y despliegue

- Un solo repo. No cambia el contrato GraphQL: desktop, mobile y mobile-pwa no se tocan.
- `develop` → filiales alpha; `release/beta` → 6 de farmacia; `master` → 18 de bodega, por
  `check-update` cada 15 min y sin gate humano después del merge.
- Rollback: es código puro; la versión anterior corre contra el mismo esquema.
- **Precondición de promoción a `release/beta` y a `master`**: correr la consulta de «Sin
  verificar» en farmacia y en bodega respectivamente. Cualquier timbrado electrónico activo sin
  teléfono en el detalle **y** en la cabecera se carga antes de promover: con el corte, esa
  sucursal dejaría de facturar en silencio (todas las ventas en modo `TODAS`).
  El filial no escribe `timbrado.telefono`: depende entero de la réplica, y la consulta corre en el
  central. Confirmar además con `replica-monitor` que las suscripciones del canal están al día (o
  correr la consulta en cada filial).

## Fuera de alcance

- **KuDE de factura del central** (`FacturaLegalGraphQL.java:1760` del central): su respaldo solo
  mira `null`, no cadena vacía, y no usa el helper. Con un detalle con `""` el XML llevaría el
  teléfono de la cabecera y el KuDE imprimiría «Telefono: » vacío. Va como issue del central. El
  filial no arma KuDE.
- **`dEst` sin configurar** (`SifenService.java:1578`): lanza `IllegalArgumentException` dentro de
  `crearDocumentoElectronico`, o sea el mismo mecanismo que pierde la venta. Preexistente; se
  reporta aparte.
- **Factura electrónica que queda sin DE**: los dos llamadores de `crearDocumentoElectronico`
  (`FacturaLegalBuilder:173` y `FacturaLegalApiService:308`) atajan el error y nadie reintenta.
  Preexistente.
- **Teléfonos no vacíos pero inválidos** (`"0"`, `"-"`, menos de 6 caracteres): el helper solo
  recorta, igual que el central. Un detalle con basura le gana a una cabecera válida y SIFEN
  rechaza como hoy. El corte cubre solo el caso vacío; la consulta de promoción los lista para
  corregir el dato.
- DEs ya rechazados por 0160 con el XML guardado: se reenvían desde `xml_original`, este fix no
  los regenera. No se conoce ninguno en filiales.

## Sin verificar

- **Medido en el central de bodega (11/10/2026, solo lectura):** 27 detalles electrónicos, 20
  activos. Dos activos sin teléfono en el detalle, los dos con teléfono en la cabecera
  (timbrado 6): detalle 118 (sucursal 13, punto 002) y detalle 116 (sucursal 25, punto 001). El
  respaldo los cubre; **ningún detalle de bodega cae en el corte**. Farmacia no se midió (factura
  en papel): la consulta sigue siendo precondición antes de `release/beta`.
- Farmacia y los teléfonos no vacíos pero inválidos. Consulta de promoción (lista también los
  de menos de 6 caracteres; mirar además `tel_cab`):

  ```sql
  select td.id, td.sucursal_id, td.activo, td.telefono, t.telefono tel_cab
  from financiero.timbrado_detalle td join financiero.timbrado t on t.id = td.timbrado_id
  where t.is_electronico and length(btrim(coalesce(td.telefono, ''))) < 6;
  ```

- Que `financiero.timbrado.telefono` esté efectivamente replicado en las 24 filiales (la issue lo
  afirma; no se leyó ninguna base de producción).
- El largo que exige SIFEN para `dTelEmi` (6 a 15 según el manual técnico, de memoria): el jar no
  trae el XSD. No se valida el largo, igual que el central.

## Auditoría del plan (paso 5, 11/10/2026)

Dos auditores, por eje y sin verse. Los dos hallazgos altos coincidieron y se verificaron en el
código.

| # | Hallazgo | Eje | Qué se hizo |
|---|---|---|---|
| 1 | La vía API arma el `BuildRequest` con `generarDE=false`: el corte no la cubría | A y B | Se quitó `isGenerarDE()` de la condición; test de la vía API; tabla corregida |
| 2 | «Venta + Ticket» queda sin factura, sin ticket y sin mensaje (NPE preexistente tras el `null` de `FacturaService`) | A y B | Declarado en la tabla; **decisión pendiente de Franco** |
| 3 | La verificación de runtime podía emitir contra SIFEN real (`sifen.ambiente=PROD` en el perfil `dev`) | B | Scheduler apagado y confirmación previa; si no se puede asegurar, no se corre |
| 4 | El fallo pasa de DE rechazado (visible) a una línea de log | A y B | Consulta de producción como precondición de promoción; prefijo `TIMBRADO_SIN_TELEFONO` |
| 5 | Teléfonos basura no vacíos siguen rechazándose | A y B | Declarado fuera de alcance; la consulta los lista |
| 6 | «Nada guardado» era inexacto en la carga manual (Persona/Cliente se crean antes) | B | Texto corregido |

Confirmado por los auditores: el mecanismo rollback-only (`SifenService:191`, REQUIRED vía proxy);
el corte queda antes de `save` (`:142`) y de `numero_actual` (`:156`); jsifenlib `frc.13` escribe
`dTelEmi` sin rama de null (`javap`); `TimbradoDetalle.timbrado` es `EAGER`; se rompen exactamente
los 3 tests electrónicos existentes del builder; `new SifenService(null × 9)` es viable con
`timbrado.ruc` y `codigoCiudad` numérico en el fixture.

## Auditoría del diff (paso 8, 11/10/2026)

Tres ejes fijos; ningún condicional (el diff no toca migraciones, replicación ni release).

| Eje | Resultado |
|---|---|
| Fijo 1 — autorización y fuga | N/A: sin resolvers, schema, DTOs ni seguridad tocados. Hallazgos de corrección: 500 en la vía API y test flojo → fase 3 |
| Fijo 2 — esquema y espejo | Sin hallazgos altos ni medios. Columna, mapeo, `EAGER` y publicación `MAIN_TO_ALL` verificados. Riesgo bajo: filial con la réplica atrasada → sumado a la precondición de promoción |
| Fijo 3 — contrato con clientes | Sin ruptura de contrato ni orden de despliegue; ningún cliente reintenta. Tabla «Qué ve el usuario» corregida (delivery, carga manual, API) |

Queda para el desktop, fuera de esta rama: el diálogo de carga manual pisa el mensaje del backend
con un aviso genérico, y el error de la vía central se muestra como «Error desconocido».

## Registro de implementación

- 11/10/2026: fases 1, 2 y 3 implementadas y pusheadas. `./mvnw clean verify -B`: 390 tests, verde.
  Los tests nuevos se vieron fallar contra el código sin el fix en las tres fases.
- **Pendiente:** prueba de runtime en local, prueba de Franco, PR.

