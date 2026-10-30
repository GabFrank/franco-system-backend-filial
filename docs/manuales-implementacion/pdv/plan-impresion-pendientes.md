# Plan: pendientes de las reimpresiones (mensajes de error, caja abierta y montos vacíos)

Pendientes que dejaron las etapas de #534 / #213, aprobados por Franco el 30/10. Ramas `fix/impresion-pendientes` en
filial y central, desde `develop`.

## A. El filial muestra el motivo de sus propios errores

Hoy todo error del filial llega al cliente como «Internal Server Error(s) while executing query»: su
`GraphqlExceptionHandler` tiene un `@ExceptionHandler(GraphQLException)` que no corre (el starter solo los usa con
`graphql.servlet.exception-handlers-enabled`), así que manda el `DefaultGraphQLErrorHandler`. Los avisos armados a
propósito («No se pudo imprimir el balance», «Venta no encontrada», …) no llegan al cajero.

- El handler pasa a implementar `GraphQLErrorHandler` (como el central), pero **solo** muestra el mensaje cuando en la
  cadena de causas del error de un resolver hay una `GraphQLException` (la primera). Cualquier otra excepción (NPE,
  SQL, …) sigue llegando como «Internal Server Error(s) while executing query»: no se filtran detalles técnicos.
- Los errores de validación de la consulta (campo inexistente, tipos) siguen igual que hoy (se muestran).
- Se conservan `path` y `locations` del error original.
- El log no cambia: `SimpleDataFetcherExceptionHandler` ya deja cada excepción con su stack.
- Desktop: no depende del texto «Internal Server Error(s)» (grep vacío); `limpiarMensajeGraphQL` saca el prefijo y el
  snackbar muestra «Ups! Algo salió mal: <motivo>». Los flujos que salen bien no cambian.

## D. Balance de una caja abierta y gastos con montos vacíos (filial y central)

- `ImpresionService.printBalance`: `getFechaCierre().format(...)` revienta con la caja abierta (fecha de cierre nula).
  Imprime «Fecha Cierre: ABIERTA». Lo usan el botón «imprimir» de la caja en el desktop, el balance del mobile/PWA vía
  el proxy y `ticketEscpos` BALANCE.
- Gasto con `retiro_*` / `vuelto_*` nulos: revientan `printGasto` (`getRetiroGs().intValue()`; R$ y US$ salen «null»),
  `PdvCajaService.generarBalance` (unboxing en `retiro - vuelto`) y, en el filial, las sumas de `GastoService`. Al
  leer, nulo cuenta como 0. `printRetiro` tiene el mismo patrón con los montos de los detalles: igual.
- Raíz: `Gasto` con `@PrePersist` que pone 0 en los seis montos nulos; las filas ya guardadas las cubre la lectura.
  La columna tiene `DEFAULT 0` pero Hibernate inserta el nulo explícito.
- Central: mismos puntos (`ImpresionService.printBalance`, `printGasto` en sus dos variantes, `printRetiro`,
  `PdvCajaService.generarBalance`) y el mismo `@PrePersist`.

## Tests

- A (filial): `processErrors` con una `GraphQLException` directa y envuelta (causa) → su mensaje, con path; con una
  NPE → «Internal Server Error(s) while executing query»; un error de validación → igual que entra.
- D: balance de caja abierta en memoria contiene «ABIERTA» y no tira; gasto con montos nulos imprime y suma 0;
  `generarBalance` con un gasto nulo no tira; `@PrePersist` pone 0. Filial y central.
- Contra el código anterior: los de D fallan (NPE) y los de A dan el mensaje genérico.
- Baterías completas de los dos repos.

## Runtime

Filial y central de la rama contra las bases locales: imprimir el balance de la caja abierta 1185 (antes reventaba),
reimprimir el gasto 704 (montos nulos), y un error armado a propósito que ahora llega con su texto al desktop (por
pantalla: reimprimir con la impresora que rechaza → «Ups! Algo salió mal: No se pudo imprimir el balance»).

## Riesgo y vuelta atrás

- Bajo. A cambia solo el texto de los errores de negocio que ya existían; D evita NPE.
- Sale automático a las filiales del canal. Vuelta atrás: revertir; sin base de datos.

## Auditoría del plan (paso 5) y cambios

