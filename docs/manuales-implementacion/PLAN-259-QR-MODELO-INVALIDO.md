# Plan #259 — el QR de los tickets sale con un «0» arriba

Issue: https://github.com/GabFrank/franco-system-backend-filial/issues/259
Rama: `fix/impresion-qr-modelo` (desde `develop`)

## Problema

`utilitarios/print/escpos/barcode/QRCode.java` manda en la función 165 (`GS ( k 4 0 49 65 n1 n2`, elegir modelo)
`n1 = 48`, que no es un modelo válido: ESC/POS admite 49 (modelo 1) y 50 (modelo 2), y algunas impresoras también 51
(micro). La OLIVETTI PRT80 corta el comando y procesa el 48 como texto: imprime «0». La Xprinter lo ignoraba.
Prueba en `frc-ticket` (14/11): 48 → «0»; 49 y 50 → sin «0».

## Cambio (una fase)

| Archivo | Cambio |
|---|---|
| `QRCode.java` | `QRModel` pasa a `_1(49)`, `_2_Default(50)`; el constructor usa `_2_Default`. Javadoc del enum con los valores del estándar |
| `src/test/java/.../utilitarios/print/escpos/barcode/QRCodeTest.java` (nuevo) | lee los bytes de `getBytes` y verifica: por defecto la función 65 lleva `n1 = 50`; `setModel(_1)` lleva 49; ningún modelo del enum manda 48 |

Llamadores (todos con `new QRCode()`, ninguno llama a `setModel`): `VentaGraphQL` (QR de delivery del ticket de venta),
`MovimientoPrintService`, `FacturaService`, `ImpresionService` (cierre/balance y otros). No cambian: toman el nuevo default.

Por qué modelo 2 y no 1: es el modelo que las impresoras dibujaban cuando descartaban el comando inválido (su default),
y el que soportan todas las térmicas ESC/POS. El QR que ve el cliente no cambia.

## Tabla de datos nuevos

N/A: no nace ningún campo, columna ni clave.

## Persistencia y migraciones

N/A: no toca la base.

## Tests

- `QRCodeTest` (nuevo). Con el código viejo tiene que fallar (n1 = 48): se revierte el fix y se comprueba.
- `./mvnw clean verify -B` completo.

## Prueba de runtime

- Filial propio de la rama (jar) en el 8092, sucursal 24 contra `general@5551`, `sifen.enabled=false`.
- Venta por la pantalla del PDV (escritorio web apuntado al 8092) con «Finalizar + Ticket», imprimiendo en `frc-ticket`,
  con `nro_delivery` de la sucursal 24 con 595 (si no lo tiene, se pone por SQL y se restaura): el QR de delivery sale sin «0».
  Franco confirma el papel.
- Mismo ticket con el jar de `develop` (alpha, 8080) como control: sale con «0».
- Ticket de cierre de una caja de la 24 (lleva el QR `frc-24-PDVCAJA-…` de `ImpresionService.escribirQr`, `setSize(6)`,
  el que se escanea en caja mayor): sin «0» y **escaneado** con un teléfono, devolviendo el texto esperado. Lo mismo con el QR de delivery.

## Rollback

Revertir el commit en `develop` (o `master` si ya llegó): el auto-update vuelve al 48 en ≤15 min. No hay estado persistente
ni configuración por filial; vuelve a aparecer el «0» en la Olivetti.

## Auditoría del plan (paso 5)

- Eje A (contrato): 6 llamadores con `new QRCode()`, ninguno usa `setModel`; nadie arma `GS ( k` de QR a mano (solo `PDF417`,
  otro símbolo); `Venta.ticketEscpos` es transitorio y el desktop reenvía los bytes sin leerlos. `ImpresionServiceQrTest` mira
  el dato, no el byte del modelo: `QRCodeTest` es la única protección. Riesgo mayor fuera de alcance: el central tiene el
  mismo bug (`QRCode.java:21`). → Se le pregunta a Franco si abre issue en el central.
- Eje B (reversibilidad): sin estado persistente, reversible por revert. Riesgos: la prueba cubre una sola impresora y el QR
  de caja mayor no estaba en la prueba; un QR ilegible no deja log. → Se suma el ticket de cierre escaneado; antes de beta,
  imprimir y escanear en una térmica que no sea la Olivetti.

## Fuera de alcance

- El central tiene la misma clase con el mismo valor (retiros, balance, devoluciones, ventas a crédito). Va en su propio
  issue/PR si Franco lo pide.

## Sin verificar

- Qué ticket de las 16:35 del 13/11 trajo el «0»: los trabajos de esa franja eran facturas sin QR y un pagaré.
- Otras marcas de impresora de las filiales: se asume que aceptan el modelo 2 (es el estándar y su default).
