# Plan #147 — reabrirVentaTarjeta controla el rol en el filial

Rama: `fix/venta-tarjeta-reabrir-rol` (desde `develop`). Una sola pieza: filial. Un PR.

## Qué cambia

`VentaTarjetaGraphQL.reabrirVentaTarjeta` exige, antes de llamar a `VentaTarjetaService.reabrir`,
el mismo rol que el diálogo de la caja del desktop (`ventas-tarjeta-caja-dialog`):

| Caja del cobro | Rol exigido (ADMIN pasa siempre) |
|---|---|
| abierta (`pdv_caja.activo = true`) | `VENTA TARJETA COMPLETAR` |
| cerrada, sin caja o caja que no existe | `ANALISIS DE CAJA` |

Con `RolSesionService.exigirRol(...)`, que ya usan los otros resolvers del filial (usuario del
token, roles de `personas.usuario_role`). El cobro se busca con
`service.findByIdAndSucursalId(id, sucursalPropia)`; la caja con `pdvCajaService.findById`.
Abierta = `Boolean.TRUE.equals(caja.getActivo())`, el mismo criterio que `cajaEstaAbierta` del
desktop. Si el cobro no existe, rige el lado estricto y después `reabrir` responde su error de
siempre.

El control va en el resolver, como en el resto del filial: `VentaTarjetaService` se arma por
constructor y no conoce `PdvCajaService`. Se inyecta `RolSesionService` en `VentaTarjetaGraphQL`
(hoy no lo tiene).

Una caja que no se puede leer (excepción de `findById`) no se atrapa: la mutation falla y no
reabre. Solo una caja que no existe cuenta como «desconocida» (lado estricto).

## saveVentaTarjeta: solo altas PENDIENTE (agregado el 2026-11-20 tras la auditoría)

El ciclo de un cobro que ya existe va por `completarVentaTarjeta`, `marcarVentaTarjetaNoCompletada[s]`
y `reabrirVentaTarjeta`, que tienen sus controles. `saveVentaTarjeta` queda para lo único que lo usa:
el alta desde el PDV (`venta-touch.component.ts#registrarPagosConTarjeta`, sin `id` y con
`estado: 'PENDIENTE'`). Rechaza con `GraphQLException`:

- un input con `id`;
- un `estado` distinto de `PENDIENTE` (null o vacío valen `PENDIENTE`; se compara sin mayúsculas
  ni espacios y se guarda `PENDIENTE`).

Además, como el resto de las mutations del filial: `sucursalId` pasa por `exigirSucursalPropia` y
el usuario sale del token (`buscarUsuario`, el argumento queda de respaldo). Para el PDV da lo
mismo: manda la sucursal de su caja y su propio usuario.

Se controla en el resolver, antes de armar la entidad. `guardarConAviso` no cambia: el evento
`venta_tarjeta.editada` sigue en el catálogo, sin emisor por esta vía (ningún cliente editaba).

Llamadores verificados el 2026-11-20: el desktop crea solo por `onSavePendiente`, sin `id` y con
`PENDIENTE`, en `develop`, `release/beta`, `master` y desde el primer commit del módulo (julio). La PWA
y la app Android crean `COMPLETADO` y mandan `id`, pero contra el `saveVentaTarjeta` del
**central** (la PWA no tiene link al filial; Android contra un filial ya no andaba: el filial no
tiene `updateVentaTarjeta`). El rechazo en el PDV mostraría el aviso fijo de «no se registró el
cobro»; con el input del PDV no ocurre.

## Fases

1. **Fase única** — resolver + test `VentaTarjetaReabrirRolTest` (Mockito, patrón de
   `PdvCajaAccesoTest`):
   - caja abierta: pide `VENTA TARJETA COMPLETAR`; sin el rol no llama a `reabrir`;
   - caja cerrada: pide `ANALISIS DE CAJA`; sin el rol no llama a `reabrir`;
   - caja inexistente o cobro sin caja: pide `ANALISIS DE CAJA`;
   - con el rol correcto delega en `reabrir` y devuelve lo que devuelve;
   - el cobro se busca con la sucursal que devuelve `exigirSucursalPropia`, no con el `sucId` crudo.
   Cada caso verifica `exigirRol(<rol exacto>)` y el rechazo se stubea para ese rol exacto (no
   `any()`: con varargs pasaría aunque se pida el rol equivocado).
   Revertir el resolver y comprobar que los tests de rechazo fallan.
2. **Fase 2** — `saveVentaTarjeta` solo altas + test `VentaTarjetaSaveSoloAltaTest`:
   - con `id` rechaza y no guarda;
   - con `estado` `COMPLETADO` / `NO_COMPLETADO` rechaza y no guarda;
   - sin `id` y con `PENDIENTE`, null, vacío o ` pendiente ` guarda `PENDIENTE` (lo que manda el PDV);
   - la sucursal guardada es la de `exigirSucursalPropia`, y una ajena se rechaza sin guardar.
   Revertir y comprobar que fallan los rechazos. Prueba de runtime: el alta con el input exacto del
   PDV sigue creando el cobro; los rechazos no cambian la fila.

## Datos nuevos

Ninguno: sin migración, sin columnas, sin cambio de `.graphqls` ni de la firma de la mutation.

## Contrato y clientes

- Único llamador: desktop (`venta-tarjeta.service.ts#onReabrir`). Ya aplica el mismo doble gate,
  así que un usuario que hoy ve el botón pasa también en el servidor. El rechazo vuelve como
  `GraphQLException("No autorizado: …")` y el diálogo lo muestra con `mensajeDeError`.
- mobile-pwa y mobile no llaman a esta mutation (grep 2026-11-20).
- Sale a las filiales alpha al mergear a `develop` (≤15 min, sin gate humano); después beta (6
  de farmacia) y stable (18 de bodega) con el flujo normal.

## Rollback

Solo código. La versión anterior funciona igual contra la misma base.

## Pruebas de runtime (local, filial propio en 8082, sucursal 24)

- Usuario sin ninguno de los dos roles: la mutation responde «No autorizado» con caja abierta y
  con caja cerrada; el cobro sigue `NO_COMPLETADO`.
- Usuario con solo `VENTA TARJETA COMPLETAR`: reabre en caja abierta; rechazado en caja cerrada.
- Usuario con solo `ANALISIS DE CAJA`: reabre en caja cerrada; rechazado en caja abierta.
- MR (ADMIN): reabre en las dos.
- Por la pantalla: Utilitarios → Conciliación de cupones → «Reabrir» con MR.
Roles por `usuario_role` temporal y cobros `NO_COMPLETADO` sembrados por SQL; se restaura al final.

## Qué queda sin verificar

- Carrera: el estado de la caja se lee antes de `reabrir`, sin lock. Si la caja se cierra en ese
  instante, un usuario con `VENTA TARJETA COMPLETAR` deja un `PENDIENTE` en una caja cerrada (lo
  mismo que podía hacer un clic antes). No se agrega lock.
- El desktop mira el estado de la caja una vez al abrir el diálogo: si se cierra con el diálogo
  abierto, el botón sigue visible y el servidor rechaza. Es el comportamiento esperado.

- La diferencia entre cajas abiertas en las 24 filiales reales: se usa el mismo campo que ya mira
  el desktop.
