# Plan — las reimpresiones del filial avisan cuando fallan y un ticket a medio armar se cancela (etapa 4b)

Rama `fix/impresion-avisar-falla-servicios`, **sobre** `fix/impresion-falla-ticket-chico` (#217, sin mergear: este PR
muestra también sus commits hasta que se mergee; base `develop`). Pieza: **filial**. Sin migración ni cambios de
esquema GraphQL. Port de lo aplicable de central#547.

## Problema

1. **Hilo de envío colgado.** Si el armado de un ticket falla antes del `close` (logo que no se lee, un dato nulo), la
   salida queda abierta y su hilo de envío lee el pipe para siempre. Pasa en `ImpresionService.printBalance`,
   `printGasto`, `printRetiro`, `printSenaCupon`, `VentaGraphQL.printTicket58mm`, `VentaCreditoGraphQL.printTicket58mm`
   y otros. Cerrar en un `finally` imprimiría medio ticket.
2. **Reimpresiones que informan éxito sin imprimir** (lo que ve el desktop contra el filial):

| Operación | Hoy | Desktop |
|---|---|---|
| `reimprimirRetiro` | `printRetiro` se traga la falla (`catch` vacío) y sin impresora sale en silencio ⇒ `true` | `true` ⇒ «Reimpresión con éxito»; otro ⇒ «Ups! intente de nuevo» |
| `reimprimirGasto` | `printGasto` se traga la falla ⇒ `true` | ignora el resultado |
| `imprimirBalance` (con impresora) | `PdvCajaService.imprimirBalance` ignora el `false` de `printBalance` | solo `console.log` |
| `reimprimirDelivery` | ignora el resultado del ticket; una excepción sale como «Delivery no encontrado» | ignora el resultado |
| `imprimirSenaCupon` | devuelve `false` si falla, pero un ticket chico que falla al enviarse sale como `true` (no espera) | `false` ⇒ aviso «No se pudo imprimir el comprobante…» |

Recordatorio: el filial no activa los manejadores de excepciones de GraphQL, así que un **error** llega al cliente
como «Internal Server Error(s)»; un **`false`** sí lo puede leer el desktop. Por eso las reimpresiones devuelven
`false` donde el desktop ya lo maneja.

## Cambio

### Fase 1 — vigía de tickets a medio armar

`SalidaConEnvioEnHilo`, sobre la espera opt-in de la 4a, con el vigía del central#547:
- si pasan 30 s sin actividad (creación, entrada y salida de cada `write`) y la salida no se cerró, el vigía
  interrumpe el hilo de envío: el envío falla sin llegar a CUPS (verificado en el central con CUPS real) y el hilo
  termina;
- un estado único ABIERTA / CERRADA / CANCELADA con compare-and-set; el cierre gana el estado **antes** de entregar el
  fin de datos y, si ganó el vigía, no lo entrega;
- `registrarFalla` guarda la primera falla («el ticket quedo a medio armar», no `InterruptedIOException`);
- un hilo daemon compartido revisa cada 1 s y se da de baja en `terminarEnvio()`;
- `PrinterOutputStream` y `TcpIpOutputStream` le pasan su hilo con `vigilarEnvio(hilo)`.

Cómo se combinan el vigía y la espera opt-in de la 4a (un `cerrar(long esperaMillis)` privado que usan `close()` y
`closeYEsperarEnvio`; `-1` = sin espera):

| Llamada | Estado | Resultado |
|---|---|---|
| `close()` / `closeYEsperarEnvio` | ABIERTA | CAS a CERRADA **siempre, también sin espera** (si no, el vigía cortaría un ticket de venta con una impresora lenta); entrega el fin de datos; espera solo con `esperarEnvio()` o con los `millis` del pagaré |
| `close()` / `closeYEsperarEnvio` | CANCELADA | CAS a CERRADA; **no** entrega el fin de datos; tira «No se pudo imprimir: el ticket quedo a medio armar» en los dos modos |
| `close()` / `closeYEsperarEnvio` | CERRADA | cierre idempotente; si es `closeYEsperarEnvio` y todavía nadie esperó, espera igual (el flag de «ya se esperó» va aparte del estado: un `close()` común antes no le quita la espera al pagaré) |
| vigía | ABIERTA, sin `write` en curso y 30 s sin actividad | CAS a CANCELADA, anota la falla y recién ahí interrumpe |

Mismo plazo de abandono (30 s) para la venta y las reimpresiones. En la venta, un ticket cancelado deja la venta
guardada: la `IOException` del `write` o del `close` ya la atrapan `printTicket58mm` (devuelve `false`) y el `catch`
de `saveVenta`. Por red (`TcpIpOutputStream`, que en el filial no usa ningún llamador de producción), la interrupción
no corta un socket bloqueado conectando o escribiendo: el hilo sigue hasta que el sistema corte; el cierre igual
informa la falla.

### Fase 2 — las reimpresiones avisan

En `ImpresionService`, `printRetiro`, `printGasto` y `printBalance` (y `printSenaCupon`):
- impresora y salida pasan de los campos compartidos (`selectedPrintService`, `printerOutputStream`) a variables
  locales;
- overload con `boolean esperarEnvio` (los actuales delegan con `false`, así `saveRetiro`, que es `@Transactional`,
  `saveGasto`, el cierre de caja y la impresión desde el cliente no cambian);
- `printRetiro` y `printGasto` pasan de `void` a `Boolean`: `false` si no hay impresora o el envío falla (antes se
  tragaba en silencio; ahora con `log.warn` del motivo). El `catch` sigue siendo de `IOException`: no cambia qué
  revierte `saveRetiro`. Los llamadores que no miran el resultado siguen igual.
