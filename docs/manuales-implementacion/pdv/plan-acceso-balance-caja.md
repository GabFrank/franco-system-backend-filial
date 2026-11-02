# Plan — control de acceso a la caja (filial)

Rama: `fix/caja-acceso-balance` (desde `develop`). Pieza: **filial**. Sin migración.

## Regla

Las operaciones sobre una caja las hace **su dueño** (`pdv_caja.usuario` = usuario del token) o
quien tenga **`ANALISIS DE CAJA`**. ADMIN pasa siempre (`RolSesionService`).

| Punto | Control |
|---|---|
| `balancePorCajaId`, `imprimirBalance`, `ticketEscpos(BALANCE)` | dueño o rol |
| campo `balance` de `PdvCaja` (`PdvCajaResolver`, `CajaResolver`) | dueño o rol; si no, `null` (no error, para que el proxy del central no descarte la caja) |
| `balancePorFecha` | rol |
| `savePdvCaja` sobre una caja existente | dueño o rol; cambiarle el usuario, rol |
| `transferirCaja` | dueño, rol o el usuario destino (el desktop inicia sesión con el destino antes de transferir) |
| `saveConteo` | dueño o rol |
| `editarConteoCaja` | ADMIN de la sesión |

Los controles van en la capa GraphQL; `PdvCajaService` no cambia.

## Clientes (mapeo contra `develop` de desktop, central, PWA y mobile)

Todo lo que llega al filial opera sobre la caja del usuario logueado: PDV (retiro, gasto, cerrar,
imprimir, «Últimas cajas», transferir), y PWA/mobile a través del central, que reenvía el token.
Las pantallas que revisan cajas ajenas (lista de cajas, análisis de diferencias) consultan al
central. `savePdvCaja` contra el filial solo se usa para cajas nuevas.

## Fases

1. `RolSesionService`: `tieneRol`, `esDuenioOTieneRol` (dueño primero, sin consultar roles) y
   `exigirDuenioORol`, cada uno con su `@Transactional(NOT_SUPPORTED)`. Los puntos de la tabla.
2. `transferirCaja` con la sesión del destino; `saveConteo` y `editarConteoCaja`.

Tests (`RolSesionServiceTest`, `PdvCajaAccesoTest`, `TicketEscposGraphQLTest`,
`ConteoGraphQLSaveConteoTest`): dueño, no dueño con y sin rol, ADMIN, sin sesión, usuario
inexistente, caja sin dueño, destino de una transferencia, y que un rechazo no calcule, imprima ni
guarde. Fallan con el código anterior.

## Pruebas por pantalla

Filial de la rama en el 8082, desktop en 127.0.0.1:4200, un cajero de prueba con `VENTA TOUCH`:
abrir caja, retiro, cerrar, reimprimir y transferir funcionan igual; las operaciones sobre la caja
de otro usuario se rechazan; con `ANALISIS DE CAJA` pasan.

## Despliegue y vuelta atrás

Sin migración ni datos. `develop` → alpha; `release/beta` → farmacia; `master` → bodega, solos en
15 min. Vuelta atrás: revert + release, o pasar la release a prerelease (`check-update.sh` vuelve a
la anterior en ≤15 min).

Si un usuario no se encuentra en la filial (réplica atrasada, nickname cambiado con la sesión
abierta), sus operaciones de caja se rechazan: volver a iniciar sesión, o esperar la réplica.

## Resultado de las pruebas (2026-10-27, filial local, sucursal 24)

- Cajero con solo `VENTA TOUCH`, por pantalla: abrir caja, venta, retiro (el saldo se verifica),
  transferir la caja a otro cajero (con su usuario y clave), y el destino la cierra y se imprime el
  cierre con diferencia 0. Sin rechazos.
- El mismo cajero sobre una caja de otro usuario: balance, impresión, ticket, transferencia,
  edición y conteos responden «No autorizado» y la caja no cambia; el campo `balance` llega `null`.
- Con `ANALISIS DE CAJA` la caja ajena pasa; ADMIN pasa siempre.
- Tests: 628, sin fallas (`./mvnw clean verify`).
- No probado de punta a punta: la reimpresión desde PWA/mobile pasando por el central (el central
  reenvía el token del usuario; el control en el filial es el mismo).
