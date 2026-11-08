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
    /** "lote" o "individual" (por defecto). */
    private String entrega;
    private boolean datosPersonales;
    private String cabeceraNombre;
    private String cabeceraRef;
    private boolean latido;
    private boolean activo;
    private Long configVersion;

    public boolean porLote() {
        return "lote".equalsIgnoreCase(entrega);
    }
}