- `printSorteoTickets` también pasa a variables locales, y los campos `selectedPrintService` / `printerOutputStream`
  de `ImpresionService` se borran (nadie más los lee).
- `TicketEscposGraphQL` (impresión desde la PC del cajero): si `printBalance`, `printGasto` o `printRetiro` no
  devuelven `true` al escribir en memoria, `GraphQLException("No se pudo armar el ticket")` en vez de devolver un
  ticket vacío o a medias.

Llamadores con `esperarEnvio = true`, que pasan a devolver el resultado real:
- `RetiroGraphQL.reimprimirRetiro` y `GastoGraphQL.reimprimirGasto`: devuelven lo que devuelve la impresión;
- `PdvCajaGraphQL.imprimirBalance` con impresora (desktop): un método nuevo de `PdvCajaService` que imprime con
  espera y tira `GraphQLException("No se pudo imprimir el balance")` si no salió. El `imprimirBalance` de 3 argumentos
  queda igual para el cierre de caja (`ConteoGraphQL`, después del commit, sin espera). El camino sin impresora
  (`imprimirBalanceVerificando`, la PWA vía el proxy del central) **no** espera: el proxy tiene su propio plazo y no
  está verificado que pase de 10 s;
- `DeliveryGraphQL.reimprimirDelivery`: devuelve `Boolean.TRUE.equals(printTicket58mm(..., esperarEnvio = true))`; una
  falla de impresión pasa a «No se pudo reimprimir el delivery: <motivo>» en vez de «Delivery no encontrado»;
- `VentaTarjetaGraphQL.imprimirSenaCupon`: espera, así un comprobante chico que no sale devuelve `false` y el desktop
  avisa.

Fuera de alcance: `reimprimirSorteo` (sin llamador en el desktop), `VentaCreditoGraphQL.imprimirVentaCredito` (el
desktop la llama contra el central), `PrintingService` / `MovimientoPrintService` (legados), `FacturaService.printTicket58mmFactura`
(sin llamadores), activar los manejadores de excepciones de GraphQL del filial, y que el desktop avise en
`reimprimirGasto`, `reimprimirDelivery` e `imprimirBalance` (hoy ignora el resultado).

## Tests

- Fase 1: los del central#547 adaptados (abandono a medio armar, nunca escrita, write bloqueado por una impresora
  lenta no se cancela, vigía y cierre no se contradicen, abandono por red), en los dos modos, más:
  - sin `esperarEnvio()`, cerrada normalmente con un envío más largo que el plazo: no se cancela ni tira (falla si el
    cierre sin espera no toma el estado);
  - sin `esperarEnvio()`, abandonada y después cerrada: tira «medio armar» y no cuelga;
  - `closeYEsperarEnvio` espera aunque antes hubo un `close()` común, y tira si la salida fue cancelada;
  - `close()` sin espera vuelve enseguida con un envío bloqueado (guarda del lock por lote);
  - al terminar, la cola del vigía queda vacía; `revisionMillis` se restaura en `@AfterEach`.
- Fallan contra la 4a: los de abandono y el de la cola del vigía; el resto protege que no cambie lo de la 4a.
- Fase 2: `ImpresionService` con `CapturaPrintService` y un `PrintService` que rechaza: `printRetiro` / `printGasto`
  devuelven `false` sin impresora y con el envío fallado (con espera), `true` si imprimen; y que sin espera el
  comportamiento de hoy no cambia. `reimprimirRetiro` / `reimprimirGasto` / `reimprimirDelivery` / `imprimirBalance`
  con mocks. Contra el código viejo con stubs mínimos.
- Batería `./mvnw clean verify -B`.

## Runtime

Filial de la rama en el 8082 contra `general` local, sin perfil, colas CUPS de prueba: reimprimir retiro, gasto y
delivery, imprimir balance y la seña contra la cola que acepta y la que rechaza; abandono con CUPS real; por pantalla,
«Reimprimir» retiro en el desktop (aviso «Ups! intente de nuevo») y un retiro nuevo con la cola que rechaza (se
guarda igual).

## Auditoría del plan (paso 5)

| Hallazgo | Eje | Qué se hizo |
|---|---|---|
| El cierre sin espera tiene que tomar el estado, o el vigía corta tickets de venta con una impresora lenta | B | tabla de estados; test que falla si no |
| `closeYEsperarEnvio` (pagaré) se rompía al copiar la clase del central | B | `cerrar(millis)` compartido y flag de espera aparte |
| `printSorteoTickets` usa los mismos campos compartidos | B | también local; campos borrados |
| `printRetiro` / `printGasto` fallaban en silencio | B | `log.warn` con el motivo |
| `TicketEscposGraphQL` ignora el resultado y devolvería un ticket vacío | A | error si no se armó |
| El balance de la PWA pasa por el proxy del central con su propio plazo | A | ese camino no espera |
| `reimprimirDelivery` puede devolver `null` | A | `Boolean.TRUE.equals` |
| La interrupción no corta un socket bloqueado | B | anotado como límite (sin uso en producción) |
| Tests en los dos modos y cuáles fallan contra la 4a | B | lista ampliada |

## Riesgo y vuelta atrás

- **Riesgo:** medio-bajo. Sale automático a las filiales del canal. Los guardados (retiro, gasto, venta, cierre) no
  esperan ni fallan por imprimir. El vigía solo interrumpe salidas sin actividad ni cierre durante 30 s.
- **Vuelta atrás:** revertir los commits; sin base de datos.
