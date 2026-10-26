package com.franco.dev.utilitarios.print;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/** Texto legible de un ticket ESC/POS, para afirmar en los tests lo que sale en el papel. */
public final class TicketTexto {

    /** El estilo que escpos-coffee manda antes de cada texto: no se ve en el papel. */
    private static final String ESTILO =
            "\u001bM0\u001bE\u0000\u001d!\u0000\u001b-0\u001ba0\u001b2\u001dB\u0000";

    private TicketTexto() {
    }

    public static String plano(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1).replace(ESTILO, "");
    }

    /** Las 4 lineas de la tabla de totales: encabezado y Gs., Rs., Us. */
    public static List<String> totales(byte[] bytes) {
        String texto = plano(bytes);
        int desde = texto.indexOf("------------Totales-------------\n");
        if (desde < 0) {
            throw new AssertionError("el ticket no tiene tabla de totales");
        }
        String[] lineas = texto.substring(desde).split("\n", 6);
        return Arrays.asList(lineas[1], lineas[2], lineas[3], lineas[4]);
    }
}
