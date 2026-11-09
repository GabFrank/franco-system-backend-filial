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

    /** Contadores desde el arranque, para el latido (fase 2b): lo que el aviso no pudo evaluar. */
    public static final java.util.concurrent.atomic.AtomicLong SIN_CAJA_ANTERIOR = new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong SIN_COTIZACION = new java.util.concurrent.atomic.AtomicLong();

    private static Map<String, Object> base(PdvCaja c) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("caja", EventosVenta.mapa("id", c.getId()));
        d.put("id", c.getId());
        d.put("sucursalId", c.getSucursalId());
        d.put("sucursal", EventosVenta.mapa("id", c.getSucursalId()));
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

    /** Caja anterior del mismo maletin y los totales de su conteo de cierre (null si no se encontro). */
    public static final class Anterior {
        public final PdvCaja caja;
        public final Double gs, rs, ds;

        public Anterior(PdvCaja caja, Double gs, Double rs, Double ds) {
            this.caja = caja;
            this.gs = gs;
            this.rs = rs;
            this.ds = ds;
        }
    }

    /**
     * caja.abierta con la forma del cliente (fase 2b): balance de apertura, la caja anterior del mismo
     * maletin y el estado consecutivo (mismo criterio que el central: total de cierre de la anterior contra
     * total de apertura de esta). Sin caja anterior: cajaAnterior null y SIN_DATOS.
     */
    public static Map<String, Object> abierta(PdvCaja c, Double gs, Double rs, Double ds, Anterior ant) {
        Map<String, Object> d = base(c);
        d.put("conteo", totales(gs, rs, ds));
        Map<String, Object> bal = new LinkedHashMap<>();
        bal.put("totalAperGs", cero(gs));
        bal.put("totalAperRs", cero(rs));
        bal.put("totalAperDs", cero(ds));
        d.put("balance", bal);
        d.put("cajaAnteriorId", ant != null ? ant.caja.getId() : null);
        d.put("estadoDiferenciaConsecutiva", estadoConsecutivo(gs, rs, ds, ant));
        if (ant != null) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("id", ant.caja.getId());
            if (ant.caja.getUsuario() != null) a.put("usuario", EventosVenta.usuario(ant.caja.getUsuario()));
            a.put("fechaCierre", ant.caja.getFechaCierre() != null ? ant.caja.getFechaCierre().toString() : null);
            Map<String, Object> ab = new LinkedHashMap<>();
            ab.put("totalCierreGs", cero(ant.gs));
            ab.put("totalCierreRs", cero(ant.rs));
            ab.put("totalCierreDs", cero(ant.ds));
            a.put("balance", ab);
            d.put("cajaAnterior", a);
        } else {
            d.put("cajaAnterior", null);
        }
        return d;
    }

    /** El central suma el conteo en todas las monedas sin convertir; aca se compara moneda por moneda. */
    public static String estadoConsecutivo(Double gs, Double rs, Double ds, Anterior ant) {
        if (ant == null) return "SIN_DATOS";
        return hayDiferencia(gs, rs, ds, ant) ? "CON_DIFERENCIA" : "SIN_DIFERENCIA";
    }

    public static boolean hayDiferencia(Double gs, Double rs, Double ds, Anterior ant) {
        return ant != null && (Math.abs(cero(gs) - cero(ant.gs)) > 0.005 || Math.abs(cero(rs) - cero(ant.rs)) > 0.005
                || Math.abs(cero(ds) - cero(ant.ds)) > 0.005);
    }

    /** caja.maletin_distinto (fase 2b): lo que dejo la caja anterior contra lo que recibio esta. */
    public static Map<String, Object> maletinDistinto(PdvCaja c, Double gs, Double rs, Double ds, Anterior ant) {
        Map<String, Object> d = new LinkedHashMap<>();
        Map<String, Object> caja = new LinkedHashMap<>();
        caja.put("id", c.getId());
        caja.put("sucursal", EventosVenta.mapa("id", c.getSucursalId()));
        if (c.getUsuario() != null) caja.put("usuario", EventosVenta.usuario(c.getUsuario()));
        caja.put("fechaApertura", c.getFechaApertura() != null ? c.getFechaApertura().toString() : null);
        if (c.getMaletin() != null) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getMaletin().getId());
            m.put("descripcion", c.getMaletin().getDescripcion());
            caja.put("maletin", m);
        }
        d.put("caja", caja);
        d.put("sucursalId", c.getSucursalId());
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("id", ant.caja.getId());
        if (ant.caja.getUsuario() != null) a.put("usuario", EventosVenta.usuario(ant.caja.getUsuario()));
        a.put("fechaCierre", ant.caja.getFechaCierre() != null ? ant.caja.getFechaCierre().toString() : null);
        d.put("cajaAnterior", a);
        d.put("dejo", tres(ant.gs, ant.rs, ant.ds));
        d.put("recibio", tres(gs, rs, ds));
        d.put("diferencia", tres(cero(gs) - cero(ant.gs), cero(rs) - cero(ant.rs), cero(ds) - cero(ant.ds)));
        return d;
    }

    private static Map<String, Object> tres(Double gs, Double rs, Double ds) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gs", cero(gs));
        m.put("rs", cero(rs));
        m.put("ds", cero(ds));
        return m;
    }

    static double cero(Double v) {
        return v != null ? v : 0.0;
    }

    /** El balance de generarBalance con los nombres de la consulta del cliente (CajaBalance). */
    public static Map<String, Object> balance(PdvCajaBalanceDto b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalAperGs", cero(b.getTotalGsAper()));
        m.put("totalAperRs", cero(b.getTotalRsAper()));
        m.put("totalAperDs", cero(b.getTotalDsAper()));
        m.put("totalCierreGs", cero(b.getTotalGsCierre()));
        m.put("totalCierreRs", cero(b.getTotalRsCierre()));
        m.put("totalCierreDs", cero(b.getTotalDsCierre()));
        m.put("diferenciaGs", cero(b.getDiferenciaGs()));
        m.put("diferenciaRs", cero(b.getDiferenciaRs()));
        m.put("diferenciaDs", cero(b.getDiferenciaDs()));
        m.put("totalVentaGs", cero(b.getTotalVentaGs()));
        m.put("totalVentaRs", cero(b.getTotalVentaRs()));
        m.put("totalVentaDs", cero(b.getTotalVentaDs()));
        m.put("totalRetiroGs", cero(b.getTotalRetiroGs()));
        m.put("totalRetiroRs", cero(b.getTotalRetiroRs()));
        m.put("totalRetiroDs", cero(b.getTotalRetiroDs()));
        m.put("totalGastoGs", cero(b.getTotalGastoGs()));
        m.put("totalGastoRs", cero(b.getTotalGastoRs()));
        m.put("totalGastoDs", cero(b.getTotalGastoDs()));
        m.put("vueltoGs", cero(b.getVueltoGs()));
        m.put("vueltoRs", cero(b.getVueltoRs()));
        m.put("vueltoDs", cero(b.getVueltoDs()));
        m.put("totalTarjeta", cero(b.getTotalTarjeta()));
        m.put("totalTransferencia", cero(b.getTotalTransferencia()));
        m.put("totalCredito", cero(b.getTotalCredito()));
        return m;
    }

    /** Cierre con el balance completo de PdvCajaService.generarBalance (fase 2b) y la cotizacion usada. */
    public static Map<String, Object> cerrada(PdvCaja c, PdvCajaBalanceDto b, Long cajaAnteriorId, Double cotRs, Double cotDs) {
        Map<String, Object> d = base(c);
        d.put("conteo", totales(b.getTotalGsCierre(), b.getTotalRsCierre(), b.getTotalDsCierre()));
        d.put("cajaAnteriorId", cajaAnteriorId);
        d.put("balance", balance(b));
        Map<String, Object> cot = new LinkedHashMap<>();
        cot.put("rs", cotRs);
        cot.put("ds", cotDs);
        d.put("cotizacion", cot);
        return d;
    }

    /** |difGs| + |difRs|·cot(R) + |difDs|·cot(D); null si falta alguna cotizacion que haga falta. */
    public static Double diferenciaTotalGs(PdvCajaBalanceDto b, Double cotRs, Double cotDs) {
        double rs = Math.abs(cero(b.getDiferenciaRs())), ds = Math.abs(cero(b.getDiferenciaDs()));
        if ((rs != 0 && (cotRs == null || cotRs <= 0)) || (ds != 0 && (cotDs == null || cotDs <= 0))) return null;
        return Math.abs(cero(b.getDiferenciaGs())) + (rs != 0 ? rs * cotRs : 0) + (ds != 0 ? ds * cotDs : 0);
    }

    public static Map<String, Object> atributosCierre(PdvCaja c, PdvCajaBalanceDto b, Double cotRs, Double cotDs) {
        Map<String, Object> a = atributosCierre(c, b);
        Double total = diferenciaTotalGs(b, cotRs, cotDs);
        if (total != null) a.put("diferencia_total_gs", total);
        return a;
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

    /**
     * caja.conteo_editado (fase 2, #246). Conteo no guarda totales: el anterior se suma de sus billetes
     * (conteo_moneda) y de sus filas de respaldo activas; el nuevo es el del input.
     */
    public static Map<String, Object> conteoEditado(PdvCaja c, boolean apertura,
                                                    com.franco.dev.domain.personas.Usuario editor,
                                                    Map<String, Double> anterior, Double gs, Double rs, Double ds) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("caja", EventosVenta.mapa("id", c.getId()));
        d.put("sucursalId", c.getSucursalId());
        d.put("apertura", apertura);
        if (editor != null) d.put("usuario", EventosVenta.usuario(editor));
        d.put("conteoAnterior", totales(anterior.get("GUARANI"), anterior.get("REAL"), anterior.get("DOLAR")));
        d.put("conteoNuevo", totales(gs, rs, ds));
        return d;
    }

    /** Fase 2b: caja{id sucursal fechaCierre}, tipo y el balance de diferencias recalculado despues de editar. */
    public static Map<String, Object> conteoEditado(PdvCaja c, boolean apertura,
                                                    com.franco.dev.domain.personas.Usuario editor,
                                                    Map<String, Double> anterior, Double gs, Double rs, Double ds,
                                                    PdvCajaBalanceDto despues) {
        Map<String, Object> d = conteoEditado(c, apertura, editor, anterior, gs, rs, ds);
        Map<String, Object> caja = new LinkedHashMap<>();
        caja.put("id", c.getId());
        caja.put("sucursal", EventosVenta.mapa("id", c.getSucursalId()));
        caja.put("fechaCierre", c.getFechaCierre() != null ? c.getFechaCierre().toString() : null);
        d.put("caja", caja);
        d.put("tipo", apertura ? "apertura" : "cierre");
        if (despues != null) {
            Map<String, Object> bal = new LinkedHashMap<>();
            bal.put("diferenciaGs", cero(despues.getDiferenciaGs()));
            bal.put("diferenciaRs", cero(despues.getDiferenciaRs()));
            bal.put("diferenciaDs", cero(despues.getDiferenciaDs()));
            d.put("balance", bal);
        }
        return d;
    }

    /** Totales por denominacion de un conteo, desde sus billetes y sus movimientos de respaldo activos. */
    public static Map<String, Double> totalesDeConteo(List<com.franco.dev.domain.financiero.ConteoMoneda> billetes,
                                                      List<com.franco.dev.domain.financiero.MovimientoCaja> respaldo) {
        Map<String, Double> t = new LinkedHashMap<>();
        if (billetes != null) for (com.franco.dev.domain.financiero.ConteoMoneda cm : billetes) {
            if (cm.getMonedaBilletes() == null || cm.getMonedaBilletes().getMoneda() == null || cm.getCantidad() == null
                    || cm.getMonedaBilletes().getValor() == null) continue;
            t.merge(cm.getMonedaBilletes().getMoneda().getDenominacion(), cm.getCantidad() * cm.getMonedaBilletes().getValor(), Double::sum);
        }
        if (respaldo != null) for (com.franco.dev.domain.financiero.MovimientoCaja mc : respaldo) {
            if (Boolean.FALSE.equals(mc.getActivo()) || mc.getMoneda() == null || mc.getCantidad() == null) continue;
            t.merge(mc.getMoneda().getDenominacion(), mc.getCantidad(), Double::sum);
        }
        return t;
    }
}
