# Plan — Política de facturación configurable por sucursal (issue filial #127)

Rama `feature/facturacion-politica-configurable` en **filial**, **central** y **desktop**, todas
desde `origin/develop` (filial `fdc321e`, central `e4cae6e3`, desktop `77b2f81a`).

## Problema (verificado en `origin/develop`)

`VentaGraphQL.saveVenta` (filial, `@Transactional`, línea 187) decide si una venta genera factura
legal según el botón:

| Botón desktop | `ticket` | `facturar` | Hoy |
|---|---|---|---|
| F10 «Finalizar» | false | true | contador `facturaCountDown` |
| «Finalizar + Ticket» / F11 | true | true | **factura e imprime siempre** (bypass) |
| F12 «Factura» manual | – | false | no factura automático |
| Venta a crédito | true | `!facturado` | ver bug `VentaCredito` abajo |

- `facturaCountDown` es un campo de instancia (`VentaGraphQL.java:94`), sin sincronizar, compartido
  por todas las cajas, reiniciado en cada arranque a la property. **Cada filial tiene su propio
  valor**, inyectado por el overlay de `frc-cicd`
  (`runbooks/application-properties-overlay.md:68-70`).
- La facturación silenciosa **también emite DE SIFEN** (`FacturaLegalBuilder:164-167`); solo no imprime.
- Bugs del contador: NPE si `ticket=true` y `facturar=null` (unboxing en la línea 262, tragado por el
  `catch` de la 338); el contador se reinicia aunque la facturación silenciosa falle (332).
- **Bug `VentaCredito` — verificado en el paso 5, fuera de este alcance:** en la rama `ticket`, la
  condición `pdvId != null && facturar` va antes que la de crédito (`VentaGraphQL.java:262` vs
  `:277`), y el desktop manda `facturar = !facturado` en el crédito (`venta-touch.component.ts:1149`,
  `:1361`). Toda venta a crédito con PDV y sin factura manual **no guarda `VentaCredito`**. Va a un
  issue aparte: **filial #133**.

## Decisiones

Del usuario (2026-09-22):

1. «Venta + Ticket» **configurable**: el admin elige si respeta la política o si factura siempre.
2. Política **configurable en 3 repos**, patrón `ConfiguracionVentaTarjeta` (ABM en central, espejo
   replicado `MAIN_TO_ALL` en filial, pantalla en desktop). Reemplaza a la property como fuente.
3. Nivel **por sucursal**, con fila **default global** (`sucursal_id NULL`).
4. Entran los bugs del contador; `VentaCredito` va aparte.

Decisiones del paso 6 → ver final del documento.

## Modelo

`financiero.configuracion_facturacion` (central publica, filial espejo):

| Columna | Tipo | Significado |
|---|---|---|
| `id` | BIGSERIAL PK (también en filial: la usa el apply para UPDATE/DELETE) | |
| `sucursal_id` | BIGINT NULL | NULL = default global; valor = override de esa sucursal |
| `modo` | VARCHAR(20) | `TODAS` · `INTERVALO` · `A_PEDIDO` |
| `ventas_sin_factura` | INT | solo `INTERVALO`: ventas sin factura entre dos facturadas (misma semántica que `facturaCountDown`) |
| `venta_ticket_respeta_politica` | BOOLEAN | false = «Venta + Ticket» factura siempre (hoy); true = decide la política |
| `usuario_id`, `creado_en`, `modificado_en` | | auditoría |

### Semántica en el filial

Dos piezas nuevas, con inyección por constructor para testear sin contexto Spring (Mockito, como
`PurgaImagenesCuponServiceTest`):

