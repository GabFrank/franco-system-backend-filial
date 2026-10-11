# PDV: «Venta + Ticket» queda sin factura, sin ticket y sin aviso (issue #169)

Estado: **plan aprobado por Franco el 11/10/2026; en implementación**. Rama `fix/venta-ticket-sin-factura-ni-aviso`, desde `develop`
(`b6137fe`). Solo filial. Va antes de la #170 (ver «Por qué primero»).

## El problema

Ruta `FACTURA_E_IMPRESION` de `VentaGraphQL#saveVenta` (`VentaGraphQL.java:290-301`):

1. `FacturaService.crearFacturaLegalDesdeVenta` (`FacturaService.java:336`, `@Transactional`)
   atrapa cualquier excepción de `FacturaLegalBuilder.build()` y devuelve `null` (`:402-405`).
2. `VentaGraphQL:293-296` le pasa ese `null` a `printTicket58mmFactura`, que da
   `NullPointerException` (`FacturaLegalGraphQL.java:916`).
3. El `catch` interno llama a `devolverTurnoSiCorresponde` con el NPE. Como la causa raíz no es
   una `GraphQLException`, `fallaAntesDeEscribir` da `false` y **el turno no vuelve**.
4. El `catch` externo (`:354`) hace `printStackTrace()` y devuelve la venta.

La venta se guarda (el NPE no cruza ningún proxy transaccional), pero el cliente se va **sin
ningún comprobante** y el cajero no recibe aviso. Lo disparan las validaciones previas del
builder: timbrado vencido, rango agotado, PDV sin timbrado del tipo que corresponde, timbrado sin
teléfono (#168).

### Por qué primero

La #170 (opción B) va a cortar antes de escribir cuando el DE no se pueda armar (ítem a precio 0,
`dEst` vacío…). En «Venta + Ticket» ese corte cae exactamente acá: sin este fix, una venta que hoy
falla con error visible pasaría a guardarse sin papel y sin aviso.

## Decisiones

