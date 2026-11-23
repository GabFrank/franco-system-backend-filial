package com.franco.dev.eventos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Arma el cuerpo en CloudEvents 1.0, modo estructurado JSON. Un evento: application/cloudevents+json;
 * un lote: application/cloudevents-batch+json (array).
 */
public final class SobreCloudEvents {

    public static final String CT_UNO = "application/cloudevents+json";
    public static final String CT_LOTE = "application/cloudevents-batch+json";

    private SobreCloudEvents() {
    }

    public static ObjectNode evento(ObjectMapper mapper, String empresa, EventosRepositorio.EntregaReclamada e) {
        return evento(mapper, empresa, e, null);
    }

    /**
     * Con suscripcion, el data de los tipos con proyeccion sale recortado para ella: sin lo personal ni lo
     * sensible si no tiene el permiso, y solo con sus `campos` si los eligio.
     */
    @SuppressWarnings("unchecked")
    public static ObjectNode evento(ObjectMapper mapper, String empresa, EventosRepositorio.EntregaReclamada e, Suscripcion s) {
        ObjectNode n = mapper.createObjectNode();
        n.put("specversion", "1.0");
        n.put("id", e.eventoId);
        n.put("source", "frc://" + empresa + "/sucursal/" + (e.sucursalId != null ? e.sucursalId : 0));
        n.put("type", "py.frc." + e.tipo + ".v1");
        if (e.ocurridoEn != null) n.put("time", e.ocurridoEn.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        if (e.sujeto != null) n.put("subject", e.sujeto);
        n.put("datacontenttype", "application/json");
        // Extensiones: correlativa por suscripcion y nodo (el receptor detecta huecos), gravedad y link
        n.put("frcsecuencia", e.secuencia);
        if (e.gravedad != null) n.put("frcgravedad", e.gravedad);
        if (e.link != null) n.put("frclink", e.link);
        JsonNode datos;
        try {
            if (e.datos == null) {
                datos = mapper.createObjectNode();
            } else if (s != null && CatalogoEventos.tieneProyeccion(e.tipo)) {
                java.util.Map<String, Object> m = mapper.readValue(e.datos, java.util.Map.class);
                m = Proyeccion.limpiar(e.tipo, m, s.isDatosPersonales(), s.isDatosSensibles());
                m = Proyeccion.recortar(e.tipo, m, s.getCampos() == null ? null : s.getCampos().get(e.tipo));
                datos = mapper.valueToTree(m);
            } else {
                datos = mapper.readTree(e.datos);
            }
        } catch (Exception ex) {
            datos = mapper.createObjectNode();
        }
        n.set("data", datos);
        return n;
    }

    public static String cuerpo(ObjectMapper mapper, String empresa, List<EventosRepositorio.EntregaReclamada> l, boolean lote) {
        return cuerpo(mapper, empresa, l, lote, null);
    }

    public static String cuerpo(ObjectMapper mapper, String empresa, List<EventosRepositorio.EntregaReclamada> l, boolean lote, Suscripcion s) {
        try {
            if (!lote) return mapper.writeValueAsString(evento(mapper, empresa, l.get(0), s));
            ArrayNode a = mapper.createArrayNode();
            for (EventosRepositorio.EntregaReclamada e : l) a.add(evento(mapper, empresa, e, s));
            return mapper.writeValueAsString(a);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
