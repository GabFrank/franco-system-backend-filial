# Plan — las reimpresiones avisan cuando el ticket no sale (#213)

Rama `fix/impresion-falla-ticket-chico` (desde `develop`). Pieza: **filial** (Spring Boot 2.7.18 / Java 11 según el
`pom.xml`; el CLAUDE.md del filial todavía dice 2.1 / Java 8). Sin migración ni cambios de esquema GraphQL.

Port de central#544 (issue central#534) y central#545, con una diferencia decidida por Franco (29/10): **la espera
es opt-in**. Fuera de las reimpresiones, la salida cierra como hasta hoy.

## Problema

1. `SalidaConEnvioEnHilo.close()` no espera al hilo que manda el trabajo a CUPS: si el envío falla cuando el ticket
   ya entró entero en el buffer del pipe (1024 bytes), nadie se entera. En el central, con CUPS real, un ticket de
   300 bytes a una cola que rechaza se informó como impreso 19 de 20 veces.
2. `FacturaLegalGraphQL.printTicket58mmFactura` / `printTicket58mmFacturaMonedaExtranjera` / `generarFacturaAutoImpreso`
   y `VentaGraphQL.printTicket58mm` cierran el **campo** `printerOutputStream` del singleton, que con dos impresiones
   simultáneas puede ser la salida de otro request (corta el ticket ajeno; con espera, además esperaría por él).
3. `VentaGraphQL.reimprimirVenta` ignora el resultado: con factura devuelve `false` aunque imprima; sin factura,
   `true` aunque falle; venta o cobro inexistentes, `false`. «Últimas ventas» del desktop toma `res != null` como
   éxito, así que un `false` hoy se muestra «Reimpreso con éxito».
4. `generarFacturaAutoImpreso` (línea 768) hace `getNombre().substring(0, 22)` del cliente sin mirar el largo. Es el
   único `substring` sin control en la impresión del filial.

## Por qué la espera es opt-in

`saveVenta` es `@Transactional` e imprime adentro (factura, ticket, pagaré, sorteo). Ahí una falla de impresión ya solo
va al log: esperar no le avisa nada al cajero y alargaría la transacción, que tiene tomado el lock del saldo por lote
(otra caja espera 15 s). Lo mismo la API de facturas (`crearFacturaLegal`, `@Transactional`), el alta manual, el
delivery (un vencimiento de la espera con el ticket ya impreso llevaría a imprimirlo dos veces) y la impresión por caja
(una espera por factura). El filial se despliega solo a todas las sucursales del canal: el cambio por defecto tiene
que ser ninguno.

## Cambio (una fase)

- `SalidaConEnvioEnHilo`: la parte de central#544 **sin el vigía** (va en la etapa 4b): un `CountDownLatch` de fin de
  envío y la falla en un `AtomicReference`.
  - `close()` sin cambios por defecto: cierra el pipe y vuelve.
  - `esperarEnvio()`: marca la salida para que `close()` espere al hilo de envío hasta 10 s y tire
    `IOException("No se pudo imprimir: <motivo>")` si falló, «No se pudo confirmar la impresion en 10 s: revisar la
    impresora antes de reintentar» si no terminó, o `InterruptedIOException`. Solo el primer cierre espera y avisa.
  - `PrinterOutputStream.closeYEsperarEnvio(millis)` (pagaré, para que salga después de la factura): espera hasta
    `millis` y avisa la falla, en vez de `close()` + `join`. Mismo tope de 5 s que hoy; su llamador ya atrapa y loguea.
- `TcpIpOutputStream`: registra también un `Error` del hilo de envío.
- Salidas locales en vez del campo compartido: `VentaGraphQL.printTicket58mm`, `FacturaLegalGraphQL.printTicket58mmFactura`,
  `printTicket58mmFacturaMonedaExtranjera` y `generarFacturaAutoImpreso` crean y cierran su propia salida (deja de
  cerrarse el campo). En `generarFacturaAutoImpreso` también la impresora deja de leerse del campo.