- **`ConfiguracionFacturacionLector`** (bean propio) — `resolver()` con
  `@Transactional(propagation = NOT_SUPPORTED, readOnly = true)`, **fuera de la transacción de la
  venta** y llamado **al principio de `saveVenta`**, antes de `saveCobro`. Motivo (auditoría B-1):
  una excepción dentro de la transacción de la venta la deja rollback-only y la venta se pierde
  aunque se la ataje. Precedente del mecanismo: `FacturaLegalBuilder.java:76-94`.
  - Orden: fila de la `sucursalId` propia → fila global → default = property `facturaCountDown`
    (leída en cada llamada), `INTERVALO`, `respeta=false`. Cualquier excepción → default.
  - Filas degeneradas (no hay UNIQUE ni CHECK en el espejo): varias filas para la misma clave → la
    de `modificado_en DESC, id DESC`; `ventas_sin_factura` NULL o negativo → valor de la property;
    `modo` NULL/desconocido → se ignora esa fila y se sigue con la siguiente del orden.
- **`PoliticaFacturacionService`** — decisor puro + contador en memoria, `synchronized` (supuesto:
  **una JVM por filial**, igual que hoy).
  - `decidir(ticket, facturar, pdvId, esCredito, config)` → `FACTURAR_E_IMPRIMIR` ·
    `FACTURAR_SILENCIOSO` · `NO_FACTURAR`. `ticket`/`facturar` nulos se tratan con
    `Boolean.TRUE.equals` (fix NPE).
  - `INTERVALO` replica **exactamente** la cadencia de hoy (auditorías A-5/B-3): con contador > 0
    decrementa **aunque `pdvId` sea null**; con contador 0 y sin `pdvId` se queda en 0; con 0 y
    `pdvId` factura y reinicia a `n`. Si `n` baja por configuración, el contador se recorta a `n`.
  - `TODAS` → factura con `pdvId`; `A_PEDIDO` → no factura automático.
  - «Venta + Ticket» con `respeta=false`: factura siempre y **no toca el contador** (idéntico a hoy).
    Con `respeta=true`: decide la política; si no toca, cae a ticket simple. **Las ventas a crédito
    quedan fuera de `respeta`** (siguen la ruta de hoy) hasta que se arregle el bug `VentaCredito`:
    si no, que se guarde o no el crédito dependería del turno (auditoría B-4).
  - Fallo de facturación: `devolverTurno()` **solo ante `GraphQLException`** (validación previa, sin
    writes). Otra excepción no devuelve el turno (auditorías A-5/B-2: una falla sistemática —timbrado
    vencido, SIFEN caído— no debe reintentarse en cada venta dentro de su transacción).

### Cambios de comportamiento con la tabla vacía (se declaran en el PR del filial)

- `ticket=true, facturar=null, pdvId` presente: hoy NPE tragado (no imprime nada) → ahora ticket simple.
- Falla de facturación silenciosa con `GraphQLException`: hoy se pierde el turno → ahora la próxima
  venta reintenta.
- Nada más: la tabla de verdad del test (abajo) lo garantiza.

## Puertas de facturación automática (regla «la bandera se respeta en TODAS las puertas»)

Verificado en el paso 5: **central no factura** (su `saveVenta` no recibe `facturar`); el único
llamador con facturación es `venta-touch` del desktop contra la filial (`servidor=false`). Mobile
tiene la llamada comentada; la PWA define `SaveVentaGQL` sin usarlo. Nadie cambia de contrato.

| Puerta | Archivo | Tratamiento |
|---|---|---|
| F10 «Finalizar» | `VentaGraphQL.saveVenta` rama `ticket != true` | política |
| «Finalizar + Ticket» / F11 | `VentaGraphQL.saveVenta` rama `ticket == true` | política según `respeta` (no crédito) |
| Delivery → `PARA_ENTREGA` | `DeliveryGraphQL.saveDeliveryEstado:204` | según `respeta` (D1), igual que «Venta + Ticket» |
| F12 factura manual | `saveFacturaLegal`, `vincularFacturaLegalAVenta` | fuera: acción explícita del usuario |
| REST `FacturaLegalController:54` | `FacturaLegalApiService` | fuera: acción explícita |
| Reimpresión | `reimprimirFacturaLegal` | fuera: no crea factura |

## Tabla de datos nuevos (escritor / lector)

