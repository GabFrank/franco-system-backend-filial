package com.franco.dev.utilitarios.print.escpos.barcode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QRCodeTest {

    private static final String DATO = "wa.me/595981000000";

    /** Posición del comando GS ( k 4 0 49 65 (función 165, elegir modelo), o -1 si no está. */
    private static int funcionModelo(byte[] b) {
        for (int i = 0; i + 7 < b.length; i++) {
            if (b[i] == 0x1D && b[i + 1] == '(' && b[i + 2] == 'k' && b[i + 3] == 4 && b[i + 4] == 0
                    && b[i + 5] == 49 && b[i + 6] == 65) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void porDefectoNoMandaLaFuncionDeModelo() {
        // La OLIVETTI PRT80 no la entiende e imprime n1 como texto: «0» con 48, «2» con 50 (#259)
        assertEquals(-1, funcionModelo(new QRCode().getBytes(DATO)));
    }

    @Test
    void conModeloExplicitoLaMandaConUnValorDelEstandar() {
        for (QRCode.QRModel m : QRCode.QRModel.values()) {
            byte[] b = new QRCode().setModel(m).getBytes(DATO);
            int i = funcionModelo(b);
            assertTrue(i >= 0, m + " no manda la función 165");
            int n1 = b[i + 7];
            assertTrue(n1 == 49 || n1 == 50, m + " manda " + n1);
        }
    }

    @Test
    void elDatoSigueEnLosBytes() {
        String bytes = new String(new QRCode().getBytes(DATO), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(bytes.contains(DATO));
        assertFalse(bytes.isEmpty());
    }
}