- `FacturaLegalGraphQL`:
  - overloads de `printTicket58mmFactura` y `...MonedaExtranjera` con `boolean esperarEnvio`; los actuales delegan con
    `false`. Sin el `catch` que se traga la `IOException` del cierre: con `false` el cierre no la produce, con `true`
    llega al llamador.
  - `reimprimirFacturaLegal` usa `esperarEnvio = true` y loguea el motivo cuando devuelve `false`.
  - `generarFacturaAutoImpreso`: la línea del cliente pasa a un helper `lineaCliente(Cliente)` como el del central
    (recorta a 22 solo si es más largo; sin persona o nombre, no escribe la línea).
- `VentaGraphQL`:
  - overload de `printTicket58mm` con `boolean esperarEnvio`; los actuales delegan con `false`.
  - `reimprimirVenta`: usa `esperarEnvio = true` y devuelve `true` solo si imprimió; si no, `GraphQLException`: «No se
    pudo reimprimir la factura», «No se pudo reimprimir el ticket», «Venta no encontrada» o «La venta no tiene cobro».

## Lo que ve cada llamador

| Llamador | Antes | Ahora |
|---|---|---|
| desktop «Últimas ventas» → `reimprimirVenta` | con factura «Reimpreso con éxito» aunque fallara; sin factura igual | «Reimpreso con éxito» solo si salió; si no, el error genérico con el motivo |
| desktop lista de facturas (contra el filial) → `reimprimirFacturaLegal` | `false` sin aviso | `false` también cuando el envío falla al cerrar; desktop#652 lo avisa |
| venta del PDV, API de facturas, alta manual, delivery, impresión por caja, sorteo, balance, gasto, retiro | — | sin cambios |
| pagaré | espera hasta 5 s, sin aviso | espera hasta 5 s; la falla va al log |