| Dato | Escribe | Lee |
|---|---|---|
| `modo`, `ventas_sin_factura`, `venta_ticket_respeta_politica`, `sucursal_id` | central `ConfiguracionFacturacionGraphQL.saveConfiguracionFacturacion` ← desktop `ConfiguracionFacturacionDialogComponent` | filial `ConfiguracionFacturacionLector.resolver()` → `PoliticaFacturacionService.decidir()` ← `VentaGraphQL.saveVenta`; desktop dialog (listado) |
| fila borrada (override quitado) | central `deleteConfiguracionFacturacion` ← desktop dialog | filial: la resolución cae a la global / property |
| `usuario_id`, `modificado_en` | central service al guardar | desktop dialog (columna «Modificado por / en»); filial (desempate de filas) |
| property `facturaCountDown` | overlay `frc-cicd` por filial (sin cambios) | filial `ConfiguracionFacturacionLector` **solo como default** |

## Migraciones

- **filial `V103.1__espejo_configuracion_facturacion.sql`**: `CREATE TABLE IF NOT EXISTS` con las
  mismas columnas y tipos, **PK `id` sí; ningún otro UNIQUE, sin FK, sin CHECK, todo nullable, sin
  seed** (el subscriber no puede ser más estricto que el publisher; precedentes `V78.1`, `V96.5`,
  `V98.5`). Filial tiene `spring.flyway.out-of-order=true` (commit `3c68319`).
- **central `V230.1__configuracion_facturacion.sql`**: `CREATE TABLE` con `NOT NULL DEFAULT`,
  `CHECK (modo IN (...))`, `CHECK (ventas_sin_factura >= 0)`, FK a `empresarial.sucursal` y
  `personas.usuario`, índice único parcial por `COALESCE(sucursal_id, 0)`; `INSERT` en
  `configuraciones.replication_table` como `MAIN_TO_ALL` (precedente `V150.1`). **Sin seed**
  (`copy_data=false`: nunca llegaría; gotchas.md 893-913) y **sin `ALTER PUBLICATION`**.
- Aditivas las dos. Rollback del JAR filial: la tabla se ignora y vuelve la property. Rollback del
  JAR central: las filas ya replicadas **siguen vigentes** → kill switch abajo.
- Enum Java `ModoFacturacion` + `enum` del `.graphqls` en el **mismo commit** (central;
  `SchemaEnumsSincronizadosTest`). El filial guarda `modo` como String y lo interpreta con tolerancia.

## Orden de PRs y despliegue

Tabla nueva `MAIN_TO_ALL` y ninguna `BRANCH_TO_MAIN` en el par → **filial primero** (§3.2, tabla por
dirección; el punto 2 genérico de §3.2 no aplica aquí).

### Alpha (`develop`)

1. **PR filial** → `develop` (sale a las filiales alpha en ≤15 min; con la tabla vacía se comporta
   como hoy salvo lo declarado arriba).
2. **Condición para mergear central:** en **cada** filial alpha,
   `SELECT version FROM flyway_schema_history WHERE version = '103.1'` devuelve fila. Que el PR del
   filial esté mergeado no alcanza (auditoría A-2).
3. **PR central** → `develop` + deploy manual (`gh workflow run Deploy`, instancia alpha;
   `deploy-auto.yml` nunca se dispara). Ojo: con `replication.sync.enabled=true` el scheduler de
   alpha corre a los 2 min del arranque y agrega **todas** las `MAIN_TO_ALL` pendientes, incluidas
   las de otras features: antes del deploy, listar `replication_table` menos
   `pg_publication_tables` y avisar si aparece algo ajeno.
4. Verificar `pg_subscription_rel.srsubstate = 'r'` para la tabla en cada suscripción alpha.
5. **Recién ahí**, configuración: primero los **overrides por sucursal** con el `facturaCountDown`
   vigente de cada filial (inventario desde el overlay), y la global **al final o nunca** (auditoría
   A-3: una global sola pisaría 24 valores ajustados a mano).
6. **PR desktop** → `develop`.

