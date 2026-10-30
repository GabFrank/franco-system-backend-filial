# Plan — dos PARA ENTREGA simultáneos no crean dos facturas (#210)

Rama `fix/delivery-factura-unica` (desde `develop` `e3c6af6`). Pieza: **filial**. Sin migración, sin cambios de
GraphQL ni del desktop.

## Problema

`DeliveryGraphQL.saveDeliveryEstado(PARA_ENTREGA)` busca la factura vigente de la venta (`findVigenteDeVenta`, #208)
y, si no hay, consulta la política y la crea (`crearFacturaLegalDesdeVenta`). El método no tiene transacción: la
búsqueda y la creación corren cada una por su lado. Dos llamadas juntas para el mismo delivery (dos cajas, o el
desktop reintentando con la primera en curso) pasan las dos la búsqueda sin factura y crean dos (dos números, dos DE).
El plan de #208 lo dejó fuera de alcance («Dos clics simultáneos todavía pueden crear dos facturas»).

**Cómo llegan dos llamadas juntas en la práctica** (desktop `develop`, verificado 30/10): dos cajas distintas casi
no: la lista de deliveries filtra por la caja de la venta en el backend y no hay otra pantalla con el botón. El caso
realista es **la misma caja**: el link corta la mutation a los 60 s (`timeout-link.ts`) o el cajero cierra el
«Guardando...» (que tiene botón Cerrar); el error devuelve el delivery a ABIERTO, reaparece PARA ENTREGA y el
reintento llega mientras el filial sigue con la primera (SIFEN o impresora lentos). No hay reintento automático ni
atajo de teclado.

## Cambio (una fase)

1. **Lock por venta con el servicio que ya existe.** `BloqueoTransaccionalService.tomar(clave, "15s")`
   (`pg_advisory_xact_lock`, el de #185 en `LoteFefoService`) con clave `factura-venta:<sucursalId>:<ventaId>`.
2. **La política se lee antes**, fuera de la transacción: `configuracionFacturacionLector.resolver()` es
   `NOT_SUPPORTED` y adentro pediría una segunda conexión mientras la primera tiene el lock (auditoría A y B). Es lo
   que ya hace `saveVenta` (`VentaGraphQL.java:237`).
3. **Transacción solo para «buscar → decidir → crear».** En `saveDeliveryEstado`, `findVigenteDeVenta`,
   `facturarDelivery` (toma el turno) y `crearFacturaLegalDesdeVenta` pasan a correr dentro de un
   `new TransactionTemplate(transactionManager)` armado en la llamada (patrón de `SorteoParticipacionService`, sin su
   `REQUIRES_NEW`: acá no hay transacción afuera), que primero toma el lock. Devuelve la factura existente, la nueva o
   null (ticket simple). La segunda llamada espera al commit de la primera, encuentra su factura y la reimprime: mismo
   camino que el reintento de #207, sin otra factura ni otro turno.
4. **El catch de la factura envuelve el template entero**, commit incluido. Una falla al generar el DE marca la
   transacción para rollback y el builder la ataja (`FacturaLegalBuilder.java:226`), así que el error sale en el
   commit (`UnexpectedRollbackException`). Hoy ese commit está dentro del `try` y el cajero ve «Problema al generar
   factura electrónica»; tiene que seguir igual, con el mismo `devolverTurno` (auditoría B, R1).
5. **La impresión queda afuera**, después del commit. Si se envolviera todo el método, una falla de impresora haría
   rollback de la factura (rompe #207) y el lock quedaría tomado mientras imprime.
6. **Espera vencida:** si en 15 s no se obtiene el lock (SQLSTATE `55P03`), el cajero recibe «La factura de este
   delivery se está generando en otra operación. Reintentá en unos segundos.» en vez de «Algo salió mal» (no dice
   «otra caja»: el caso realista es la misma caja reintentando). Para eso
   `LoteFefoService.esEsperaVencida` pasa de package-private a `public`. Se descartó moverlo a
   `BloqueoTransaccionalService` (sugerencia de A): obliga a tocar `LoteFefoService` y su test por una dependencia
   estática.

Lo que cambia para el cajero: si dos cajas marcan el mismo delivery a la vez, la segunda espera a la primera y
**vuelve a imprimir** la misma factura y la comanda (en su PC, si imprime desde el cliente). Dos papeles, una factura.

Por qué no las otras opciones de la issue:

- **`SELECT … FOR UPDATE` sobre la venta:** fila replicada filial→central; el lock por nombre no toca filas y es el
  patrón ya usado.
- **Restricción única:** hay ventas que ya quedaron con dos facturas activas (por eso `findVigenteDeVenta` tolera
  varias, #208), así que la migración fallaría o exigiría limpiar datos en 24 bases.

### Por qué no hay deadlock ni lectura vieja

- La transacción nueva toma **primero** el lock de la venta y después lo que ya tomaba la creación (timbrado, etc.).
  Ningún otro camino toma `factura-venta:*`, así que no hay orden inverso.
- `crearFacturaLegalDesdeVenta` y el builder son `@Transactional` REQUIRED: se suman a la transacción nueva en vez de
  abrir la suya. El builder no usa `REQUIRES_NEW` y no llama a SIFEN por red (arma y firma el DE; el envío va por lote
  después), así que el lock se retiene lo que tarda crear la factura.
- PostgreSQL en READ COMMITTED: la búsqueda corre después de obtener el lock y ve la factura que la primera commiteó.
- Los turnos de la política viven en memoria (`synchronized`); el rollback no los toca, y `devolverTurno` sigue igual.
- Dentro del lock no se pide otra conexión: la política se leyó antes y el camino de creación no tiene
  `REQUIRES_NEW` (el único, `SifenService.consultarDE`, no está en este camino). Pool Hikari por defecto (10).
- El filial tiene `OpenEntityManagerInViewFilter`, pero `findVigenteDeVenta` es una consulta (va a la base, no a la
  caché de primer nivel) y nadie cargó la factura de la otra llamada antes del lock.

## Tabla de datos nuevos

Ninguno. La clave del lock no se persiste.

## Tests

- `DeliveryParaEntregaReintentoTest` (se agrega):
  - **orden:** se toma el lock de `factura-venta:24:20` antes de `findVigenteDeVenta`; la creación ocurre antes del
    commit, y la impresión después del commit (`InOrder` sobre el `PlatformTransactionManager` mockeado). Con el
    código viejo falla (no hay lock ni transacción).
  - **política afuera:** `resolver()` se llama antes de `getTransaction`.
  - **espera vencida:** si `tomar` tira un error con SQLSTATE `55P03`, el mensaje es el de reintentar y no se crea
    factura.
  - **falla en el commit:** si `commit` tira `UnexpectedRollbackException`, el mensaje es «Problema al generar factura
    electrónica» y no se imprime.
  - El test existente con factura ya creada sigue verificando que no se toma turno. Los dos tests existentes siguen
    pasando (se agregan los mocks del lock y del transaction manager).
  - Límite: los mocks no prueban que los proxies se sumen a la transacción ni la espera real; eso va en runtime.
- Batería `./mvnw clean verify -B`.

## Runtime (paso 9)

Como en #208: jar en un puerto propio contra `general` local (sucursal 24, SIFEN apagado), política de la 24 en TODAS
y timbrado no electrónico en el PDV, cola CUPS `zz_ok` de prueba; se restaura todo al final.

1. **Reproducir con `develop`:** un delivery ABIERTO, dos `saveDeliveryEstado(PARA_ENTREGA)` lanzados juntos por API
   → dos facturas para la venta. **Si no se reproduce, no se sigue**: el paso 2 daría un falso OK (auditoría B, R3).
2. **Con el fix:** otro delivery. Se toma antes el lock desde `psql` (`pg_advisory_lock`, que comparte claves con el
   `_xact_`), se lanzan las dos llamadas, se ve en `pg_locks` que las dos esperan y recién ahí se suelta → una sola
   factura; las dos llamadas responden OK y la segunda reimprime la misma.
3. **Por pantalla:** PARA ENTREGA desde el desktop (`ng serve -c web`) sobre un delivery: factura e impresión como
   siempre; repetirlo reimprime sin otra factura (#207 sigue).
4. **Espera vencida:** tomar el lock a mano desde `psql` (`select pg_advisory_lock(hashtext('factura-venta:24:<id>'))`)
   y marcar PARA ENTREGA → a los 15 s, el mensaje de reintentar; soltar el lock y reintentar → factura.

## Fuera de alcance

- **«Finalizar con factura» de un delivery** (`saveFacturaLegal` + `vincularFacturaLegalAVenta`,
  `FacturaLegalGraphQL.java:453`) liga una factura nueva sin mirar si la venta ya tiene una: a un delivery facturado en
  PARA ENTREGA se le puede emitir otra, en secuencia (no es carrera). Tomar el lock ahí no alcanza: falta buscar la
  vigente. Issue aparte: #221 (auditoría A).
- `facturarEImprimir` de `saveVenta` no se cruza: la venta es nueva y nadie la ve hasta el commit.
- Las ventas que ya tienen dos facturas no se tocan.
- Si falla el commit, el delivery ya quedó PARA_ENTREGA (se guarda antes, en otra transacción) y el turno se devuelve
  solo si la falla fue antes de escribir; el reintento crea la factura. Es lo mismo que pasa hoy.

## Qué queda sin verificar y cómo

- La carrera real entre dos PCs: se simula con dos llamadas simultáneas a la misma API.
- PostgreSQL de las filiales: `pg_advisory_xact_lock` y `hashtext` existen desde 8.x; ya los usa #185 en las mismas
  bases.

## Auditoría (paso 5)

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| Mayor: `resolver()` es `NOT_SUPPORTED`; dentro del lock pide otra conexión | A y B | se lee antes del template (punto 2) |
| Mayor: el commit fuera del `try` cambia «Problema al generar factura» por «Algo salió mal» si falla el DE | B | el catch envuelve el template (punto 4) + test |
| Menor: la prueba simultánea puede no solaparse y dar falso OK | B | paso 1 obligatorio; lock previo desde `psql` en el paso 2 |
| Menor: el test con mocks no prueba proxies ni espera real | B | declarado; lo cubre runtime |
| Menor: la segunda caja reimprime | A | declarado en «Lo que cambia para el cajero» |
| Menor: «Finalizar con factura» puede duplicar en secuencia | A | fuera de alcance, issue aparte |
| Menor: mover `esEsperaVencida` a `BloqueoTransaccionalService` | A | descartado (diff más grande) |

Sin hallazgos en: contrato GraphQL y desktop (igual), migración/variables/central (no hacen falta), choque con
`lote-saldo` (sin ciclo), `REQUIRES_NEW` en la creación (no hay), vuelta atrás (no deja nada).

## Auditoría del diff (paso 8)

- Fijo 2 (esquema y migración): N/A, el diff no trae migración, `.graphqls`, entidad ni enum.
- Fijo 1 (autorización): N/A para el diff, que no agrega ni cambia resolvers ni firmas.
- Fijo 3 (contrato) y corrección, con un revisor: sin bloqueantes. Contrato igual (el desktop no interpreta el texto
  del error; PWA y mobile no llaman a la mutation) y cada camino de error conserva su mensaje y su manejo de turno.
  - **Mayor:** ningún test cubría el turno (todos en modo TODAS) → tres tests en modo INTERVALO: la validación del
    builder devuelve el turno; la espera vencida y la falla del DE en el commit no.
  - **Declarado, previo:** `findVigenteDeVenta` busca con la sucursal del delivery y el builder guarda la factura con
    la del timbrado. Si un PDV de delivery facturara con timbrado de otra sucursal, el reintento no la encontraría
    (lock o no). Viene de #208; no se cambia acá.
  - **Declarado:** si además falla el rollback, la excepción que sale es la del rollback; ya pasaba en el proxy de
    `crearFacturaLegalDesdeVenta`.

## Riesgo, despliegue y vuelta atrás

- **Riesgo:** bajo. Un método del filial; sin migración ni cambio de API. Lo nuevo que puede pasar: una espera de
  hasta 15 s si dos cajas marcan el mismo delivery a la vez.
- **Despliegue:** al mergear a `develop` sale solo a las filiales alpha en ≤15 min (reinicio del servicio); beta y
  stable con su promote.
- **Vuelta atrás:** revertir el commit. No deja datos ni esquema.
