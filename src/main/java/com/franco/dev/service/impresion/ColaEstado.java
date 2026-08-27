package com.franco.dev.service.impresion;

import java.util.ArrayList;
import java.util.List;

/**
 * Estado real de una cola CUPS, tal como lo reporta {@code lpstat -p}.
 *
 * <p>Existe porque el chip de la tarjeta mostraba {@code impresora.activo} (un flag de la BD)
 * y no el estado del sistema: en produccion la cola adm_ticket estuvo {@code disabled} tres
 * dias mientras la app decia "Activa". CUPS deshabilita una cola ante un fallo del backend
 * (politica {@code stop-printer}) y no se recupera sola.</p>
 */
public class ColaEstado {

    public static final String INACTIVA = "INACTIVA";
    public static final String IMPRIMIENDO = "IMPRIMIENDO";
    public static final String DESHABILITADA = "DESHABILITADA";
    public static final String DESCONOCIDA = "DESCONOCIDA";

    private static final String PREFIJO = "printer ";

    private String nombre;
    private String estado;
    private String razon;
    private Boolean habilitada;

    /**
     * Parsea {@code lpstat -p}. Se ejecuta con {@code LC_ALL=C} para que las frases que se
     * matchean aca no dependan del idioma del sistema.
     */
    public static List<ColaEstado> parsear(String salidaLpstat) {
        List<ColaEstado> colas = new ArrayList<>();
        if (salidaLpstat == null || salidaLpstat.isEmpty()) {
            return colas;
        }
        String[] lineas = salidaLpstat.split("\\R");
        for (int i = 0; i < lineas.length; i++) {
            String linea = lineas[i];
            if (!linea.startsWith(PREFIJO)) {
                continue;
            }
            String resto = linea.substring(PREFIJO.length());
            int sp = resto.indexOf(' ');
            if (sp < 0) {
                continue;
            }
            ColaEstado cola = new ColaEstado();
            cola.nombre = resto.substring(0, sp);
            cola.aplicarEstado(resto.substring(sp + 1));
            cola.razon = razonSiguiente(lineas, i);
            colas.add(cola);
        }
        return colas;
    }

    /** La razon del fallo viene en la linea indentada que sigue al encabezado de la cola. */
    private static String razonSiguiente(String[] lineas, int indice) {
        if (indice + 1 >= lineas.length) {
            return "";
        }
        String siguiente = lineas[indice + 1];
        if (siguiente.isEmpty() || !Character.isWhitespace(siguiente.charAt(0))) {
            return "";
        }
        return siguiente.trim();
    }

    private void aplicarEstado(String descripcion) {
        if (descripcion.contains("disabled since")) {
            estado = DESHABILITADA;
            habilitada = false;
        } else if (descripcion.contains("now printing") || descripcion.contains("is busy")) {
            estado = IMPRIMIENDO;
            habilitada = true;
        } else if (descripcion.contains("is idle")) {
            estado = INACTIVA;
            habilitada = true;
        } else {
            estado = DESCONOCIDA;
            habilitada = !descripcion.contains("disabled");
        }
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getRazon() { return razon; }
    public void setRazon(String razon) { this.razon = razon; }
    public Boolean getHabilitada() { return habilitada; }
    public void setHabilitada(Boolean habilitada) { this.habilitada = habilitada; }
}