### Beta / farmacia y stable / bodega (promoción)

Por canal, en este orden: `release/beta` (o `master`) del **filial** → esperar la flota (6 farmacia
/ 18 bodega) con la verificación del paso 2 → **central** del canal (deploy con 1 reviewer) →
**paso manual** (los schedulers de replicación están OFF en farmacia por el naming legacy
`filial5_pub`, y el REFRESH del sync solo alcanza a sucursales con IP cargada, gotchas.md:660-672):
`ALTER PUBLICATION central_pub ADD TABLE financiero.configuracion_facturacion` en autocommit y, en
cada filial, `ALTER SUBSCRIPTION … REFRESH PUBLICATION WITH (copy_data = true)`; verificar
`srsubstate='r'` suscripción por suscripción contra la lista de `hosts.md` → overrides → desktop.
Suc. Fiesta (nómade) se verifica al reconectar.

### Kill switch

`DELETE FROM financiero.configuracion_facturacion` en central (se replica): todas las filiales
vuelven a la property en la próxima venta, sin reinicio. Se prueba en alpha en el paso 4 del
despliegue y se documenta en el `CLAUDE.md` del filial.

## Fases

### Filial
- **F1** — `V103.1` + entidad `ConfiguracionFacturacion` (read-only, `Long sucursalId` plano,
  getters tolerantes a NULL) + repositorio.
- **F2** — `ConfiguracionFacturacionLector` + `PoliticaFacturacionService` con tests.
- **F3** — `VentaGraphQL.saveVenta` usa las dos piezas; se elimina el campo `facturaCountDown` del
  resolver; Delivery según D1 (misma bandera `respeta`).
- **F4** — `CLAUDE.md` del filial (facturación: la property pasa a ser default; kill switch).

### Central
- **F1** — `V230.1` + entidad + enum Java + repositorio.
- **F2** — service (upsert por sucursal, validaciones) + input + resolver + `.graphqls`
  (`configuracionesFacturacion`, `saveConfiguracionFacturacion`, `deleteConfiguracionFacturacion`),
  con `TesoreriaSecurityService`: `requireVer()` en la query, `requireGestionar()` en save/delete,
  como primera línea. Test del service.
- **F3** — doc en `docs/manuales-implementacion/`.

### Desktop
- **F1** — modelo, `graphql-query.ts`, `getConfiguracionesFacturacion.ts`,
  `saveConfiguracionFacturacion.ts`, `deleteConfiguracionFacturacion.ts`, service (siempre
  `servidor=true`: se administra en central).
- **F2** — `ConfiguracionFacturacionDialogComponent` en `financiero/factura-legal/` (junto a
  `configuracion-factura-con-venta-dialog`), abierto desde `factura-legal-dashboard` igual que ese
  diálogo; tamaño por `panelClass` + `styles.scss`; tabla centrada; dark.

## Tests

| Pieza | Qué | Revertir el fix y ver fallar |
|---|---|---|
| filial | `PoliticaFacturacionServiceTest` — **tabla de verdad** del decisor contra la lógica vieja trasplantada a un método de test: `ticket` {null,false,true} × `facturar` {null,false,true} × `pdvId` {null, 1} × crédito {sí,no} × contador {0, >0}, con la tabla vacía (default) → mismas decisiones que hoy salvo las 2 filas declaradas | las 2 filas declaradas (NPE, V+T con `respeta=true`) fallan con la lógica vieja; el resto debe coincidir con ella. El resolver en sí no se testea (sin contexto, `@Autowired` de campo) — se testea el decisor extraído |
| filial | `INTERVALO`, `TODAS`, `A_PEDIDO`, recorte de `n`, `devolverTurno` solo con `GraphQLException`, concurrencia (N hilos → cantidad exacta de turnos) | N/A: comportamiento nuevo |
| filial | `ConfiguracionFacturacionLectorTest` — orden sucursal > global > property; excepción → default; filas duplicadas, NULL, negativo, `modo` desconocido | N/A: nuevo |
| central | `ConfiguracionFacturacionServiceTest` (upsert, validaciones, global única); `SchemaEnumsSincronizadosTest` | N/A: feature nueva |
| desktop | `npm run check` | N/A: CI no corre tests |