Fuera de alcance (etapa 4b, port de central#547): el vigía de tickets a medio armar, los métodos que se tragan la
falla en `ImpresionService` y `VentaCreditoGraphQL.imprimirVentaCredito`, la impresora no encontrada que sale en
silencio, y el campo compartido en `ImpresionService`, `FacturaService` y `VentaCreditoGraphQL`.

## Tests

- `PrinterOutputStreamTest` / `TcpIpOutputStreamTest`:
  - sin `esperarEnvio()`, un ticket chico con el envío fallado cierra sin tirar (el comportamiento de hoy, para la
    venta);
  - con `esperarEnvio()`: avisa el motivo al cerrar; el segundo cierre no repite; espera vencida (10 s configurable a
    300 ms en el test); envío normal termina antes de volver;
  - `closeYEsperarEnvio` avisa la falla y respeta el tope;
  - el de `unTicketChicoConElEnvioFalladoNoCuelgaAlCerrar` sigue (sin espera) y se agrega su versión con espera.
- `lineaCliente` (en `FacturaLegalGraphQL`): 15, 22 y 33 caracteres, sin nombre, sin persona.
- `reimprimirVenta` con mocks (al lado de `VentaGraphQLTicketDestinoTest`): factura `true` / `false`, ticket `true` /
  `false`, venta inexistente, sin cobro; y que llama con `esperarEnvio = true`.
- Que la venta no espera: `VentaGraphQLFacturaEImpresionTest` verifica que `facturarEImprimir` llama a la factura sin
  espera.
- Contra el código viejo: los métodos nuevos no existen, así que se corren con stubs mínimos que compilen y sin
  comportamiento (como en el central).
- Batería `./mvnw clean verify -B`.

## Runtime

Filial de la rama en el 8082 contra `general` local, SIFEN apagado, colas CUPS de prueba:
- `reimprimirVenta` (con y sin factura) y `reimprimirFacturaLegal` contra una cola que acepta y una que rechaza;
- una venta del PDV con la cola que rechaza: se guarda y el tiempo de respuesta no cambia respecto de `develop`;
- por pantalla, «Últimas ventas» → Reimprimir con las dos colas;
- con las clases de la rama y CUPS real: ticket de 300 bytes a la cola que rechaza, con y sin `esperarEnvio()`.

### Resultado (29/10)

- Tests: batería `./mvnw clean verify -B` verde (749). Con la lógica anterior (stubs mínimos) fallan los 6 de
  `reimprimirVenta`, 3 de `lineaCliente` y los 5 «con espera»; pasan los que protegen el cierre sin espera.
- CUPS real con las clases del filial, ticket de 300 bytes a `zz_rechaza`, 20 veces: sin espera (la venta) 17 se
  informan como impresos, como hoy; con `esperarEnvio()` (reimpresión) 20 con error, cierre en ≤ 3 ms.
- Filial de la rama en el 8082 contra `general` local (sin perfil: SIFEN y backup apagados; Flyway apagado):
  `reimprimirVenta` de la 79270 (con factura) y la 79314 (sin factura) devuelve `true` con `zz_ok` y error con
  `zz_rechaza` (en el log: «No se pudo reimprimir la factura» / «Printer is not accepting job.»).
- Pantalla, desktop web contra el filial (PDV 25, caja 1185 de MR): PDV → Utilitarios → Reimpresión de ticket → venta
  79270: con `zz_rechaza`, «Ups! Algo salió mal: Internal Server Error(s) while executing query» (antes «Reimpreso con
  éxito»); con `zz_ok`, «Reimpreso con éxito».
- Venta del PDV con `zz_rechaza` (CR + Ticket, agua 20 L): la venta 79315 se guardó CONCLUIDA en 546 ms; la falla del
  ticket quedó en el log, como antes.

Encontrado en la prueba, fuera de alcance:
- El filial no activa los manejadores de excepciones de GraphQL (`graphql.servlet.exception-handlers-enabled`): todo
  error llega al cliente como «Internal Server Error(s) while executing query», sin motivo.
- `reimprimirFacturaLegal` no está publicado en el esquema del filial: la lista de facturas del desktop reimprime
  contra el central.
- La factura 33137 de la base local tiene el timbrado con `is_electronico` nulo y no se puede imprimir (NPE en
  `printTicket58mmFactura`). Antes «Últimas ventas» lo mostraba como «Reimpreso con éxito».

## Auditoría del diff (paso 8)

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| Solo las dos reimpresiones esperan; el resto cierra como antes; sin referencias al campo borrado | Fijo 3 | verificado |
| Pagaré: el cierre que ahora avisa puede tapar el error del `write` (el llamador loguea y la venta sigue) | Fijo 3 | bajo, queda |
| Impresora no encontrada en `reimprimirVenta` da el mensaje genérico | Fijo 3 | bajo, etapa 4b |
| `printService` sigue siendo un campo compartido en `FacturaLegalGraphQL` | Fijo 3 | bajo, previo, etapa 4b |
| Una reimpresión contra una impresora colgada retiene un hilo hasta 10 s | Fijo 1 | bajo: requiere sesión, tope fijo, la venta no espera |
| Sin cambios de permisos ni de datos devueltos; los mensajes no llegan al cliente (manejadores apagados) | Fijo 1 | verificado |
| Fijo 2 (esquema y migraciones) | — | N/A para filial: el diff no toca `.graphqls` ni migraciones |

## Auditoría del plan (paso 5)

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| Con espera por defecto también esperarían el sorteo (dentro de `saveVenta`), balance, gasto, retiro | A | la espera pasa a opt-in, solo en las reimpresiones |
| `VentaGraphQL.printTicket58mm` cierra el campo compartido: una venta podría esperar por la salida de otro | B | salidas locales |
| Copiar la clase del central trae el vigía y su `CANCELADA` | B | se porta sin vigía |
| `reimprimirVenta` seguía devolviendo `false` en venta o cobro inexistentes | B | errores con motivo |
| Delivery: con espera, un vencimiento con el ticket ya impreso llevaría a imprimirlo dos veces | B | el delivery no espera |
| Impresión por caja: una espera por factura | B | no espera |
| Los tests nuevos no compilan contra el código viejo | B | stubs mínimos para la prueba |
| Faltaban tests del cableado | B | `reimprimirVenta` y `facturarEImprimir` |
| El `pom` dice Boot 2.7.18 / Java 11 | B | anotado; el port no usa nada nuevo |

## Riesgo y vuelta atrás

- **Riesgo:** bajo. Sale automático a las filiales del canal en ≤15 min tras el merge. Por defecto nada cambia; las
  dos reimpresiones esperan a CUPS (< 1 s) y avisan la falla. Con un desktop sin #652, la reimpresión de factura que
  falla sigue sin aviso, como hoy.
- **Vuelta atrás:** revertir el commit; sin base de datos.
