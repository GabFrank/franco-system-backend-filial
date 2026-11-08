package com.franco.dev.eventos;

import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;

import java.util.LinkedHashMap;
import java.util.Map;

/** Datos de los eventos de venta, solo con campos de la lista blanca. */
public final class EventosVenta {

    private EventosVenta() {
    }

    public static Map<String, Object> itemEliminado(VentaItem vi, Venta venta) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("venta_item_id", vi.getId());
        if (venta != null) {
            d.put("venta_id", venta.getId());
            if (venta.getCaja() != null) d.put("caja_id", venta.getCaja().getId());
            if (venta.getEstado() != null) d.put("venta_estado", venta.getEstado().name());
        }
        if (vi.getProducto() != null) d.put("producto_id", vi.getProducto().getId());
        if (vi.getPresentacion() != null) d.put("presentacion_id", vi.getPresentacion().getId());
        d.put("cantidad", vi.getCantidad());
        d.put("precio", vi.getPrecio());
        if (vi.getCantidad() != null && vi.getPrecio() != null) d.put("valor_total", vi.getCantidad() * vi.getPrecio());
        return d;
    }
}