1. **`FacturaService.crearFacturaLegalDesdeVenta` solo convierte en `null` la `GraphQLException`.**
   Es lo que lanzan las validaciones de `FacturaLegalBuilder.build()`, todas antes del primer
   write y sin marcar la transacción (`noRollbackFor`). Cualquier otra excepción que salga de
   `build()` ya dejó la transacción de la venta rollback-only: **se relanza**, y todo sigue como
   hoy (la venta se pierde en el commit; eso es la #170). El `catch (Exception)` actual convertía
   también ese caso en `null`, y por eso `null` no alcanzaba para saber si la venta sobrevivía.
   - En `VentaGraphQL` la excepción relanzada cae en el `catch` de `:298-300`, que no devuelve el
     turno porque la causa no es una `GraphQLException`. Sin papel y sin turno devuelto: igual que
     hoy, a propósito (devolverlo haría que la venta siguiente repita el fallo y también se pierda).
   - En delivery (`DeliveryGraphQL:242-260`) el `catch` ya trata cualquier excepción igual.
   - El armado previo de `FacturaService` (`:338-395`) está fuera del `try`: ya lanza hoy.
2. **Con factura `null` sale el ticket simple.** En `FACTURA_E_IMPRESION` se imprime con
   `printTicket58mm(...)` y los argumentos de la ruta `TICKET_SIMPLE` (respeta
   `imprimirEnCliente`). Desde la decisión 1, `null` sí significa «cortó una validación, no se
   escribió nada y la transacción está sana».
3. **El turno vuelve, antes de imprimir**, con `salioDeUnTurno(...)` + `devolverTurno()`. Con una
   validación que falla siempre (timbrado vencido) en modo `INTERVALO`, cada venta siguiente
   reintenta y vuelve a cortar: es inocuo (no escribe) y es lo que ya hace la factura silenciosa.
4. **Si el ticket simple falla al imprimir** (logo ausente, impresora), la venta queda guardada sin
   papel, igual que una venta `TICKET_SIMPLE` hoy.
5. **Si la factura existe y lo que falla es su impresión, no cambia nada** en esta rama.
6. **Venta a crédito con «Venta + Ticket»** (el desktop manda `ticket` y `facturar` con el
   crédito, y la ruta es `FACTURA_E_IMPRESION`): el respaldo es el ticket simple **sin pagaré**, y
   el turno no se devuelve (un crédito no lo toma). En esa ruta el `VentaCredito` ya no se guarda
   hoy (#133); no se cambia acá.
7. **Diferencias del ticket simple** respecto del ticket-factura, a saber: no dice nada sobre la
   factura y no suma el precio del delivery al total.

### El aviso al cajero — decidido (Franco, 11/10/2026): opción (a), sin aviso

Con la decisión 2 el cajero ve salir un ticket simple donde esperaba una factura, pero nada le
dice por qué. `saveVenta` devuelve la `Venta` y no hay canal para un aviso.

- **(a) Sin aviso en esta rama** — recomendado. El papel sale, el motivo queda en el log con
  prefijo, y el cajero puede emitir la factura a mano desde la venta. Un solo repo, sin contrato.
- **(a2) Una línea en el propio ticket simple** («FACTURA NO EMITIDA»): único aviso posible sin
  tocar el contrato, pero queda en un papel que se lleva el cliente.
- **(b) Campo nuevo y aditivo en `Venta`** (transitorio, como `ticketEscpos`), por ejemplo
  `avisoFacturacion: String`, que el desktop muestra al terminar la venta. Es cross-repo (filial +
  desktop, y el `.graphqls`), con el orden de despliegue de §3 del ciclo. Iría como trabajo aparte.

## Fases

### Fase 1 — extracción pura

- `VentaGraphQL`: la rama `FACTURA_E_IMPRESION` pasa tal cual a un método de instancia
  package-private `facturarEImprimir(...)`, sin anotaciones (se llama con `this.`, sin proxy, como
  `printTicket58mm`). `setTicketEscpos` queda en `saveVenta`. Sin cambio de comportamiento.
- Test `VentaGraphQLFacturaEImpresionTest` (`@Spy @InjectMocks VentaGraphQL`, colaboradores
  mockeados, `doReturn(true)` sobre `printTicket58mm`; el mock de `printTicket58mmFactura` lanza
  `NullPointerException` con factura nula, como en producción). **Se ve en rojo** sobre este commit.

### Fase 2 — el fix

- `FacturaService`: `catch (GraphQLException)` → log `FACTURA_NO_CREADA` + `null`; el resto se
  relanza.
- `facturarEImprimir`: con factura `null` → devolver el turno si corresponde y `printTicket58mm`.
- Tests:
  - factura `null` → ticket simple, no ticket-factura.
  - factura `null`, `INTERVALO` + `ventaTicketRespetaPolitica` → `devolverTurno()` una vez.
  - factura `null`, política sin turno → `devolverTurno()` nunca.
  - factura `null` a crédito → ticket simple, turno nunca.
  - `crearFacturaLegalDesdeVenta` lanza `RuntimeException` → ni ticket simple ni `devolverTurno()`.
  - factura creada → ticket-factura, sin ticket simple.
  - sin stub de impresión, con `ticketCliente` en memoria: el ticket simple produce bytes.
  - `FacturaService`: `build()` lanza `GraphQLException` → `null`; lanza otra cosa → la relanza.

### Verificación

- `./mvnw clean verify -B`, leído del log.
- Runtime (necesita sesión: token del central local, con replicación apagada). Disparador: PDV sin
  timbrado activo, o el corte del teléfono con SIFEN habilitado. «Venta + Ticket» con
  `imprimirEnCliente=true` y con impresora del servidor. Comprobar en la base: la venta quedó, no
  hay `factura_legal`, `numero_actual` no cambió. Restaurar el timbrado. Si no se puede levantar el
  central para el token, queda para la prueba de Franco desde el desktop y se anota.

## Datos nuevos

Ninguno (opción a).

## Alcance y despliegue

- Un solo repo, sin migración ni cambio de contrato.
- `develop` → filiales alpha; `release/beta` → 6 de farmacia; `master` → 18 de bodega, en ≤15 min.
- Rollback: código puro, sin estado nuevo.
- Cambio de comportamiento: donde hoy no sale nada, sale un ticket simple.

## Sin verificar

- Con qué frecuencia pasa en producción (no se leyeron logs de filiales).
- Qué hace el desktop al recibir la venta con un ticket simple donde esperaba la factura.
- Si `printTicket58mm` necesita algo que en `FACTURA_E_IMPRESION` no esté disponible (se llama con
  los mismos argumentos que `TICKET_SIMPLE`, que están todos en el mismo ámbito).

## Auditoría del plan (paso 5, 11/10/2026)

Los dos auditores, sin verse, encontraron el mismo error de fondo: la primera versión afirmaba que
un `null` de `crearFacturaLegalDesdeVenta` implicaba «nada escrito y transacción viva». Es falso:
el `catch (Exception)` de `FacturaService:402` también convierte en `null` una falla posterior a un
write, con la transacción ya rollback-only. Con aquel plan habría salido papel de una venta que se
revierte y el turno devuelto habría hecho perder también la venta siguiente. De ahí la decisión 1.

Otros hallazgos aplicados: el caso de venta a crédito (decisión 6); el test no fallaba «por NPE»
con el colaborador mockeado y `doNothing()` no sirve porque `printTicket58mm` devuelve `Boolean`
(fases 1 y 2); la verificación de runtime no disparaba el corte con SIFEN apagado.

Confirmado: la extracción no cambia AOP ni transacciones; el desktop imprime los bytes que recibe y
no marca la venta como facturada; mobile y mobile-pwa no llaman a `saveVenta`; no hay ningún canal
existente para avisarle al cajero; rollback del JAR sin estado nuevo.

