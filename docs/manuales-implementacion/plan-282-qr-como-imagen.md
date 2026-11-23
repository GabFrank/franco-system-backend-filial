# Plan — filial #282 (y central): el QR sale como texto en la OLIVETTI PRT80

Ramas `fix/impresion-qr-como-imagen` en filial (desde `develop` cb35331) y central (desde `develop` 02d17ff4).

## Causa (confirmada)

`QRCode.getBytes` arma el QR con los comandos nativos `GS ( k` (funciones 67, 69, 80 y 81; la 165 ya no va desde
filial #260 / central #642). La PRT80 no los interpreta e imprime los datos como texto.

- Bytes de la seña volcados el 23/11 (`printSenaCupon` a un `OutputStream`): la secuencia es la canónica de Epson,
  sin la 165 y con pL/pH correctos. No hay un byte mal: es la familia de comandos.
- Ejemplo que anda en la misma impresora (Franco, 23/11): el QR de la factura SIFEN. Lo arma zxing como imagen y
  sale con `BitImageWrapper` (`ESC *`, 24 puntos doble densidad) + `BitonalThreshold` (`FacturaLegalGraphQL`).

## Cambio (una fase por repo, mismo código)

Solo `utilitarios/print/escpos/barcode/QRCode.java`, la clase que usan todos los tickets con QR (filial: 8 usos;
central: 14). `getBytes(data)` deja de mandar `GS ( k` y devuelve el QR como imagen:

1. zxing `QRCodeWriter.encode(data, QR_CODE, 0, 0, hints)` → matriz mínima (un píxel por módulo), con
   `ERROR_CORRECTION` según `errorCorrectionLevel` (default M, como hoy) y `MARGIN` 2.
2. Cada módulo pasa a `size` puntos (el mismo `setSize` de hoy: mismo tamaño físico que el QR nativo). Si el ancho
   supera 384 puntos (ancho útil de 58 mm), se baja el módulo hasta que entre: una imagen `ESC *` más ancha que el
   papel sale rota.
3. `new BitImageWrapper().setJustification(justification).getBytes(new EscPosImage(new CoffeeImageImpl(img),
   new BitonalThreshold()))`: lo mismo que la factura.
4. `getBytes` **nunca lanza**: ante cualquier `Exception` (no solo `WriterException`; un dato vacío da
   `IllegalArgumentException`) imprime el dato como texto centrado, o nada si viene vacío o nulo. El QR del
   ticket de venta (`VentaGraphQL`, `wa.me/…`) va antes de los totales y del corte: una excepción ahí dejaba al
   cliente sin papel (hallazgo B1).
5. La imagen se arma con `BufferedImage.setRGB` (sin `Toolkit` ni `Graphics`): anda en servidores sin pantalla.

`setModel` queda sin efecto (zxing genera siempre modelo 2, el default de las impresoras); se conserva para no
romper la API, con comentario.

El texto que sigue al QR no queda con el interlineado del `ESC 3 16` de la imagen: cada `Style` manda `ESC 2`.

## Tests (por repo, en `QRCodeTest`, reemplazan los del #260/#642)

- `noMandaComandosNativosDeQr`: los bytes no contienen `GS ( k`. **Falla con el código viejo.**
- `elQrSeLeeDeVuelta`: la imagen generada se decodifica con zxing `QRCodeReader` y da el dato (uno corto, uno
  como la seña con `|` y el largo de una URL SIFEN).
- `mandaLaImagenConEscAsterisco` y la justificación pedida.
- `nuncaPasaDelAnchoDe58mm`: `size` 16 con dato largo → ancho ≤ 384.
- `datoDemasiadoLargoSaleComoTexto` y `datoVacioONuloNoLanza`.
- Filial: la seña completa a un `OutputStream` sigue con el texto, el `ESC 2` y el corte después del QR.
- Central: `EtiquetaDevolucionServiceTest.elTicketTermicoEsUnoConUnSoloQr` buscaba la función 81 y el dato
  como texto. Pasa a contar un `ESC * 33` por ticket y el identificador una sola vez (hallazgo A1).

El comentario ⚠️ del `feed(1)` de la seña cambia: con imagen, lo que se perdería es el `ESC a` (hallazgo B4).

Paso 9: `./mvnw clean verify -B` en filial y `-DskipFlyway=true` en central (en copia fuera de dev-frc, por jdtls).

## Prueba en papel (Franco mira la térmica)

- Filial: reimprimir la seña 1226 a `frc-ticket` → QR visible y legible con el lector o el teléfono.
- Central: un ticket con QR del central (cierre de caja) a `frc-ticket`.
- Regresión: una venta común (QR `wa.me` si la sucursal tiene delivery) y la factura (sin cambios).
- El QR más largo que pasa por `QRCode`: la URL SIFEN de `FacturaService` (filial) / `FacturaLegalGraphQL`
  (central). Con el tope de 384 baja a módulo 4: confirmar que se lee.

## Auditoría del plan (paso 5)

| # | Hallazgo | Verificación | Qué se hace |
|---|---|---|---|
| A1 | `EtiquetaDevolucionServiceTest` (central) busca la función 81 y el dato como texto | Cierto (líneas 111-120) | Se reescriben esas aserciones |
| A2 | Tope fijo de 384 achica el QR en rollos de 80 mm | Cierto, solo con datos largos | Se acepta: un QR chico se lee; prueba en papel con la URL larga |
| A3 / B2 | `ESC 3 16` de la imagen | El `Style` y `feed` mandan `ESC 2` | Test de la seña completa |
| B1 | Una excepción del QR corta el ticket de venta antes de los totales y el corte | Cierto (`VentaGraphQL` ~1320) | `getBytes` nunca lanza; test con dato vacío/nulo |
| B3 | Headless | AWT ya se usa (logo, QR SIFEN); Boot arranca headless | `setRGB`, sin `Toolkit` |
| B4 | Comentario del `feed(1)` de la seña obsoleto | Cierto | Se actualiza |
| — | Rollback | `Venta.ticketEscpos` es `@Transient`: no queda estado | — |
| — | Desktop (impresión desde el cliente) | Reenvía los bytes en base64 sin parsear | Sin cambios; crecen unos KB |

## Datos nuevos

Ninguno: sin migraciones, sin GraphQL, sin configuración.

## Riesgo y despliegue

- Todos los tickets con QR de las 24 filiales y de los centrales pasan de comando nativo a imagen. Respaldo: el QR
  de la factura electrónica ya sale como imagen en todas las filiales. El ticket crece unos KB por QR.
- Filial: el merge a `develop` sale a las filiales alpha en ≤ 15 min. Central: deploy manual. Sin orden entre los
  dos (no hay base ni réplica de por medio).

## Queda sin verificar

- Impresoras de 58 mm de las filiales: se cubre por el tope de 384 puntos y por la factura, no con papel.
