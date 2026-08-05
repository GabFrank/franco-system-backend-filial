package com.franco.dev.utilitarios;

/**
 * Formato del número de factura legal: {@code EEE-PPP-NNNNNNN}
 * (establecimiento - punto de expedición - correlativo con padding a 7 dígitos).
 */
public final class FacturaNumeroUtils {

    private static final int LARGO_CORRELATIVO = 7;

    private FacturaNumeroUtils() {
    }

    /**
     * @param codigoEstablecimiento código de establecimiento de la sucursal
     * @param puntoExpedicion       punto de expedición del timbrado detalle
     * @param numeroFactura         correlativo de la factura
     * @return el número formateado, o null si no hay correlativo
     */
    public static String format(String codigoEstablecimiento, String puntoExpedicion, Integer numeroFactura) {
        if (numeroFactura == null) return null;
        StringBuilder correlativo = new StringBuilder();
        String numero = numeroFactura.toString();
        for (int i = LARGO_CORRELATIVO; i > numero.length(); i--) {
            correlativo.append("0");
        }
        correlativo.append(numero);
        return codigoEstablecimiento + "-" + puntoExpedicion + "-" + correlativo;
    }
}
