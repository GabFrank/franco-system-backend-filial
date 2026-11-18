package com.franco.dev.eventos;

import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Fila del espejo configuraciones.webhook_suscripcion. */
@Data
public class Suscripcion {
    private Long id;
    private String nombre;
    private String url;
    private List<String> eventos = Collections.emptyList();
    private List<String> eventosPausados = Collections.emptyList();
    private List<Long> sucursalIds;
    /** {"tipo": {"campo": minimo}}: el evento sale solo si el valor absoluto del campo llega al minimo. */
    private Map<String, Map<String, Number>> umbrales;
    /** "individual" (por defecto), "lote" o "lote:<segundos>" (junta lo pendiente y lo manda cuando el mas viejo cumple la ventana). */
    private String entrega;
    /**
     * {"tipo": {"atributo": [valores]}}: el evento sale solo si cada atributo coincide con alguno de los
     * valores (si el atributo del evento es una lista, alcanza con un elemento). Un atributo que el
     * catalogo no conoce para ese tipo no entrega nada y se cuenta como filtro invalido.
     */
    private Map<String, Map<String, List<Object>>> filtros;
    /** {"tipo": ["ruta", ...]}: recorta el data a esas rutas. Sin entrada para el tipo, va completo. */
    private Map<String, List<String>> campos;
    private boolean datosPersonales;
    /** Codigo de autorizacion, boleta, QR crudo, identificador de transaccion: solo con este permiso. */
    private boolean datosSensibles;
    private String cabeceraNombre;
    private String secretoRef;
    private boolean latido;
    private boolean activo;
    private Long configVersion;

    public boolean porLote() {
        return entrega != null && entrega.trim().toLowerCase().matches("lote(:[0-9]+)?");
    }
}
