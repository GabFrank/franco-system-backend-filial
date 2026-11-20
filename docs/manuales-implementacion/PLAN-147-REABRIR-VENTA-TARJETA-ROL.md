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
