# Plan — la captura por foto del cupón exige una caja abierta que el sistema nunca marca

Rama: `fix/venta-tarjeta-caja-abierta` en **filial** y **desktop** (un PR por repo).
Fecha: 2026-09-24. Estado: plan auditado (paso 5, dos ejes) y **aprobado por Gabriel el 2026-09-24**.

## Qué pasó

Primera prueba real del flujo de venta con tarjeta en farmacia (filial 1, BANCARD L1, venta_tarjeta 6,
caja 5459 abierta desde las 18:11 UTC). Al pedir la foto del cupón, el filial respondió
`la caja no esta abierta` y el desktop lo mostró como «No se pudo abrir la captura. Revisá que el
servidor de la sucursal esté funcionando». Pasó igual en el cobro (pago-touch) y al completar el
pendiente desde utilitarios.

## Causa (verificada)

- `CapturaCuponService.crear` (línea 151) y `procesar` (línea 217) exigen
  `pdv_caja.estado = EN_PROCESO`.
- **Nadie escribe `pdv_caja.estado`**: ni el desktop (`PdvCajaEstado.*` no se asigna en ningún
  `.ts`), ni el filial, ni el central (`grep setEstado|PdvCajaEstado.` sobre `src/main`). Es una
  columna del esquema inicial (`V0__initial_schema.sql`) que quedó sin escritor.
- Datos reales, solo lectura, 2026-09-24:

  | base | cajas | `estado` NULL | capturas alguna vez |
  |---|---|---|---|
  | filial 1 farmacia | 5.366 | 5.366 | 0 |
  | filial alpha (mauro) | 2.379 | 2.379 | 0 |

- El sistema define «caja abierta» por **`activo`**: `PdvCajaService.save` marca el maletín abierto
  con `activo = true` y rechaza «Ya existe una caja abierta» con `findByUsuarioIdAndActivo(…, true)`.
  En filial 1, `activo = true` ⇔ `fecha_cierre IS NULL` sin excepciones (11 abiertas, 5.355
  cerradas con `activo = false` y fecha de cierre).
- El mismo supuesto está en el desktop: `ventas-tarjeta-caja-dialog.consultarEstadoDeLaCaja` pone
  `cajaAbierta = caja.estado === 'EN_PROCESO'`, así que **siempre** da `false` y el gate de
  «Reabrir» usa el rol de caja cerrada aun con la caja abierta.

Nunca se vio porque la captura por foto no se ejercitó jamás contra una caja real (0 capturas en
alpha).

## Fases

### Fase 1 — filial: criterio único de caja abierta

- `CapturaCuponService`: método estático `cajaAbierta(PdvCaja)` =
  `caja != null && Boolean.TRUE.equals(caja.getActivo())`. **Decidido con Gabriel el 2026-09-24**:
  `activo = true` es el criterio, el mismo que usa `PdvCajaService` para el maletín y para
  «Ya existe una caja abierta». Sin condiciones extra de fechas.
  Los dos chequeos (`crear` y `procesar`) pasan a usarlo. `estado` deja de leerse.
- Mensaje de `crear`: `la caja no esta abierta` → `la caja de esta venta no esta abierta: el cupon
  se puede cargar a mano`. Es lo que el cajero lee en el diálogo desde la fase 2.
- **Tests** (`CapturaCuponCajaAbiertaTest`, JUnit 5, sin contexto):
  1. caja activa, sin cierre, `estado` NULL → abierta (el caso real; **tiene que fallar con el
     código viejo**: se corre primero contra la implementación basada en `estado`).
  2. caja con `activo = false` y fecha de cierre → cerrada.
  3. `activo = null` → cerrada.
  4. caja `null` → cerrada.
  5. `estado = EN_PROCESO` con `activo = false` → cerrada (`estado` ya no decide).
- Gate: `./mvnw clean verify -B`.

### Fase 2 — desktop: mismo criterio y el motivo real en pantalla

- Nuevo `caja-abierta.ts` (junto a `mensaje-error.ts`, en `venta-tarjeta/qr-pos/`) con
  `cajaEstaAbierta(caja)` = `caja?.activo === true`.
  `ventas-tarjeta-caja-dialog.consultarEstadoDeLaCaja` lo usa. `cajaSimpleQuery` ya trae `activo`
  y `fechaCierre`: no cambia ninguna query.
- `escanear-cupon-dialog` (línea 468) y `registrar-venta-tarjeta-dialog` (línea 606): el error de
  `onCrear` pasa por `mensajeDeError(err, <texto actual>)`. El texto genérico queda solo como
  respaldo cuando el servidor de verdad no responde.
- **Tests**: `caja-abierta.spec.ts` con los mismos casos de la fase 1.
- Gate: `npm run check` (AOT), leído del log.

## Tabla de datos nuevos

Ninguno. No hay columnas, claves ni campos GraphQL nuevos. Se **deja de leer** `pdv_caja.estado`
en los tres lugares donde se leía; la columna queda como está (sin escritor ni lector en este flujo).

## Migraciones

Ninguna. N/A para filial y desktop porque no se toca esquema [ev: este plan, tabla de datos nuevos].

## Orden de despliegue y canales

- Los dos PRs son independientes (no hay contrato nuevo): el desktop nuevo contra un filial viejo
  sigue recibiendo el rechazo, pero ahora lo muestra con su texto; el filial nuevo contra un desktop
  viejo ya permite la foto.
- develop → **alpha**: el filial llega solo a mauro en ≤15 min; el desktop, por su release alpha y
  Deploy Web alpha.
