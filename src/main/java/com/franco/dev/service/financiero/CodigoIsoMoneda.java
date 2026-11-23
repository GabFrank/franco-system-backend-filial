package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Moneda;

/**
 * El codigo ISO 4217 de una moneda: {@code moneda.codigo_iso} si el central ya lo relleno (V259.6), y
 * si no, deducido de la denominacion de las monedas de siempre. Es la unica comparacion por nombre que
 * queda, y vive aca: SIFEN necesita el codigo (cMoneOpe) y una factura no puede esperar al relleno.
 */
public final class CodigoIsoMoneda {

    private CodigoIsoMoneda() {
    }

    /** null si no se puede saber. */
    public static String de(Moneda moneda) {
        if (moneda == null) return null;
        if (moneda.getCodigoIso() != null && !moneda.getCodigoIso().trim().isEmpty()) {
            return moneda.getCodigoIso().trim().toUpperCase();
        }
        return deDenominacion(moneda.getDenominacion());
    }

    /** Tambien acepta un codigo ya ISO ("USD") o una denominacion ("DOLAR"), como manda el escritorio. */
    public static String deDenominacion(String texto) {
        if (texto == null) return null;
        String d = texto.trim().toUpperCase();
        if (d.isEmpty()) return null;
        if (d.length() == 3 && d.chars().allMatch(Character::isLetter)) return d;
        if (d.contains("GUARAN")) return "PYG";
        if (d.contains("DOLAR") || d.contains("DÓLAR")) return "USD";
        if (d.contains("REAL")) return "BRL";
        if (d.contains("PESO ARG") || d.equals("PESO") || d.contains("ARGENTIN")) return "ARS";
        if (d.contains("EURO")) return "EUR";
        return null;
    }
}
