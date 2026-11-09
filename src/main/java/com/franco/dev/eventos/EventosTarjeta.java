package com.franco.dev.eventos;

import com.franco.dev.domain.financiero.VentaTarjeta;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Proyeccion v1 de venta_tarjeta.* (fase 2, #246). La autorizacion, la boleta y el QR crudo son
 * SENSIBLE en el catalogo; la imagen del cupon, datos_extra y la cuenta bancaria nunca salen.
 */
public final class EventosTarjeta {

    private EventosTarjeta() {
    }

    public static Map<String, Object> tarjeta(VentaTarjeta vt) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", vt.getId());
        d.put("ventaId", vt.getVentaId());
        d.put("cajaId", vt.getCajaId());
        d.put("sucursalId", vt.getSucursalId());
        d.put("estado", vt.getEstado());
        d.put("origen", vt.getOrigen());
        d.put("monto", vt.getMonto());
        d.put("montoEscaneado", vt.getMontoEscaneado());
        if (vt.getMoneda() != null) d.put("moneda", EventosVenta.mapa("denominacion", vt.getMoneda().getDenominacion()));
        d.put("creadoEn", vt.getCreadoEn() != null ? vt.getCreadoEn().toString() : null);
        if (vt.getTerminalPos() != null) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("id", vt.getTerminalPos().getId());
            t.put("descripcion", vt.getTerminalPos().getDescripcion());
            t.put("serie", vt.getTerminalPos().getSerie());
            t.put("adquirente", adquirente(vt));
            d.put("terminalPos", t);
        }
        d.put("codigoAutorizacion", vt.getCodigoAutorizacion());
        d.put("numeroBoleta", vt.getNumeroBoleta());
        d.put("qrCrudo", vt.getQrCrudo());
        d.put("noCompletadoMotivo", vt.getNoCompletadoMotivo());
        d.put("noCompletadoEn", vt.getNoCompletadoEn() != null ? vt.getNoCompletadoEn().toString() : null);
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
}