- **Prueba en alpha antes de farmacia**: cobro con TARJETA en una caja abierta, captura por foto
  del cupón hasta el final; el pendiente desde utilitarios; y **«Reabrir» con un usuario que tenga
  `VENTA_TARJETA_COMPLETAR` y no `ANALISIS_DE_CAJA`** (ver abajo).
- **Permiso que se activa (eje A).** Hoy `cajaAbierta` da siempre `false`, así que «Reabrir» exige
  siempre `ANALISIS_DE_CAJA` (supervisor). Con el fix, en caja abierta alcanza con
  `VENTA_TARJETA_COMPLETAR` (cajero), como el código ya pretendía. El backend
  (`VentaTarjetaService.reabrir`) no chequea rol: el único gate es este del desktop. **Confirmado
  por Gabriel el 2026-09-24**: el cajero puede reabrir su propio cobro en su caja abierta.
- **Farmacia (release/beta)**: después de la prueba en alpha. Hoy `develop` y `release/beta` tienen
  los mismos archivos en los tres repos (`git diff` vacío, 2026-09-24). Regla: al promover se vuelve
  a comparar; si siguen iguales salvo este fix, se promueve `develop`; si entró otra cosa, se lleva
  solo este fix por cherry-pick.
- **Antes de habilitar en farmacia**: fijar `dias_retencion_imagenes` (hoy NULL = no purgar nunca).
  Las fotos van al mismo disco que la base de la filial. Las **filas** de `captura_cupon` no tienen
  retención; solo las imágenes (`PurgaImagenesCuponService`).

## Rollback

Revertir el merge. Sin datos ni esquema de por medio, el revert deja el código como hoy (la captura
por foto vuelve a no funcionar). Quedan las filas y fotos de las capturas hechas mientras tanto
(`captura_cupon` es local del filial, no replica), y una captura en vuelo en el instante del revert
falla con el mensaje viejo.

## Auditoría del plan (paso 5)

| Eje | Hallazgo | Severidad | Qué se hizo |
|---|---|---|---|
| A | El plan decía que el central no decide con `estado`: falso, dos queries filtran por él | alta | Corregido en «Qué queda sin verificar»; issue aparte, fuera de alcance |
| A | «Reabrir» en caja abierta pasa del rol supervisor al de cajero; el backend no chequea rol | media | Confirmado por Gabriel; caso de prueba en alpha |
| A | Invariante `activo ⇔ sin cierre` medido solo en filial 1 | baja | Gabriel decidió `activo = true` como criterio único; queda como dato |
| A | Modo web sin captura (LAN-only) | baja | Documentado como límite previo |
| A | Cambio de texto del error: ¿alguien lo compara? | — | Verificado que no (`captura.html` ramifica por status HTTP) |
| B | Un merge a develop llega a alpha sin gate; qué pasa si entra otra cosa antes de promover | alta | Regla de promoción: comparar ramas y cherry-pick si difieren |
| B | Imágenes sin retención configurada; filas de `captura_cupon` sin limpieza | media | Paso previo a habilitar en farmacia |
| B | Ventana caja-se-cierra-durante-OCR | baja | Documentada y aceptada |
| B | Rollback con capturas en vuelo | baja | Documentado |
| B | Replicación: `captura_cupon` local; `pdv_caja` la escribe el filial | — | Sin riesgo: el filial lee su propio dato |

## Qué queda sin verificar

- Que la captura completa (QR → teléfono → OCR → campos) funcione de punta a punta: nunca corrió.
  Se verifica con la prueba en alpha de arriba. Es posible que aparezca el siguiente defecto recién
  ahí.
- **Otras lecturas de `pdv_caja.estado`, fuera de alcance.** Corrección de la auditoría (eje A): no es
  cierto que el central solo declare el enum. Filtran por `estado`, y con un valor no nulo devuelven
  0 filas siempre: `findAllWithFilters` del filial (`PdvCajaRepository.java:60`, resolver
  `cajasWithFilters`) y `findAllForAnalisisDiferenciasNative` del central (`PdvCajaRepository.java:102,118`).
  Hoy no hacen daño porque ningún cliente manda un estado no nulo (`list-caja` tiene el control sin
  `<mat-select>`; `analisis-diferencia` nunca produce un valor del enum). Se abre un issue; no se
  arreglan acá. La PWA solo lo etiqueta (`estado-registry.ts:71`).
- **Cajas viejas que siguen `activo = true`.** En filial 1 hay 5 de las 11: aperturas del 2025-08-19,
  2025-10-31, 2026-07-16, 2026-08-26 y la 3439 sin apertura. Son la última caja de su maletín. Con
  `activo` como criterio, un pendiente de esas cajas se puede completar por foto; aceptado por
  Gabriel: es la misma caja de la venta y no abre nada que la carga manual no permita ya.
- **El invariante `activo ⇔ sin cierre` solo se midió en filial 1 farmacia** (alpha no respondió
  por SSH al auditar). Con el criterio decidido (`activo`) ya no es condición del fix: queda como
  dato.
- **Modo web (`isLocal:false`)**: la captura por foto es solo LAN (el teléfono tiene que alcanzar al
  filial). Límite previo del módulo, no cambia con este fix.
- **Ventanas residuales, aceptadas.** (a) `procesar` chequea la caja antes del OCR (~4 s): una caja que
  se cierra justo en ese intervalo deja una captura LISTO; la red es `VentaTarjetaService.completar`,
  que solo completa PENDIENTE, y el cierre marca NO_COMPLETADO antes de poner `activo = false`.
  (b) Una captura en vuelo durante un rollback vuelve a fallar con el mensaje viejo.
