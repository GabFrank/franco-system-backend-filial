# Convenio: forma de pago única y sin descuento

Rama: `feature/pdv-convenio-sin-mezcla` en filial y desktop, desde `develop` (2026-11-24).

## Reglas pedidas

1. Una venta por **convenio** se paga **solo** por convenio. No se combina con efectivo, tarjeta ni
   ninguna otra forma de pago, sin importar el monto ni el orden en que se cargó (convenio primero o
   último).
2. Una venta por convenio **no lleva descuento**: ni el de F9, ni el precargado por producto
   (`data.descuento` → línea `descuento=true`).

## Cómo funciona hoy (análisis, paso 3)

- El único cobro por convenio es `pago-touch` del desktop: F6 / botón «Convenio» / elegir CONVENIO
  en el combo → `onConvenioClick()` abre `AddVentaCreditoDialog` por **el saldo que queda** y
  finaliza en el acto. El total del diálogo no es editable.
  - Por eso el caso «convenio primero y después otra forma» no se puede armar desde la pantalla;
    sí «efectivo/tarjeta primero y convenio por el resto», y «descuento (F9 o precargado) y después
    convenio». Eso es lo que hoy pasa sin freno.
- Delivery: sus líneas se guardan una a una (`saveCobroDetalle`) y el cierre va por
  `saveVentaDelivery` con la lista completa (las ya guardadas vuelven con su id).
- El filial guarda: `VentaGraphQL.saveVenta` (venta normal) y `saveVentaDelivery`. «Es convenio» =
  `ventaCreditoInput != null` (el desktop solo lo manda desde convenio; `onFirmaClick` no tiene
  llamador y no existe la forma de pago FIRMA: `financiero.forma_pago` = EFECTIVO, TARJETA,
  CONVENIO, TRANSFERENCIA, CHEQUE).
- La PWA solo **consulta** el convenio (mis finanzas): N/A para mobile-pwa porque no cobra.
  N/A para central porque el PDV guarda contra el filial (`onSaveVenta(..., servidor=false)`).
  N/A para mobile (Android) porque no cobra ventas por convenio.

## Fases

### Fase 1 — filial: el servidor rechaza la mezcla y el descuento

- `VentaGraphQL`: método estático `exigirConvenioSolo(List<CobroDetalleInput>, Function<Long,String>
  descripcionFormaPago)` que, para un cobro con crédito, lanza `GraphQLException` si alguna línea
  tiene `descuento=true` («No se puede aplicar descuento a una venta por convenio.») o una forma de
  pago distinta de CONVENIO («El convenio solo se puede usar como única forma de pago.»).
- `saveVenta`: se llama antes de reservar la huella (antes del primer write), solo si
  `ventaCreditoInput != null`.
- `saveVentaDelivery`: antes del `try` (que se come los mensajes), sobre las líneas ya guardadas del
  cobro **más** las entrantes, solo si `ventaCreditoInput != null`.
- Tests: `VentaGraphQLConvenioSoloTest` (JUnit, sin Spring): solo convenio pasa; convenio +
  efectivo, efectivo + convenio, convenio + descuento, línea con forma de pago desconocida →
  rechazan; sin crédito no se valida.

### Fase 2 — desktop: el cajero no puede armar el cobro

- `pago-touch.onConvenioClick()`: si `cobroDetalleList` ya tiene líneas, no abre el diálogo y avisa:
  - con alguna línea de descuento: «No se puede aplicar descuento a una venta por convenio.»
  - si no: «El convenio solo se puede usar como única forma de pago.» (textos pedidos por Franco, 24/11)
  - y vuelve la forma de pago a la primera de la lista (como al cancelar el diálogo).
- Cubre F6, el botón y el combo (los tres pasan por `onConvenioClick`). El orden inverso (convenio y
  después otra) ya no se puede armar porque el convenio finaliza al confirmarse.
- Test: spec de `pago-touch` (Karma con tsconfig acotado, ver memoria): con una línea de efectivo o
  de descuento no abre el diálogo; sin líneas sí.

## Datos nuevos

Ninguno: no hay columnas, claves ni configuración nueva. N/A migraciones.

## Orden de despliegue

No hay contrato nuevo, pero sí un orden. El filial llega solo a las filiales; el desktop, cuando cada
PC acepta actualizar. Con el filial nuevo y un desktop viejo, el cobro mezclado se rechaza al guardar
con el mensaje del filial:
- venta común: queda en pantalla para volver a cobrar;
- delivery: se sale del delivery y la línea de efectivo queda guardada. Hay que reabrirlo desde la
  lista, borrar la línea y cobrar por convenio.

Orden (Franco, 24/11): se hacen las dos fases; el PR del **filial se mergea recién cuando el desktop
esté en stable**. Saldo de convenio insuficiente: no se completa con otra forma (es la regla).

## Pruebas de runtime (paso 9)

Filial propio en 8082 (sucursal 24, SIFEN apagado) + desktop web con `MR`:
- efectivo parcial → F6: aviso, no abre el diálogo; borrar la línea → F6 abre y la venta se guarda.
- F9 descuento → F6: aviso de descuento; con descuento precargado (delivery con ítem con descuento)
  idem.
- convenio solo: se guarda como siempre.
- delivery: línea de efectivo guardada → F6 bloquea; borrarla → convenio cierra el delivery.
- API directa al filial: `saveVenta` con crédito + línea efectivo, y con línea descuento → error con
  el mensaje; `saveVentaDelivery` idem.

