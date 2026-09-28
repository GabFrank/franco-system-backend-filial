# Plan — auditar el `usuarioId` del input contra la sesión

Issue: #142 · Rama: `feat/auditoria-usuarioid-sesion` · Repo: **filial** (un solo repo)

## 1 · El problema

Los resolvers toman el id del usuario que firma la operación **del input del cliente** y nunca lo
contrastan contra el principal autenticado. `SecurityGraphQLAspect` exige que haya sesión, pero no
que la sesión sea la de quien dice el payload.

**147 usos de `getUsuarioId()` en 92 archivos** bajo `graphql/`. Uno solo en todo ese directorio lee
`SecurityContextHolder` (`VentaTarjetaGraphQL:249`).

La consecuencia no es acceso a datos ajenos: es **atribución**. Quién queda registrado como autor de
una venta, un cobro o una factura legal es quien el cliente dijo. El balance de caja se arma por
`usuario_id`, y la factura legal lleva ese usuario.

## 2 · Por qué no se puede arreglar de una

**`usuarioId` significa dos cosas distintas según el resolver**, y por eso un fix parejo rompe cosas:

| Significado | Ejemplo | Qué pasa si se sobreescribe con la sesión |
|---|---|---|
| **Quién firma la operación** | venta, cobro, caja, conteo | correcto: es lo que queremos |
| **Quién creó el registro**, preservado al editar | proveedor | **se reescribe al autor original en cada edición** |
| **A quién pertenece el hecho**, establecido por otra vía | marcación | **se rompe**: la identidad la da el rostro, no la sesión |

Las dos evidencias:

- `desktop:adicionar-proveedor-dialog.component.ts:434` manda `this.selectedProveedor.usuario?.id`
  junto con `creadoEn`, a propósito, para no pisar al creador.
- `MarcacionGraphQL.saveMarcacion:84` verifica el rostro contra el embedding del `usuarioId` del
  input y rechaza con similitud < 0,6. La sesión puede ser un terminal compartido; el dueño de la
  marcación es quien pasó la cara.

No se pueden enumerar los casos legítimos por grep con confianza sobre 147 usos. **Hay que medir.**

## 3 · Lo que sí está despejado

Resolver sesión → id **es viable hoy**, sin tocar el JWT y sin depender del #177 del central:

- `JwtGenerator:27` pone el **nickname** como subject del token.
- `JwtValidator:30-32` lo lee y arma el principal (`JwtUserDetails`, `userName` = nickname).
- `UsuarioService.findByNickname()` ya existe y normaliza (`trim` + `toUpperCase`).
- El nickname es **único**: 492 usuarios, 492 nicknames distintos, 0 nulos.

El JWT **no** trae el id, así que hace falta el salto nickname → `personas.usuario`.

## 4 · Alcance decidido

**Observar primero, enforzar después** (decisión de Franco). Esta fase **no cambia ningún
comportamiento**: resuelve el usuario de la sesión, lo compara contra el del input y registra la
diferencia. Nada se rechaza, nada se sobreescribe.

El objetivo es entrar a la fase de enforcement **con evidencia de producción** en vez de con una
clasificación adivinada de 92 archivos.

Marcación **entra**, a pedido, y es el control positivo del experimento: si el instrumento no
reporta mismatch ahí, está mal construido.

## 5 · Diseño

### 5.1 · `UsuarioSesionService` — resuelve la identidad de la request

```
SecurityContextHolder → principal (UserDetails) → username = nickname
                      → UsuarioService.findByNickname(nickname) → id
```

- Devuelve `Optional<Long>`; vacío si no hay principal.
- **Corre con `@Transactional(propagation = NOT_SUPPORTED, readOnly = true)`.** Ver **D1**.
- **Sin principal = proceso de sistema** *dentro de este alcance*. Ver **D2**: no es una propiedad
  general del filial.
- Cachea `nickname → id` con **TTL corto**, no eterno. Ver **D3**.

### D1 · Fuera de la transacción de la venta

`saveVenta` es `@Transactional` (`VentaGraphQL.java:192`). Si el SELECT de
`findByNicknameIgnoreCase` fallara **dentro** de esa transacción —un blip de conexión, un timeout
de statement, un deadlock—, Postgres marca la transacción física como abortada y **el INSERT de la
venta falla después**, aunque el `try/catch` del auditor se haya tragado la excepción Java.

«Nunca lanza» no alcanza: protege contra la propagación de una excepción, no contra envenenar la
transacción.

El repo ya vivió esto y lo documentó. `ConfiguracionFacturacionLector:52` lleva
`@Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)`, con este javadoc:

> *Corre fuera de la transaccion de la venta (NOT_SUPPORTED). saveVenta es @Transactional: si esta
> lectura fallara dentro de esa transaccion, la dejaria marcada rollback-only y la venta se
> perderia aunque el error se ataje aca.*

