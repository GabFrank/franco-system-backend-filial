package com.franco.dev.eventos;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Campos que puede llevar cada tipo de evento. Lo que no esta en la lista no sale: asi no se filtran
 * nicks, documentos, telefonos ni datos de tarjeta por descuido. Un tipo sin lista no lleva datos.
 */
public final class ListaBlanca {

    private static final Map<String, Set<String>> CAMPOS = new HashMap<>();

    static {
        CAMPOS.put("venta.item_eliminado", set(
                "venta_id", "venta_item_id", "caja_id", "producto_id", "presentacion_id",
                "cantidad", "precio", "valor_total", "venta_estado"));
        CAMPOS.put("webhook.prueba", set("mensaje"));
        CAMPOS.put("nodo.latido", set(
                "pendientes", "pendiente_mas_viejo", "descartadas_24h", "config_version", "destinos_pausados",
                "hora_nodo", "emisor_encendido", "firma_configurada", "fallos_emisor", "filtros_invalidos"));
    }

    private ListaBlanca() {
    }

    private static Set<String> set(String... c) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(c)));
    }

    public static Set<String> campos(String tipo) {
        return CAMPOS.getOrDefault(tipo, Collections.emptySet());
    }

    public static Map<String, Object> filtrar(String tipo, Map<String, Object> datos) {
        // Los tipos con proyeccion (fase 2) tienen su lista blanca por rutas en CatalogoEventos
        if (CatalogoEventos.tieneProyeccion(tipo)) return Proyeccion.limpiar(tipo, datos, true, true);
        Map<String, Object> out = new LinkedHashMap<>();
        if (datos == null) return out;
        Set<String> permitidos = campos(tipo);
        for (Map.Entry<String, Object> e : datos.entrySet()) {
            if (permitidos.contains(e.getKey())) out.put(e.getKey(), e.getValue());
        }
        return out;
    }
}
