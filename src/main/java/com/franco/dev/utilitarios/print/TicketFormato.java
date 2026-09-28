package com.franco.dev.utilitarios.print;

/**
 * Helper de formateo de tickets ESC/POS parametrizado por cantidad de columnas
 * (caracteres por linea). Reemplaza el ancho hardcodeado de 32 columnas por un valor
 * derivado del perfil de papel de la impresora (48/58/72/80 mm...).
 */
public class TicketFormato {

    public static final int COLUMNAS_POR_DEFECTO = 32;

    private final int columnas;

    public TicketFormato(Integer columnas) {
        this.columnas = (columnas == null || columnas <= 0) ? COLUMNAS_POR_DEFECTO : columnas;
    }

    public int getColumnas() {
        return columnas;
    }

    public String separador() {
        return repetir('-', columnas);
    }

    public String separador(char caracter) {
        return repetir(caracter, columnas);
    }

    public String truncar(String texto) {
        return truncar(texto, columnas);
    }

    public String truncar(String texto, int max) {
        if (texto == null) {
            return "";
        }
        return texto.length() > max ? texto.substring(0, max) : texto;
    }

    public String padDerecha(String texto) {
        return padDerecha(texto, columnas);
    }

    public String padDerecha(String texto, int ancho) {
        String t = truncar(texto == null ? "" : texto, ancho);
        StringBuilder sb = new StringBuilder(t);
        while (sb.length() < ancho) {
            sb.append(' ');
        }
        return sb.toString();
    }

    public String padIzquierda(String texto, int ancho) {
        String t = truncar(texto == null ? "" : texto, ancho);
        StringBuilder sb = new StringBuilder();
        while (sb.length() < ancho - t.length()) {
            sb.append(' ');
        }
        sb.append(t);
        return sb.toString();
    }

    /**
     * Dos columnas: {@code izquierda} a la izquierda y {@code derecha} a la derecha,
     * ocupando en total el ancho de columnas.
     */
    public String izqDer(String izquierda, String derecha) {
        String izq = izquierda == null ? "" : izquierda;
        String der = derecha == null ? "" : derecha;
        int espacio = columnas - izq.length() - der.length();
        if (espacio < 1) {
            int maxIzq = Math.max(0, columnas - der.length() - 1);
            izq = truncar(izq, maxIzq);
            espacio = Math.max(1, columnas - izq.length() - der.length());
        }
        StringBuilder sb = new StringBuilder(izq);
        for (int i = 0; i < espacio; i++) {
            sb.append(' ');
        }
        sb.append(der);
        return sb.toString();
    }

    private static String repetir(char caracter, int cantidad) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cantidad; i++) {
            sb.append(caracter);
        }
        return sb.toString();
    }

    /** Lo que se imprime en lugar de un total en moneda extranjera cuando no hay cotizacion. */
    public static final String SIN_COTIZACION = "-";

    /**
     * Total en moneda extranjera (reales, dolares) para una linea de ticket, con dos decimales.
     *
     * <p>{@code total} es nulo cuando la venta se registro sin cotizacion cargada: el PDV deja
     * {@code venta.totalRs/totalDs} en null en vez de inventar un cambio 1:1. Se imprime
     * {@link #SIN_COTIZACION}. Sin este guard, {@code venta.getTotalRs() + extra} es un
     * NullPointerException al desboxear, y {@code String.format("%.2f", null)} imprime "nu"
     * (el Formatter aplica la precision como limite de caracteres sobre el texto "null").
     *
     * @param extra monto a sumar en la misma moneda (p. ej. el delivery); nulo cuenta como 0
     */
    public static String formatearTotalMoneda(Double total, Double extra) {
        if (total == null) {
            return SIN_COTIZACION;
        }
        return String.format("%.2f", total + (extra != null ? extra : 0d));
    }
}