Se copia ese mecanismo tal cual. Agrava el caso que **una sola venta dispara el auditor varias
veces en la misma transacción**: `saveVenta`, más `saveCobro`, más `saveFacturaLegal` en las rutas
con factura.

### D2 · «Sin principal = sistema» vale solo en este alcance

Por WebSocket **no hay `SecurityContext`**, y no es una suposición: lo documenta
`CapturaCuponSubscription.java:9-18` — *«por WebSocket no hay SecurityContext… Verificado el
2026-09-10 contra el filial real»*. Ahí «sin principal» es un humano, no un scheduler.

Los 13 resolvers del alcance son todos `GraphQLMutationResolver` sobre HTTP, nunca
`GraphQLSubscriptionResolver`, así que la premisa se sostiene **acá**. Queda escrito para que en la
fase de enforcement nadie la generalice a un resolver nuevo que sí tenga subscription.

### D3 · El cache expira

El nickname **es mutable**: `UsuarioGraphQL.saveUsuario:55` mapea el input entero con ModelMapper,
y `deleteUsuario:65` existe. Hay `UNIQUE (nickname)` en la base (`usuario_un_nickname`), así que en
cualquier instante el mapeo es 1:1 — pero un rename **libera** el nickname viejo para otro usuario.

Un cache eterno se quedaría con `nickname_viejo → id_A` y devolvería el **id equivocado**. Eso es
peor que devolver vacío: un id incorrecto pero no nulo produce WARN espurios que en el log **no se
distinguen de un mismatch legítimo**, justo en la fase cuyo objetivo es juntar evidencia limpia.

Se resuelve con **TTL corto** en vez de invalidación enganchada a `UsuarioService.save()`: cubre
rename, borrado y reutilización con un solo mecanismo, se auto-sana, y no acopla un componente de
observabilidad al camino de administración de usuarios —que además necesitaría un segundo hook para
el delete—. El primer hit de cada nickname por ventana golpea la base; con D1 eso es seguro.

### 5.2 · `AuditorUsuarioId` — compara y registra

```java
auditor.verificar("VentaGraphQL.saveVenta", ventaInput.getUsuarioId());
```

Reglas duras:

- **Nunca lanza.** Todo el cuerpo va en try/catch que traga. Un instrumento de observabilidad que
  tumba una venta es peor que el problema que mide.
- **Nunca cambia comportamiento.** No devuelve nada que el llamador use.
- Registra en `WARN` **solo cuando difieren**, con un marcador fijo `AUDIT-USUARIOID` para poder
  grepear, más la operación, el id del input, el id de la sesión y la sucursal.
- Si el input trae `usuarioId` nulo, o no hay sesión, no registra.
- **Se puede apagar sin desplegar**: property `auditoria.usuarioid.habilitado` (default `true`),
  consultada en `verificar()`. Ver **D4**.

### D4 · Interruptor

El plan es exploratorio y admite que puede hacer ruido. Sin interruptor, la única forma de callarlo
es esperar el próximo release y el auto-update —15 minutos, y solo si `develop` ya tiene el cambio.
Una property que se lee en cada llamada permite silenciarlo con un restart del servicio.

### 5.3 · Dónde se instrumenta

Los 13 resolvers del núcleo — **43 de los 147 usos**:

| Área | Resolvers |
|---|---|
| Venta | `VentaGraphQL` (9), `CobroGraphQL` (1), `CobroDetalleGraphQL` (1), `VentaCreditoGraphQL` (3), `DeliveryGraphQL` (3), `VueltoGraphQL` (2), `VueltoItemGraphQL` (2) |
| Caja y dinero | `PdvCajaGraphQL` (4), `ConteoGraphQL` (4), `GastoGraphQL` (2), `RetiroGraphQL` (1) |
| Facturación | `FacturaLegalGraphQL` (7) |
| Control positivo | `MarcacionGraphQL` (4) |

La cola larga (79 archivos, 104 usos) queda fuera: son catálogos con 1 uso, y el costo de
instrumentarlos no se paga con la información que darían en esta fase.

## 6 · Cómo se lee la evidencia

En alpha, sobre el log del filial:

```bash
grep 'AUDIT-USUARIOID' /var/log/... | awk '{print $N}' | sort | uniq -c | sort -rn
```

Lo que buscamos responder, por operación:

1. ¿Hay mismatch alguna vez? Si una operación nunca lo tiene, se puede enforzar sin riesgo.
2. ¿El mismatch es sistemático (marcación, terminal compartido) o esporádico?
3. ¿Aparece algún par `(sesión, input)` que no tenga explicación? Eso ya sería un hallazgo.

