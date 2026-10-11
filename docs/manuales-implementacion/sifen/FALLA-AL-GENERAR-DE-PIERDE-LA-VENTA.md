# SIFEN: una falla al generar el DE pierde la venta (issue #170)

Estado: **implementado y pusheado el 11/10/2026; decisión de negocio abierta (ver «Decisión abierta») y pendiente de prueba de Franco y de PR**. Rama
`fix/sifen-falla-de-pierde-venta`, desde `develop` (`b6137fe`, incluye el #168). Solo filial.
**Precondición de merge: el PR #171 (#169) tiene que estar mergeado antes** en `develop` (el
orden se hereda a beta y master): sin él, en «Venta + Ticket» el corte deja la venta sin papel,
sin aviso y sin devolver el turno. Las dos ramas no tienen conflictos.

## El problema

`SifenService.crearDocumentoElectronico` (`SifenService.java:192`) es `@Transactional` común y se
une a la transacción de quien lo llama: `FacturaLegalBuilder.build()` (dentro de
`VentaGraphQL#saveVenta`) o `FacturaLegalApiService.crearFacturaLegal`. Los dos llamadores atajan
la excepción «para no romper el guardado», pero cuando una `RuntimeException` sale de ese método
el interceptor ya marcó la transacción compartida como **rollback-only**: el commit termina en
`UnexpectedRollbackException` y se pierde todo. En la API, la factura; en el PDV, **la venta ya
cobrada**.

### Qué lo dispara

| Dónde (`SifenService`) | Causa | ¿Rutinario? |
|---|---|---|
| `:2033` | ítem con precio unitario 0 (regalo, bonificación) | plausible en una venta normal |
| `:1972`, `:2003` | cantidad 0, o que redondea a 0 (pesable sin `balanza`) | plausible |
| `:2066` | descuento global por unidad mayor al precio del ítem | plausible |
| `:2016` | precio unitario `null` | dato roto |
| `:1579` | sucursal sin `codigoEstablecimientoFactura` (`dEst`) | configuración |
| `:1560`, `:1708`, `:2169` | `parseInt` de número de timbrado, código de ciudad, tipo de sociedad | configuración |
| `SifenReceptorHelper:244` | factura innominada con total ≥ 7.000.000 | **rutinario** |
| `SifenReceptorHelper:147`, `:207` | DV de RUC inválido; no contribuyente sin nombre | dato del cliente |
| `:1153` | `totalFinal` nulo | orden de armado |
| `:204` | factura sin ítems (la carga manual lo permite) | dato |
| `:~231` (`generarXml`) | firma o certificado; **y también datos**: moneda extranjera sin tipo de cambio (NPE), ítems vacíos (`/ by zero`) | entorno y datos |

### Reproducido (11/10/2026, local, `develop` `b6137fe`)

`POST /api/facturas-legales` con dos ítems, uno con `precioUnitario: 0` → `HTTP 500
{"mensaje":"Transaction silently rolled back because it has been marked as rollback-only"}`. Ni
factura ni DE guardados. Un auditor lo confirmó además con el `TransactionInterceptor` real de
spring-tx 5.1.16. No se reprodujo por `saveVenta` (hace falta sesión); es el mismo interceptor.

## Por qué no la red de seguridad (opción A, descartada)

`noRollbackFor` en `crearDocumentoElectronico` salvaría la venta, pero dejaría una factura
**numerada y sin DE**: la impresión revienta sin DE (`FacturaLegalGraphQL:1447`, `:1835`), delivery
consumiría un número por reintento, desktop y central la mostrarían como papel (deciden por el
CDC), y el número queda consumido sin que SIFEN lo vea. No hay ningún reintento del DE.

## Diseño (opción B): ensayar el DE antes de escribir

El armado del DE (`generarDEDesdeFacturaDatosReales`) **no usa ningún servicio ni estado
persistido**: solo los campos de la factura, la lista de ítems y el timbrado. Se puede ejecutar en
memoria antes del primer write.

