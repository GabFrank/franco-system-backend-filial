package com.franco.dev.eventos;

import com.franco.dev.domain.financiero.VentaTarjeta;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.domain.operaciones.Venta;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Proyeccion de venta_tarjeta.* con la forma de la consulta del cliente (fase 2b, #246). La autorizacion,
 * la boleta, el QR crudo y la imagen del cupon son SENSIBLE en el catalogo; datos_extra y la cuenta
 * bancaria nunca salen.
 */
public final class EventosTarjeta {

    private EventosTarjeta() {
    }

    public static Map<String, Object> tarjeta(VentaTarjeta vt) {
        return tarjeta(vt, null, null);
    }

    /** Con la venta y sus lineas de cobro, si la operacion las tiene (se leen por id en la misma TX). */
    public static Map<String, Object> tarjeta(VentaTarjeta vt, Venta venta, List<CobroDetalle> detalles) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", vt.getId());
        d.put("ventaId", vt.getVentaId());
        d.put("cajaId", vt.getCajaId());
        d.put("caja", EventosVenta.mapa("id", vt.getCajaId()));
        d.put("sucursalId", vt.getSucursalId());
        d.put("estado", vt.getEstado());
        d.put("origen", vt.getOrigen());
        d.put("monto", vt.getMonto());
        d.put("montoEscaneado", vt.getMontoEscaneado());
        if (vt.getMoneda() != null) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", vt.getMoneda().getId());
            m.put("denominacion", vt.getMoneda().getDenominacion());
            d.put("moneda", m);
        }
        if (vt.getUsuario() != null) d.put("usuario", EventosVenta.usuario(vt.getUsuario()));
        d.put("creadoEn", vt.getCreadoEn() != null ? vt.getCreadoEn().toString() : null);
        if (vt.getTerminalPos() != null) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("id", vt.getTerminalPos().getId());
            t.put("descripcion", vt.getTerminalPos().getDescripcion());
            t.put("serie", vt.getTerminalPos().getSerie());
            t.put("codigo", vt.getTerminalPos().getCodigo());
            t.put("adquirente", adquirente(vt));
            if (vt.getTerminalPos().getProveedorServicio() != null) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("id", vt.getTerminalPos().getProveedorServicio().getId());
                if (vt.getTerminalPos().getProveedorServicio().getPersona() != null)
                    p.put("persona", EventosVenta.mapa("nombre", vt.getTerminalPos().getProveedorServicio().getPersona().getNombre()));
                t.put("proveedorServicio", p);
            }
            d.put("terminalPos", t);
        }
        d.put("codigoAutorizacion", vt.getCodigoAutorizacion());
        d.put("numeroBoleta", vt.getNumeroBoleta());
        d.put("qrCrudo", vt.getQrCrudo());
        d.put("imagenUrl", vt.getImagenUrl());
        d.put("noCompletadoMotivo", vt.getNoCompletadoMotivo());
        d.put("noCompletadoEn", vt.getNoCompletadoEn() != null ? vt.getNoCompletadoEn().toString() : null);
        if (venta != null) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", venta.getId());
            v.put("estado", venta.getEstado() != null ? venta.getEstado().name() : null);
            Map<String, Object> cobro = new LinkedHashMap<>();
            cobro.put("cobroDetalleList", EventosVenta.lineasCobro(detalles));
            v.put("cobro", cobro);
            d.put("venta", v);
        }
        return d;
    }

    private static String adquirente(VentaTarjeta vt) {
        if (vt.getTerminalPos() == null || vt.getTerminalPos().getProveedorServicio() == null) return null;
        if (vt.getTerminalPos().getProveedorServicio().getPersona() == null) return null;
        return vt.getTerminalPos().getProveedorServicio().getPersona().getNombre();
    }

    public static Map<String, Object> atributos(VentaTarjeta vt) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("estado", vt.getEstado());
        a.put("terminal_id", vt.getTerminalPos() != null ? vt.getTerminalPos().getId() : null);
        a.put("adquirente", adquirente(vt));
        a.put("caja_id", vt.getCajaId());
        return a;
    }

    /** Foto de lo que cuenta como edicion de un cobro con tarjeta: monto, moneda y terminal. */
    public static Map<String, Object> foto(VentaTarjeta vt) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("monto", vt.getMonto() != null ? vt.getMonto().stripTrailingZeros().toPlainString() : null);
        f.put("moneda", vt.getMoneda() != null ? vt.getMoneda().getId() : null);
        f.put("terminalPos", vt.getTerminalPos() != null ? vt.getTerminalPos().getId() : null);
        return f;
    }

    /** {campo: {antes, despues}} de lo que cambio entre dos fotos; vacio si no cambio nada relevante. */
    public static Map<String, Object> cambios(Map<String, Object> antes, Map<String, Object> despues) {
        Map<String, Object> c = new LinkedHashMap<>();
        for (String k : despues.keySet()) {
            if (Objects.equals(antes.get(k), despues.get(k))) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("antes", antes.get(k));
            m.put("despues", despues.get(k));
            c.put(k, m);
        }
        return c;
    }
}
