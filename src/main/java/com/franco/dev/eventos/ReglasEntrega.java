package com.franco.dev.eventos;

import java.util.Map;

/** Reglas puras del reparto y del envio: que suscripcion recibe que evento y que hacer con cada respuesta. */
public final class ReglasEntrega {

    public enum Resultado { ENVIADA, REINTENTO, DESCARTADA, PAUSAR_410 }

    /** Reintentos: 10 s, 1 min, 5 min, 30 min y despues cada 1 h, hasta 72 h desde el primer intento. */
    private static final long[] ESPERAS_SEG = {10, 60, 300, 1800, 3600};

    private ReglasEntrega() {
    }

    public static boolean coincideTipo(String patron, String tipo) {
        if (patron == null || tipo == null) return false;
        if ("*".equals(patron)) return true;
        if (patron.endsWith(".*")) return tipo.startsWith(patron.substring(0, patron.length() - 1));
        return patron.equals(tipo);
    }

    public static boolean recibe(Suscripcion s, String tipo, Long sucursalId, Map<String, Object> datos) {
        if (!s.isActivo() || s.getUrl() == null) return false;
        // El latido va solo a quien lo pidio, sin importar la lista de eventos
        if ("nodo.latido".equals(tipo)) return s.isLatido();
        if (s.getEventos() == null || s.getEventos().stream().noneMatch(p -> coincideTipo(p, tipo))) return false;
        if (s.getEventosPausados() != null && s.getEventosPausados().stream().anyMatch(p -> coincideTipo(p, tipo))) return false;
        if (s.getSucursalIds() != null && !s.getSucursalIds().isEmpty()
                && (sucursalId == null || !s.getSucursalIds().contains(sucursalId))) return false;
        return pasaUmbral(s, tipo, datos);
    }

    static boolean pasaUmbral(Suscripcion s, String tipo, Map<String, Object> datos) {
        if (s.getUmbrales() == null) return true;
        Map<String, Number> u = s.getUmbrales().get(tipo);
        if (u == null || u.isEmpty()) return true;
        for (Map.Entry<String, Number> e : u.entrySet()) {
            Object v = datos == null ? null : datos.get(e.getKey());
            if (!(v instanceof Number)) return false;
            if (Math.abs(((Number) v).doubleValue()) < e.getValue().doubleValue()) return false;
        }
        return true;
    }

    /**
     * Solo se descarta en el acto lo que el receptor nunca va a aceptar tal cual (400, 413, 415, 422).
     * El resto se reintenta con backoff: 3xx (no se siguen), 401/403 (credencial rotada), 404 (URL
     * transitoria), 408, 409, 425, 429, 5xx y cualquier otro. 410 pausa el destino. Lo que no sale en
     * 72 h desde el primer intento pasa a DESCARTADA (ver EventosRepositorio.resolver).
     */
    public static Resultado clasificar(int status) {
        if (status >= 200 && status < 300) return Resultado.ENVIADA;
        if (status == 410) return Resultado.PAUSAR_410;
        if (status == 400 || status == 413 || status == 415 || status == 422) return Resultado.DESCARTADA;
        return Resultado.REINTENTO;
    }

    /** Horas desde el primer intento real tras las cuales una entrega que no salio se descarta. */
    public static final int VENCIMIENTO_HORAS = 72;

    /** Segundos hasta el proximo intento; respeta Retry-After si viene y es mayor. */
    public static long esperaSegundos(int intentosHechos, Long retryAfterSeg) {
        long base = ESPERAS_SEG[Math.min(Math.max(intentosHechos - 1, 0), ESPERAS_SEG.length - 1)];
        if (retryAfterSeg != null && retryAfterSeg > base) return Math.min(retryAfterSeg, 3600);
        return base;
    }
}