| Hallazgo | Eje | Cambio |
|---|---|---|
| El handler por defecto deja pasar los errores que ya son `GraphQLError` (`UnauthenticatedAccessException`, `ProductoDescripcionYaExisteException`) | B | primero: si la excepción es `GraphQLError`, va tal cual (como el central); después la primera `GraphQLException` con mensaje en la cadena de causas (tope de profundidad) |
| `ExceptionWhileDataFetching.getMessage()` trae el prefijo «Exception while fetching data (/x) : »; `PrintRouterService.rechazoPorRol` del central compara `startsWith("No autorizado")` | A | el error nuevo lleva el mensaje limpio de la causa, con `path` y `locations`; test |
| El default junta los errores de servidor en **un** genérico al final, sin path | B | igual: uno solo al final |
| Tres `GraphQLException` concatenan `e.getMessage()` de cualquier excepción (`FacturaLegalGraphQL` guardar factura, `DeliveryGraphQL` factura electrónica, `VentaGraphQL.reimprimirVenta`; también `reimprimirDelivery`) | A | mensaje fijo y detalle al log; el motivo solo se agrega si la causa es una `IOException` de la impresión (textos propios «No se pudo imprimir…») |
| El log `Error executing query` del default desaparece; los errores de validación dejan de loguearse | B | aceptado; los de los resolvers siguen en el log por `SimpleDataFetcherExceptionHandler` |
| El central reenvía el motivo del filial (`FilialCajaProxyService`, `PrintRouterService`) | A | efecto esperado: ahora con el motivo real |
| D quedó corto: `printBalance` tiene más `.intValue()` sin guarda (filial: tarjeta, transferencia, crédito, retiro, gasto, diferencia; central: todos), `printVueltoGasto` del central está vivo, `generarBalance` también suma `retiroDetalle.getCantidad()` y lee `getMoneda()` sin guarda | B | todos con guarda (nulo = 0; sin moneda, se saltea) |
| `GastoService.saveAndSend` del filial compara `retiro > 0` **antes** del `save`: un `@PrePersist` no lo protege | B | el filial normaliza los montos a 0 en `GastoGraphQL.saveGasto` (ya lo hace con el vuelto) y las comparaciones toleran nulo |
| Los nulos llegan por réplica desde el central (sin JPA) | A | la lectura tolerante es el arreglo real; `@PrePersist` + `@PreUpdate` solo en el `Gasto` del central |
| `GastoRepository` del central suma `retiro_gs - vuelto_gs` sin `COALESCE` en dos consultas: una fila con nulo se excluye | A | `COALESCE` en esas consultas, así histórico y nuevo dan lo mismo |
| `GastoService` filial l.234 usa `getVueltoRs()` para la moneda US$ | B | bug previo: se corrige (`getVueltoDs`), declarado |

## Resultado de la prueba (30/10)

Baterías: filial 796 tests, central 3140 (2 omitidos de siempre). Los tests nuevos fallan contra `develop`.

Filial de la rama (8082) y central de la rama (8094) contra las bases locales:

| Caso | Antes | Ahora |
|---|---|---|
| `reimprimirVenta` de una venta inexistente | «Internal Server Error(s)…» | «Venta no encontrada» |
| `imprimirBalance` con la cola que rechaza | «Internal Server Error(s)…» | «No se pudo imprimir el balance» |
| Error técnico (cliente inexistente en la base local) | genérico | sigue genérico |
| Consulta con un campo inexistente | error de validación | igual |
| Balance de la caja abierta 1185 (`imprimirBalance`, `ticketEscpos`), filial y central | NPE | sale, con «Fecha Cierre: ABIERTA» |
| Reimprimir el gasto 704 (montos nulos) | NPE | `true` |

Por pantalla (desktop web contra el filial de la rama): «Imprimir balance» en Últimas cajas con la cola que rechaza →
«Ups! Algo salió mal: No se pudo imprimir el balance» (antes «… Internal Server Error(s) while executing query»).

Datos locales tocados para la prueba y restaurados: responsable del gasto 704 y clientes inexistentes de las ventas
79306/79308/79309 (sin ellos el balance de la 1185 y el ticket del 704 cortan por otra causa, ya genérica).
El desktop no ofrece «Imprimir balance» para una caja abierta; el arreglo cubre el mobile/PWA y `ticketEscpos`.

## Auditoría del diff (paso 8)

| Hallazgo | Qué se hizo |
|---|---|
| `FacturaLegalBuilder` armaba «FACTURA_NO_EMITIBLE: » con el texto de cualquier excepción del ensayo del DE (firma, XML, ruta del certificado), que ahora puede llegar al cliente | solo conserva el texto de las reglas del armado (`IllegalArgumentException`, como «El precio unitario del item … debe ser mayor a cero»), así siguen llegando al cajero y a la API; el resto dice «no se pudo armar el documento electronico» y queda en el log. Test actualizado y uno nuevo |
| Nada probaba que Spring use el handler nuevo | probado en runtime: el filial de la rama devolvió los avisos con su texto |
| `GastoRepository` del central: `ORDER BY SUM(g.retiro_gs)` sin `COALESCE` (en `DESC` los nulos van primero) | con `COALESCE` |
| Falta test de la edición de un gasto en US$ | `GastoServiceEdicionTest` (vuelto en US$ y nulos en 0) |
| Mensajes con el motivo de la impresora (`reimprimirVenta`, `saveDeliveryEstado`) | se mantienen: son textos propios de la impresión y ayudan al cajero |
| Sin `RenderableNonNullableFieldWasNullError` del default | aceptado (menor) |
| COALESCE: una fila con retiro nulo y vuelto ahora resta el vuelto en el reporte por mes | coherente con el balance (nulo = 0) |
