package com.franco.dev.utilitarios.print.escpos.barcode;

import com.franco.dev.utilitarios.print.escpos.EscPosConst;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La OLIVETTI PRT80 no interpreta el QR nativo ({@code GS ( k}) e imprime los datos como texto (#259, #282):
 * el QR sale como imagen {@code ESC *}, igual que el de la factura electrónica.
 */
class QRCodeTest {

    private static final String CORTO = "wa.me/595981000000";
    /** Como la seña del cobro con tarjeta: el dato posicional lleva '|'. */
    private static final String SENA =
            "frc-24-VENTA_TARJETA-79370-79370-RegistroVentaTarjetaComponent-1185|7000|1226-1763913036000";
    /** Del largo de una URL de consulta de SIFEN (la más larga que pasa por esta clase). */
    private static final String URL_SIFEN = "https://ekuatia.set.gov.py/consultas/qr?nVersion=150&Id="
            + "01800695631001001000000112023042717159542123&dFeEmiDE=323032332d30342d32375431303a32353a3330"
            + "&dRucRec=80012345&dTotGralOpe=150000&dTotIVA=13636&cItems=3"
            + "&DigestValue=4a6b736b6c3832396b6c66737a6b6c6a6b6c66736b6c6a6b6c736b64666a6b6c&IdCSC=0001"
            + "&cHashQR=8b3a1e5f0c2d4b6a9e7f1d3c5b7a9e1f3d5c7b9a1e3f5d7c9b1a3e5f7d9c1b3a";

    private static String latin1(byte[] b) {
        return new String(b, StandardCharsets.ISO_8859_1);
    }

    private static int ocurrencias(byte[] bytes, byte[] buscado) {
        String texto = latin1(bytes);
        String aguja = latin1(buscado);
        int n = 0;
        for (int i = texto.indexOf(aguja); i >= 0; i = texto.indexOf(aguja, i + 1)) n++;
        return n;
    }

    private static String leer(BufferedImage imagen) throws Exception {
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(imagen)));
        return new QRCodeReader().decode(bitmap).getText();
    }

    @Test
    void noMandaComandosNativosDeQr() {
        // GS ( k: la PRT80 no lo entiende e imprime los datos como texto
        for (String dato : new String[]{CORTO, SENA, URL_SIFEN}) {
            byte[] b = new QRCode().setSize(6).getBytes(dato);
            assertEquals(0, ocurrencias(b, new byte[]{0x1D, '(', 'k'}), dato);
            assertEquals(0, ocurrencias(b, dato.getBytes(StandardCharsets.ISO_8859_1)), "el dato no va como texto");
        }
    }

    @Test
    void mandaLaImagenConEscAsteriscoYLaJustificacionPedida() {
        byte[] b = new QRCode().setSize(5).setJustification(EscPosConst.Justification.Center).getBytes(CORTO);
        assertArrayEquals(new byte[]{0x1B, 'a', 49}, new byte[]{b[0], b[1], b[2]});
        assertTrue(ocurrencias(b, new byte[]{0x1B, '*', 33}) > 0, "sin ESC * 33");
    }

    @Test
    void elQrSeLeeDeVuelta() throws Exception {
        for (String dato : new String[]{CORTO, SENA, URL_SIFEN}) {
            assertEquals(dato, leer(new QRCode().setSize(5).imagen(dato)));
        }
    }

    @Test
    void conservaElTamanoDelModuloCuandoEntra() throws Exception {
        BufferedImage chico = new QRCode().setSize(3).imagen(CORTO);
        BufferedImage grande = new QRCode().setSize(6).imagen(CORTO);
        assertEquals(chico.getWidth() * 2, grande.getWidth());
    }

    @Test
    void nuncaPasaDelAnchoDe58mm() throws Exception {
        BufferedImage imagen = new QRCode().setSize(16).imagen(URL_SIFEN);
        assertTrue(imagen.getWidth() <= QRCode.ANCHO_MAXIMO, "ancho " + imagen.getWidth());
        assertEquals(URL_SIFEN, leer(imagen));
    }

    @Test
    void datoDemasiadoLargoSaleComoTexto() {
        StringBuilder largo = new StringBuilder();
        while (largo.length() < 3000) largo.append("frc-dato-demasiado-largo-");
        String dato = largo.toString();
        byte[] b = new QRCode().setJustification(EscPosConst.Justification.Center).getBytes(dato);
        assertEquals(1, ocurrencias(b, dato.getBytes(StandardCharsets.ISO_8859_1)));
        assertEquals(0, ocurrencias(b, new byte[]{0x1B, '*'}));
    }

    @Test
    void datoVacioONuloNoLanza() {
        assertEquals(0, new QRCode().getBytes("").length);
        assertEquals(0, new QRCode().getBytes(null).length);
    }
}