Batería: filial `./mvnw clean verify -B`, central `./mvnw clean verify -B -DskipFlyway=true`;
veredicto final `gh pr checks`.

## Prueba de runtime (local, no alpha)

Central 8081 + filial 8082 (`-Dspring-boot.run.profiles=dev`) + desktop `ng serve -c web`.
Sin replicación local: la fila se inserta a mano **en la base local del filial** para simular la
llegada por réplica. Casos: sin fila (igual a hoy), `INTERVALO 2` con `respeta=true/false`,
`A_PEDIDO`, `TODAS`, override de sucursal sobre global, venta sin `pdvId`, venta a crédito,
**y lectura que falla** (renombrar la tabla local) → la venta persiste (auditoría B-7).

## Qué queda sin verificar

- Replicación real de la tabla nueva: solo en alpha, post-merge (pasos 2-4 del despliegue).
- Dry-run de `V230.1` / `V103.1` contra copia de base real (paso 10): pendiente de dump.
- SIFEN: si `crearDocumentoElectronico` transmite el DE antes del commit, un rollback posterior deja
  un DE sin venta (preexistente, no lo cambia este trabajo). `TODAS` consume el timbrado más rápido;
  no hay alerta de rango ni de fallas consecutivas (fuera de alcance, candidato a issue).
- El runbook del overlay en `frc-cicd` pasa a describir un **default**: pedido a Gabriel
  (`frc-cicd` es solo lectura en esta máquina).

## Auditoría del plan (paso 5)

| # | Eje | Hallazgo | Qué se hizo |
|---|---|---|---|
| B-1 | B | Leer la config dentro de la tx de `saveVenta` puede tumbar la venta | lector en bean propio `NOT_SUPPORTED`, al inicio |
| B-2 / A-5 | A+B | `devolverTurno` multiplica fallas sistemáticas | solo ante `GraphQLException` |
| B-3 / A-5 | A+B | «solo con `pdvId`» cambiaba la cadencia de hoy | cadencia exacta + tabla de verdad |
| B-4 | B | Bug `VentaCredito` real; `respeta` lo volvería variable | crédito fuera de `respeta`; issue aparte (D2) |
| B-5 | B | Rollback de central no apaga la política | kill switch documentado y probado |
| B-6 | B | Filas replicadas degeneradas | reglas de desempate y tolerancia + tests |
| B-7 | B | Ningún test cubre B-1 | caso de runtime «lectura que falla» |
| B-8 | B | SIFEN / timbrado | anotado en «sin verificar» |
| A-1 | A | Sync no sirve en farmacia/bodega | paso manual por canal |
| A-2 | A | Scheduler alpha se adelanta; filial sin `V103.1` rompe el REFRESH | condición de merge por `flyway_schema_history` + listado previo |
| A-3 | A | Global pisaría el `facturaCountDown` por filial | overrides primero, global al final o nunca; pedido a Gabriel |
| A-4 | A | Orden de promoción implícito | secuencia explícita por canal |
| A-6 | A | PK del espejo ambigua | «PK `id` sí» |
| A-7 | A | Grep de clientes sin registrar | registrado en «Puertas» |
| — | B | La skill local `flyway-migraciones-frc` dice que el filial no tiene `out-of-order`; el código dice `true` (`3c68319`) | gana el código; avisado al usuario |

## Decisiones del paso 6 (usuario, 2026-09-22)

- **D1 — Delivery:** misma bandera `respeta` que «Venta + Ticket». Con `respeta=false` (default)
  factura siempre, como hoy; con `respeta=true` decide la política.
- **D2 — Bug `VentaCredito`:** issue aparte, **filial #133**. Este trabajo deja al crédito fuera de
  `respeta`.
- Plan aprobado. Fase = commit + push de la rama; PR solo después de la prueba del usuario.
