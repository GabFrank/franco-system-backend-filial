package com.franco.dev.eventos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Recorta el `data` de los eventos con proyeccion (CatalogoEventos):
 * - limpiar: deja solo las rutas de la lista blanca del tipo, y lo personal o sensible solo si se pide;
 * - recortar: deja solo las rutas que eligio la suscripcion en `campos`.
 * Las listas se recorren elemento por elemento con la misma ruta (sin indices).
 */
public final class Proyeccion {

    private Proyeccion() {
    }

    /** Lista blanca del tipo. Un tipo sin catalogo devuelve un mapa vacio. */
    public static Map<String, Object> limpiar(String tipo, Map<String, Object> datos, boolean personales, boolean sensibles) {
        CatalogoEventos.Tipo t = CatalogoEventos.tipo(tipo);
        if (t == null || datos == null) return new LinkedHashMap<>();
        return copiar(datos, "", ruta -> {
            CatalogoEventos.Clase c = t.rutas.get(ruta);
            if (c != null) {
                return c == CatalogoEventos.Clase.OPERATIVO
                        || (c == CatalogoEventos.Clase.PERSONAL && personales)
                        || (c == CatalogoEventos.Clase.SENSIBLE && sensibles);
            }
            return esPrefijo(ruta, t.rutas.keySet());
        });
    }

    /** Las rutas de `campos` que existen en el catalogo del tipo (igual a una ruta o prefijo de alguna). */
    public static List<String> camposValidos(String tipo, Collection<String> campos) {
        List<String> ok = new ArrayList<>();
        CatalogoEventos.Tipo t = CatalogoEventos.tipo(tipo);
        if (t == null || campos == null) return ok;
        for (String c : campos) {
            if (c == null) continue;
            String r = c.trim();
            if (t.rutas.containsKey(r) || esPrefijo(r, t.rutas.keySet())) ok.add(r);
        }
        return ok;
    }

    /**
     * Deja solo las rutas pedidas. Sin `campos` (null o vacio) devuelve todo. Las rutas desconocidas se
     * ignoran; si ninguna es valida, el data queda vacio (sale solo el sobre, con su subject).
     */
    public static Map<String, Object> recortar(String tipo, Map<String, Object> datos, List<String> campos) {
        if (campos == null || campos.isEmpty() || datos == null) return datos;
        List<String> validos = camposValidos(tipo, campos);
        if (validos.isEmpty()) return new LinkedHashMap<>();
        return copiar(datos, "", ruta -> {
            for (String v : validos) {
                // La ruta pedida o algo adentro de ella se queda; un ancestro se recorre
                if (ruta.equals(v) || ruta.startsWith(v + ".") || v.startsWith(ruta + ".")) return true;
            }
            return false;
        });
    }

    /** Claves (con su ruta) que no estan en el catalogo del tipo: el test de contrato espera ninguna. */
    public static List<String> rutasFueraDeCatalogo(String tipo, Map<String, Object> datos) {
        List<String> fuera = new ArrayList<>();
        CatalogoEventos.Tipo t = CatalogoEventos.tipo(tipo);
        if (t == null) return fuera;
        recorrer(datos, "", ruta -> {
            if (!t.rutas.containsKey(ruta) && !esPrefijo(ruta, t.rutas.keySet())) fuera.add(ruta);
        });
        return fuera;
    }

    static boolean esPrefijo(String ruta, Set<String> rutas) {
        String p = ruta + ".";
        for (String r : rutas) if (r.startsWith(p)) return true;
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copiar(Map<String, Object> m, String base, Predicate<String> entra) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : m.entrySet()) {
            String ruta = base.isEmpty() ? e.getKey() : base + "." + e.getKey();
            if (!entra.test(ruta)) continue;
            Object v = e.getValue();
            if (v instanceof Map) {
                out.put(e.getKey(), copiar((Map<String, Object>) v, ruta, entra));
            } else if (v instanceof Collection) {
                List<Object> l = new ArrayList<>();
                for (Object x : (Collection<?>) v) {
                    l.add(x instanceof Map ? copiar((Map<String, Object>) x, ruta, entra) : x);
                }
                out.put(e.getKey(), l);
            } else {
                out.put(e.getKey(), v);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void recorrer(Map<String, Object> m, String base, java.util.function.Consumer<String> visita) {
        if (m == null) return;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            String ruta = base.isEmpty() ? e.getKey() : base + "." + e.getKey();
            Object v = e.getValue();
            if (v instanceof Map) {
                recorrer((Map<String, Object>) v, ruta, visita);
            } else if (v instanceof Collection) {
                boolean hoja = true;
                for (Object x : (Collection<?>) v) {
                    if (x instanceof Map) {
                        hoja = false;
                        recorrer((Map<String, Object>) x, ruta, visita);
                    }
                }
                if (hoja) visita.accept(ruta);
            } else {
                visita.accept(ruta);
            }
        }
    }
}
