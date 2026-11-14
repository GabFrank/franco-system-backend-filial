package com.franco.dev.utilitarios.print.escpos.barcode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QRCodeTest {

    /** Función 165 (elegir modelo): GS ( k 4 0 49 65 n1 n2. Va justo después de ESC a n. */
    private static int modeloEnviado(QRCode qr) {
        byte[] b = qr.getBytes("wa.me/595981000000");
        int i = 3;
        assertEquals(0x1D, b[i]);
        assertEquals('(', b[i + 1]);
        assertEquals('k', b[i + 2]);
        assertEquals(65, b[i + 6]);
        return b[i + 7];
    }

    @Test
    void porDefectoMandaElModelo2() {
        assertEquals(50, modeloEnviado(new QRCode()));
    }

    @Test
    void elModelo1EsEl49() {
        assertEquals(49, modeloEnviado(new QRCode().setModel(QRCode.QRModel._1)));
    }

    @Test
    void ningunModeloMandaUnValorFueraDelEstandar() {
        // 48 no es un modelo: una impresora estricta (OLIVETTI PRT80) lo imprime como «0» (#259)
        for (QRCode.QRModel m : QRCode.QRModel.values()) {
            int n1 = modeloEnviado(new QRCode().setModel(m));
            assertTrue(n1 == 49 || n1 == 50 || n1 == 51, m + " manda " + n1);
        }
    }
}