1. **`SifenService.validarGeneracionDE(FacturaLegal, List<FacturaLegalItem>)`**, público y **sin
   `@Transactional`**: ejecuta `generarDEDesdeFacturaDatosReales` y `obtenerCDC()`; no persiste ni
   firma. Si algo lanza, lo deja salir. Sin anotación, la excepción cruza el proxy sin marcar la
   transacción. Dos guardas propias para lo que el armado no ve y hoy revienta después, en
   `crearDocumentoElectronico` o en `generarXml`: **lista de ítems vacía** y **moneda extranjera
   sin tipo de cambio**.
2. **`FacturaLegalBuilder.build()` se reordena** para tener todo en memoria antes del primer
   `save`:
   - arma los `FacturaLegalItem` (`construirItem`, que solo lee) y calcula los parciales
     (`ParcialesCalculator`, puro) sobre la factura todavía sin guardar;
   - si `sifenHabilitado && timbradoElectronico` (la misma condición que el corte del teléfono;
     cubre PDV, carga manual y API): llama a `validarGeneracionDE`. Cualquier `Exception` →
     `GraphQLException("FACTURA_NO_EMITIBLE: <motivo>")`, **sin `cause`** (el stack se loguea
     aparte): `fallaAntesDeEscribir` mira la causa raíz y `saveFacturaLegal` re-envuelve
     conservándola; con causa encadenada el turno de la factura silenciosa no volvería;
   - recién después: `save` de la cabecera, `save` de cada ítem (reasignándole la cabecera
     guardada), `numero_actual`, y el segundo `save` de la cabecera, como hoy.
3. **Se corta con cualquier excepción del ensayo, no solo con las de validación.** El ensayo es
   la misma función que el armado real y con las mismas entradas (cambian solo los `id` y
   `creadoEn`, que el armado no usa para decidir): si el ensayo lanza, el armado real también
   lanzaría, y eso hoy pierde la venta.
4. **API**: `FacturaLegalController` trata la `GraphQLException` como 400 (hoy cae en el 500
   genérico), así el motivo llega limpio al central.
5. **Lo que falla después de escribir sigue como hoy**: se pierde la operación con error visible
   y no quedan facturas huérfanas. `crearDocumentoElectronico` no se toca. El ensayo **no cubre
   `generarXml`** (firmar cuesta ~33 ms contra 0,25 ms del armado): ahí quedan los fallos de firma
   y certificado, y cualquier dato que solo reviente al calcular totales en la librería. Los dos
   casos de datos conocidos van como guardas (punto 1); en una sonda con 25 variantes (crédito,
   USD, RUC, CI, pesable, delivery, descuento, nulos del emisor) ensayo y XML coincidieron.
7. **Interruptor `sifen.ensayo-de.enabled`**, `true` por defecto en código y declarado en
   `application.properties`; no hace falta crear nada en ninguna filial. Solo `false` lo apaga: se
   lee como texto, así que un valor mal escrito no impide arrancar (deja el ensayo encendido). En
   `false` el builder no ensaya y todo queda como antes de este fix. **Para apagarlo en una
   filial**: agregar la línea `sifen.ensayo-de.enabled=false` en
   `/opt/frc-filial/application.properties` y reiniciar `frc.service`. No se probó por ese archivo
   (se probó como argumento de arranque); la variable de entorno equivalente no se verificó.
