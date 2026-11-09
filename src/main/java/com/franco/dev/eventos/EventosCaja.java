package com.franco.dev.eventos;

import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.graphql.financiero.input.PdvCajaBalanceDto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Proyecciones v1 de los eventos de caja (fase 2, #246). Proyeccion.limpiar saca lo que no va. */
public final class EventosCaja {

    private EventosCaja() {
    }

    private static Map<String, Object> base(PdvCaja c) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("caja", EventosVenta.mapa("id", c.getId()));
        d.put("sucursalId", c.getSucursalId());
        if (c.getUsuario() != null) d.put("usuario", EventosVenta.usuario(c.getUsuario()));
        if (c.getMaletin() != null) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getMaletin().getId());
            m.put("descripcion", c.getMaletin().getDescripcion());
            d.put("maletin", m);
        }
        d.put("fechaApertura", c.getFechaApertura() != null ? c.getFechaApertura().toString() : null);
        d.put("fechaCierre", c.getFechaCierre() != null ? c.getFechaCierre().toString() : null);
        return d;
    }

    static List<Map<String, Object>> totales(Double gs, Double rs, Double ds) {
        List<Map<String, Object>> l = new ArrayList<>();
        l.add(total("GUARANI", gs));
        l.add(total("REAL", rs));
        l.add(total("DOLAR", ds));
        return l;
    }

    private static Map<String, Object> total(String moneda, Double v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("moneda", moneda);
        m.put("total", v != null ? v : 0.0);
        return m;
    }

    public static Map<String, Object> abierta(PdvCaja c, Double gs, Double rs, Double ds) {
        Map<String, Object> d = base(c);
        d.put("conteo", totales(gs, rs, ds));
        return d;
    }

    /** Cierre con el balance de PdvCajaService.generarBalance: contado y diferencia por moneda. */
    public static Map<String, Object> cerrada(PdvCaja c, PdvCajaBalanceDto b) {
        Map<String, Object> d = base(c);
        d.put("conteo", totales(b.getTotalGsCierre(), b.getTotalRsCierre(), b.getTotalDsCierre()));
        List<Map<String, Object>> dif = new ArrayList<>();
        dif.add(diferencia("GUARANI", b.getTotalGsCierre(), b.getDiferenciaGs()));
        dif.add(diferencia("REAL", b.getTotalRsCierre(), b.getDiferenciaRs()));
        dif.add(diferencia("DOLAR", b.getTotalDsCierre(), b.getDiferenciaDs()));
        d.put("diferencias", dif);
        return d;
    }

    private static Map<String, Object> diferencia(String moneda, Double contado, Double diferencia) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("moneda", moneda);
        m.put("contado", contado != null ? contado : 0.0);
        m.put("diferencia", diferencia != null ? diferencia : 0.0);
        return m;
    }

    public static Map<String, Object> atributosCierre(PdvCaja c, PdvCajaBalanceDto b) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("caja_id", c.getId());
        a.put("usuario_id", c.getUsuario() != null ? c.getUsuario().getId() : null);
        double gs = b.getDiferenciaGs() != null ? b.getDiferenciaGs() : 0;
        double rs = b.getDiferenciaRs() != null ? b.getDiferenciaRs() : 0;
        double ds = b.getDiferenciaDs() != null ? b.getDiferenciaDs() : 0;
        a.put("diferencia_gs", gs);
        a.put("diferencia_rs", rs);
        a.put("diferencia_ds", ds);
        a.put("con_diferencia", gs != 0 || rs != 0 || ds != 0);
        return a;
    }

    public static Map<String, Object> atributosCaja(PdvCaja c) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("caja_id", c.getId());
        a.put("usuario_id", c.getUsuario() != null ? c.getUsuario().getId() : null);
        return a;
    }
}