## 7 · Tabla de datos nuevos

**N/A**: no nace ninguna columna, campo de GraphQL ni valor de enum. **Sin migración Flyway y sin
espejo en central.** Lo único que se agrega es log.

## 8 · Fases

### Fase 1 — el instrumento

`UsuarioSesionService` + `AuditorUsuarioId`, con tests:

- resuelve el id desde un `SecurityContext` armado a mano;
- devuelve vacío sin principal, y eso **no** se registra;
- el cache evita el segundo viaje a la base;
- **nunca lanza**: con `UsuarioService` reventando, `verificar(...)` vuelve sin excepción;
- **la transacción envolvente sobrevive** a una falla del repositorio — es el test que importa, y
  no el anterior: que no se escape una excepción Java no prueba que la transacción siga viva;
- con la property en `false` no registra nada;
- registra solo cuando los ids difieren.

### Fase 2 — cablear los 13 resolvers

Una llamada por punto de entrada, con el nombre de la operación. Sin otra lógica.

Gate de las dos: `./mvnw clean verify -B`.

## 9 · Lo que esta fase NO hace

- **No cierra el agujero.** Un cliente que mande un `usuarioId` ajeno lo sigue logrando; ahora queda
  registrado. Es el precio de no romper marcación ni las ediciones que preservan al creador.
- **No toca los 79 archivos de la cola larga.**
- **No arregla el `usuarioId` sin verificar de marcación cuando no viene embedding.**
  `saveMarcacion:70` solo valida el rostro **si** el input trae embedding; sin embedding no hay
  verificación de ningún tipo. Es un hallazgo aparte de esta lectura, anotado acá para no perderlo.

## 10 · Hallazgos de la auditoría del plan (paso 5)

Dos auditores, sin verse. Los cinco hallazgos se verificaron contra el código antes de aplicarlos.

| # | Eje | Hallazgo | Qué se hizo |
|---|---|---|---|
| 1 | B | «Nunca lanza» no protege la transacción de la venta; el repo ya resolvió esto en `ConfiguracionFacturacionLector:52` | Aceptado → **D1** |
| 2 | A | El cache eterno puede devolver un id equivocado tras un rename, indistinguible de un mismatch real | Aceptado → **D3**, con TTL en vez del hook que proponía el auditor |
| 3 | A | «Sin principal = sistema» es falso en general: por WebSocket no hay `SecurityContext` | Aceptado → **D2**, acotando la premisa al alcance |
| 4 | B | No hay forma de apagarlo sin desplegar | Aceptado → **D4** |
| 5 | B | El test propuesto medía lo que no importaba | Aceptado → test de transacción viva en la fase 1 |

Verificado y sin cambio: el principal **sí** es un `UserDetails` en toda request GraphQL autenticada
(`JwtAuthenticationProvider:38-52` devuelve `JwtUserDetails`, y no hay otro `AuthenticationProvider`
registrado), así que el salto nickname → id es sólido. Y el costo del cache es trivial: 492 filas,
techo natural del mapa.

## 11 · Qué queda sin verificar

- **El volumen del log — medido en local el 2026-09-24.** Una venta limpia escribe **0** líneas de
  auditoría. Una venta con `usuarioId` ajeno escribe **3**: una por cada eslabón instrumentado de
  la cadena (`saveVenta` → `saveCobro` → `saveCobroDetalle`). O sea que el costo no es por venta
  sino **por venta infractora, multiplicado por 3**. Si un terminal quedara mal configurado, cada
  una de sus ventas escribiría 3 WARN.
  No hay `logback` de producción en el repo; el rotado lo maneja `journald` por filial y no se pudo
  verificar el `SystemMaxUse` de las 24. Si marcación resulta ser un terminal compartido, va a registrar un WARN
  por marcación. Se verá en alpha; si molesta, se agrega un throttle por
  `(operación, par de ids)`. No se agrega ahora para no complicar la fase.
- **Si el nickname del token siempre resuelve.** Un usuario renombrado entre el login y la request
  daría un `Optional` vacío, que esta fase trata como «sin sesión». En una fase de enforcement eso
  tendría que distinguirse.
- **El central tiene el mismo patrón**, pero no se auditó en este trabajo.

## 12 · Estado de los pasos del ciclo

| Paso | Estado |
|---|---|
| 1 Rama | hecho — `feat/auditoria-usuarioid-sesion` desde `develop` |
| 2 Skill de dominio | hecho — `frc-filial` |
| 3 Análisis | hecho — secciones 2 y 3 |
| 4 Plan | este archivo |
| 5 Auditoría del plan | hecho — 2 agentes, 5 hallazgos, todos verificados y aplicados (sección 10) |
| 6 Presentar y commitear | en curso |