6. La validación de `dEst` y el corte del teléfono quedan cubiertos por el ensayo; el corte
   explícito del teléfono (#168) se mantiene, con su mensaje propio.

### Qué ve el usuario (ítem a precio 0, `dEst` vacío, etc.)

| Vía | Hoy | Con el fix |
|---|---|---|
| Factura silenciosa del PDV | «Ups! Algo salió mal: Transaction silently rolled back»; la venta no se guarda y **el carrito queda intacto para corregir y reintentar** | «Venta guardada con éxito»; venta guardada sin factura, número no consumido, el turno vuelve, `FACTURA_NO_EMITIBLE` solo en el log |
| «Venta + Ticket» | ídem | venta guardada; con #171, sale el ticket simple y el turno vuelve; sin #171, sin papel, sin aviso y el turno no vuelve |
| Delivery | «Delivery no encontrado»; el delivery ya quedó `PARA_ENTREGA` (se guarda antes, sin transacción) | igual mensaje y estado; sin factura ni comprobante; el turno vuelve. **Un delivery con ítem a precio 0 queda sin comprobante** |
| Carga manual | error genérico; no se guarda la factura | el motivo sale unos segundos y detrás el desktop encola «No se pudo confirmar la factura. Verifique en la lista…» (desktop #470); no se guarda la factura (persona y cliente se crean antes, como hoy) |
| API (desde el central) | 500 «Transaction silently rolled back» | 400 con el motivo |

Una factura normal no cambia: mismo DE, mismo XML, un armado en memoria más por factura.

## Decisión abierta (Franco): guardar sin factura, o rechazar con mensaje

Lo encontró la auditoría del diff, mirando el desktop. Hoy estos casos **rechazan la venta**: sale
«Ups! Algo salió mal», el carrito queda intacto (`venta-touch.component.ts:1693-1717`) y el cajero
corrige y reintenta. En una venta sin nombre de 7.000.000 o más, corregir es cargar el cliente, y
la factura sale. Con el fix tal como está, esa venta **se guarda sin factura** y el desktop
muestra «Venta guardada con éxito» (`:1575-1580`), sin ninguna señal. El desktop no valida ni el
tope de 7.000.000 ni el precio mayor a cero: el error críptico de hoy es el único freno.

- **Guardar sin factura** (lo implementado): sirve cuando el cajero no puede corregir (ítem de
  regalo a precio 0; sucursal sin código de establecimiento, donde hoy no cierra ninguna venta
  con factura). Costo: ventas sin facturar que nadie ve en el momento.
- **Rechazar la venta con el motivo**, antes de escribir: lo de hoy, pero legible. El cajero
  corrige y reintenta. Costo: una venta con un regalo a precio 0 no se puede cerrar con factura.
- **Por caso**: rechazar con mensaje lo que el cajero puede corregir (el tope de 7.000.000) y
  guardar sin factura lo que no.

Hasta que se decida, **esta rama no se mergea**.

## Fases

### Fase 1 — el ensayo en `SifenService`

- `validarGeneracionDE`, con javadoc que diga por qué no es transaccional, y las dos guardas.
- `SifenValidarGeneracionDETest`, con el `SifenService` real (`new SifenService(null × 8, new
  SifenConfig())`: habilitado, sin certificado ni red) y un fixture de factura electrónica válida.
  Campos obligatorios medidos en la sonda: factura `totalFinal`, `fecha`, `numeroFactura`; detalle
  `sucursal.codigoEstablecimientoFactura`, `puntoExpedicion`, `codigoCiudad` numérico; timbrado
  `numero` numérico, `ruc`, `fechaInicio`; ítem `cantidad` y `precioUnitario` > 0.
  - **No lanza**: válida sin `id` ni `creadoEn`; a crédito; USD con tipo de cambio; cliente con
    RUC; ítem pesable; delivery como ítem; con descuento global.
  - **Lanza**: precio 0; cantidad 0; sucursal sin código; innominada ≥ 7.000.000; `totalFinal`
    nulo; ítems vacíos; USD sin tipo de cambio.

### Fase 2 — el builder ensaya antes de escribir

- Reorden de `build()` según el diseño. El armado solo lee `totalFinal` entre los totales:
  `ensayarDE` se lo **presta** a la cabecera mientras corre el ensayo y lo restaura en un
  `finally` (sin eso el armado lanza y todo «Venta + Ticket» y delivery dejarían de facturar).
  **Los writes y su orden quedan idénticos a `develop`**: INSERT de la cabecera sin parciales →
  ítems → `numero_actual` → UPDATE con parciales. (El plan original preveía mandar los parciales
  en el INSERT; se descartó para no cambiar nada del flujo que se replica al central.)
- Interruptor `sifen.ensayo-de.enabled`.
- Tests en `FacturaLegalBuilderTest` (mock de `SifenService`):
  - el ensayo lanza → `GraphQLException` con prefijo `FACTURA_NO_EMITIBLE` y **sin causa**
    (`PoliticaFacturacionService.fallaAntesDeEscribir(ex)` y la versión re-envuelta dan `true`);
    `never()` sobre todos los `save` y sobre `crearDocumentoElectronico`; `numero_actual` intacto.
    **Falla con el código actual.**
  - el ensayo se llama antes del primer `save` (`InOrder`) y recibe la cabecera con `totalFinal`
    calculado y los ítems armados.
  - vía API (`generarDE=false`, detalle pre-resuelto) → ensaya. Papel, SIFEN deshabilitado o
    interruptor en `false` → no ensaya.
- `FacturaLegalBuilderConSifenRealTest` — el que detecta un falso positivo: builder + `SifenService`
  real habilitado (`new SifenConfig()`), con la factura **con la forma de cada llamador**:
  - `FacturaService` (sin `totalFinal`, cliente `null`, con y sin descuento), carga manual y API
    (con el `totalFinal` del request): factura válida → **no corta** y llega a los `save`.
  - ítem a precio 0 → corta con `FACTURA_NO_EMITIBLE`, sin escribir.

### Fase 3 — API

- `FacturaLegalController`: `catch (GraphQLException)` → 400. El central no reintenta; solo cambia
  el texto que propaga.

### Verificación

- `./mvnw clean verify -B`, leído del log.
- Runtime en local (`:8082`, perfil `dev`, `--sifen.scheduler.enabled=false`):
  1. El `POST` del ítem a precio 0: **400** con `FACTURA_NO_EMITIBLE`, sin factura ni DE,
     `numero_actual` sin avanzar.
  2. Una factura normal: 201, DE `PENDIENTE`, y el `xml_original` con los mismos datos que antes
     (comparar contra una emitida con `develop`, salvo CDC/fecha/firma).
  3. El mismo `POST` con `sifen.ensayo-de.enabled=false`: vuelve el 500 de hoy (el interruptor
     apaga de verdad).
  4. Borrar la factura y el DE de prueba y restaurar `numero_actual`.
- Por `saveVenta` desde el desktop: una venta normal que **siga facturando**, y una con un ítem a
  precio 0. Prueba de Franco.

## Datos nuevos

| Dato | Quién lo escribe | Quién lo lee |
|---|---|---|
| property `sifen.ensayo-de.enabled` (default `true` en código) | quien opere la filial, solo para apagar el ensayo | `FacturaLegalBuilder` |

Sin columnas, campos GraphQL ni enums.

## Alcance y despliegue

- Un solo repo, sin migración ni cambio de contrato GraphQL. La API REST cambia un 500 por un 400
  en un caso que hoy ya falla.
- `develop` → filiales alpha; `release/beta` → 6 de farmacia; `master` → 18 de bodega, en ≤15 min
  y sin gate humano.
- Rollback: código puro, sin estado nuevo (el fix deja de escribir en un caso; no escribe nada
  nuevo).
- **Riesgo principal: es el camino de todas las facturas.** Un falso positivo del ensayo dejaría
  de facturar en silencio (la venta se guarda sin factura). Mitigaciones: misma función que el
  armado real; test con entidades sin `id`; test del builder con el `SifenService` real; prueba de
  runtime de una factura normal; y `FACTURA_NO_EMITIBLE` como texto fijo para medir en el log de
  alpha antes de promover.
- **Antes de promover**: contar `FACTURA_NO_EMITIBLE` en los logs del canal **y** confirmar que
  después del deploy se siguieron emitiendo facturas con DE (si alpha no tiene volumen
  electrónico, el conteo en cero no prueba nada). Observar `release/beta` antes de `master`.

## Fuera de alcance

- Fallas posteriores al primer write (firma, certificado, base): siguen perdiendo la operación.
- Listar o alertar las ventas que quedaron sin factura por `FACTURA_NO_EMITIBLE`.
- Permitir facturar ítems a precio 0 (bonificaciones): es una regla de negocio y de SIFEN aparte.

## Sin verificar

- Con qué frecuencia pasa hoy en producción (no se leyeron logs de filiales).
- Que `construirItem` y el armado del DE no dependan de que la cabecera esté guardada (leído: no;
  lo confirma el test de entidades sin `id`).
- Que el camino por `saveVenta` se comporte como la API después del fix.

## Historial

Primera versión del plan (red de seguridad + corte de `dEst`), auditada el 11/10/2026: el
mecanismo era correcto, pero los efectos aguas abajo de una factura sin DE (impresión, delivery,
desktop, central, número consumido) llevaron a descartarla. Detalle en «Por qué no la red de
seguridad».

## Auditoría del plan v2 (paso 5, 11/10/2026)

Dos auditores, por eje y sin verse; el del eje B corrió una sonda con el `SifenService` real.

- **Confirmado**: el armado y `obtenerCDC()` corren sobre entidades transitorias, sin red,
  certificado ni configuración global; cuestan 0,25 ms (N=1000, 10 ítems). En las tres vías la
  factura ya trae fecha, cliente, moneda, descuento y crédito antes de `build()`, y entre `build()`
  y la creación del DE nada la muta. Las únicas diferencias ensayo/real son `id` y `creadoEn`.
  `save` devuelve la misma instancia (no ejecutado). El central no reintenta ante 400 ni 500.
- **Aplicado al plan**: `totalFinal` antes del ensayo, fijado con tests de servicio real por
  llamador (los dos); excepción sin causa para que el turno vuelva (los dos); guardas de ítems
  vacíos y de moneda sin cambio, y el alcance real respecto de `generarXml` (los dos); causas que
  faltaban en la tabla; fila de delivery corregida (A); el test «de integración» cortaba antes por
  «SIFEN deshabilitado» → `new SifenConfig()` y caso válido (B); interruptor (B); gate de
  promoción que no dependa solo del conteo (los dos).
- **Queda abierto**: el armado real relee los ítems sin `ORDER BY`
  (`FacturaLegalItemRepository:20`) y el último absorbe el residuo del descuento: el orden podría
  diferir del ensayo. No reproducido; no cambia qué casos cortan.

## Auditoría del diff (paso 8, 11/10/2026)

Tres ejes fijos; ningún condicional. Sin bloqueantes técnicos; un hallazgo de negocio que queda
como «Decisión abierta».

| Eje | Resultado |
|---|---|
| Fijo 1 — autorización y corrección | N/A en autorización. Reorden equivalente (mismos `save`, datos y orden; `save` devuelve la misma instancia); el ensayo no muta nada persistible y restaura `totalFinal` también al cortar; ningún llamador ensaya sin crear el DE después. Sonda propia con el `SifenService` real |
| Fijo 2 — esquema, estado y config | Sin esquema. Writes idénticos a `develop`; el corte no deja filas nuevas; por la API revierte todo. Faltaba declarar la property y el procedimiento para apagar el interruptor |
| Fijo 3 — contrato con clientes | Central: trata igual 400 y 500, no reintenta, mejora el texto. Sin otros consumidores de la API. **Desktop: la venta sin factura es indistinguible de una normal** → «Decisión abierta». Orden de merge con #171 |

Aplicado: la guarda de moneda solo corta si el código es una moneda extranjera conocida (un
código fuera del enum se factura en guaraníes y no necesita cambio: era un falso positivo);
interruptor leído como texto (un valor mal escrito tumbaba el arranque); motivo sin «null» cuando
la causa no trae mensaje; property declarada en `application.properties`; plan corregido donde
contradecía al código.

Queda abierto: descripción de ítem nula (pasa el ensayo; `generarXml` no verificado); ítems
releídos sin `ORDER BY`; una `SifenException` chequeada de `obtenerCDC` antes dejaba la factura
sin DE con la venta guardada y ahora corta (solo alcanzable con `fecha` nula, que ningún llamador
produce).

## Registro de implementación

- 11/10/2026: fases 1 a 3 y ajustes de auditoría implementados y pusheados. `./mvnw clean verify
  -B`: 420 tests, verde. Los tests del ensayo fallan si el builder no ensaya.
- 11/10/2026, runtime en local (`:8082`, perfil `dev`, `--sifen.scheduler.enabled=false`, API):
  - ítem a precio 0 → **400** `FACTURA_NO_EMITIBLE: El precio unitario del item 'ITEM DE REGALO'
    debe ser mayor a cero`; sin factura ni DE; `numero_actual` sin avanzar.
  - factura normal → 201, DE `PENDIENTE`, XML con total, cantidad, precio y teléfono correctos.
  - interruptor en `false` (como argumento de arranque, antes y después de pasarlo a texto) →
    vuelve el 500 «Transaction silently rolled back».
  - Sin envíos a SIFEN; factura, ítem y DE de prueba borrados y `numero_actual` restaurado.
- **Pendiente:** decisión abierta; prueba de Franco por `saveVenta` desde el desktop (una venta
  normal que siga facturando y una con un ítem a precio 0); PR, después del #171.