## Auditoría del plan (paso 5) y qué se hizo

| # | Eje | Hallazgo | Decisión |
|---|---|---|---|
| A1 | A | Con desktop viejo + filial nuevo, el **delivery** no queda en pantalla: `venta-touch` siempre sale del delivery (l.1769) y la línea de efectivo ya quedó guardada. | Se corrige la sección de despliegue. Procedimiento: reabrir el delivery, borrar la línea y cobrar por convenio. Se prueba en runtime con el desktop de `develop` contra el filial nuevo. |
| A2 | A | «Desktop primero» no se garantiza: el filial llega solo a las filiales en 15 min, el desktop depende del «Más tarde». | Filial se mergea después de que el desktop llegue a stable (Franco, 24/11). |
| A3/B3 | A+B | «Es convenio» no coincide: `saveVenta` guarda crédito solo con `ventaCreditoInput` **y** las cuotas (l.350); `saveVentaDelivery`, con el input solo. | Cada camino valida con **su** condición de guardado. |
| A3 | A | `saveVentaDelivery` descarta descuentos duplicados con `continue` y las guardadas son entidades. | El validador recibe formas de pago + «hay descuento» (no `CobroDetalleInput`); en delivery se arma con las entrantes crudas **y** las guardadas, antes del `try`. Test del caso repetido. |
| B1 | B | El descuento precargado bloquea el convenio; borrarlo sube el cobro. | Es la regla 2. El botón de borrar se muestra en toda línea, incluida la de descuento (`pago-touch.component.html:258`). Hoy `valorDescuento` por ítem no se puede cargar en el PDV (`editItem` lo deriva a F12), solo llega en datos viejos. |
| B2 | B | Delivery con línea en vuelo: `onDeleteItem` no borra (l.1279). | Igual que hoy: reabrir el delivery desde la lista. Entra en la prueba de runtime. |
| — | A | PWA y mobile tienen `saveVenta` en `graphql-query`, pero sin llamador (en mobile, `onSaveVenta` comentado en `venta.service.ts:47`). | Se confirma N/A. |
| — | B | El spec de Karma no corre en ningún gate. | Se corre a mano; la prueba real es por pantalla. |

## Pruebas de runtime hechas (24/11)

Filial de la rama en 8082 (sucursal 24, SIFEN apagado), central de `develop` en 8081 (sin migrar nada),
desktop de la rama en `127.0.0.1:4200`, usuario MR, caja 1185:
- Efectivo 1.500 → botón Convenio y F6: aviso de forma de pago única, no abre el diálogo.
- Borrar el efectivo, F9 10 % (500) → Convenio: aviso de descuento.
- Borrar el descuento → Convenio por 6.500, firma → venta 79373 CONCLUIDA con una sola línea CONVENIO.
- `saveVenta` por API: efectivo+convenio, convenio+tarjeta y descuento+convenio → los tres rechazados
  con su mensaje; la última venta de la caja siguió siendo la 79373.
- Delivery 8067: efectivo 2.000 guardado → Convenio bloqueado; `saveVentaDelivery` por API (con la
  línea reenviada y sin reenviarla) → rechazado; borrar la línea → convenio por 11.500, delivery
  CONCLUIDO con una sola línea CONVENIO.
- No probado por pantalla: el descuento precargado por producto (`data.descuento`), porque el PDV no
  deja cargar descuento por ítem. Genera la misma línea `descuento=true` que F9: lo cubren la prueba
  de F9 y el spec.

## Descuento con CONVENIO elegido (prueba pedida por Franco, 24/11)

- Con el diálogo de convenio abierto, F9 no hace nada (los atajos están bloqueados).
- Con CONVENIO elegido en la forma de pago (sin el diálogo), el botón Descuento / F9 **abría** el
  diálogo de descuento. Al aceptarlo, `addCobroDetalle` abría el convenio sin registrar el descuento y
  `isDescuento` quedaba en true: al cancelar el convenio, el siguiente Enter guardó «500 Descuento».
- Arreglo: `onDescuento` corta con «No se puede aplicar descuento a una venta por convenio.» si la forma
  elegida es CONVENIO. Probado: botón y F9 avisan, sin diálogo, sin marca y sin líneas; después el
  convenio cerró la venta 79376 con una sola línea CONVENIO por 6.000. Spec agregado.

## Auditoría del diff (paso 8)

Ejes fijos 1–3; condicionales A y B no aplican (sin migraciones ni maquinaria de release).
0 bloqueantes, 0 mayores. Menores, sin cambio de código:
- `findByCobroId` no filtra por sucursal: mismo patrón que la línea que ya estaba debajo.
- Una línea CONVENIO **sin** `ventaCreditoInput` no se valida: ningún cliente lo arma; sería otro defecto
  (convenio cobrado sin crédito registrado).
- En delivery, un F6 antes de que cargue el replay de líneas abre el diálogo: lo frena el filial al
  cerrar (igual que con un desktop viejo).

## Sin verificar / riesgos

- Un funcionario con saldo de convenio menor al total ya no puede completar con efectivo: es la regla
  pedida, pero cambia la operación en caja.
- Si un cliente viejo manda una línea CONVENIO con aumento: se acepta (no es descuento ni otra forma
  de pago). El aumento solo sale con saldo negativo, que el convenio no deja.
