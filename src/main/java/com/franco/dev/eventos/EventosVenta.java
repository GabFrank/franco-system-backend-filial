package com.franco.dev.eventos;

import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.operaciones.enums.VentaEstado;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /** La venta paso a CONCLUIDA en esta operacion (y no lo estaba antes): solo ahi sale venta.concluida. */
    public static boolean concluyeAhora(VentaEstado anterior, Venta venta) {
        return venta != null && venta.getEstado() == VentaEstado.CONCLUIDA && anterior != VentaEstado.CONCLUIDA;
    }

    /**
     * Proyeccion v1 de venta.concluida / venta.cobro_editado, con los nombres de la consulta del cliente.
     * Se arma solo con lo que la operacion ya tiene cargado. valorTotal y valorDescuento se calculan de
     * los items (Σ cantidad×precio y Σ cantidad×valorDescuento). Proyeccion.limpiar saca lo que no va.
     */
    public static Map<String, Object> venta(Venta v, List<VentaItem> items, List<CobroDetalle> detalles) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", v.getId());
        d.put("estado", v.getEstado() != null ? v.getEstado().name() : null);
        d.put("creadoEn", v.getCreadoEn() != null ? v.getCreadoEn().toString() : null);
        d.put("sucursalId", v.getSucursalId());
        d.put("totalGs", v.getTotalGs());
        d.put("totalRs", v.getTotalRs());
        d.put("totalDs", v.getTotalDs());
        d.put("esDelivery", v.getDelivery() != null);
        if (v.getUsuario() != null) d.put("usuario", usuario(v.getUsuario()));
        if (v.getCliente() != null) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("id", v.getCliente().getId());
            c.put("codigo", v.getCliente().getCodigo());
            if (v.getCliente().getPersona() != null) {
                c.put("nombre", v.getCliente().getPersona().getNombre());
                c.put("persona", mapa("nombre", v.getCliente().getPersona().getNombre()));
            }
            d.put("cliente", c);
        }
        if (v.getCaja() != null) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("id", v.getCaja().getId());
            c.put("fechaApertura", v.getCaja().getFechaApertura() != null ? v.getCaja().getFechaApertura().toString() : null);
            c.put("fechaCierre", v.getCaja().getFechaCierre() != null ? v.getCaja().getFechaCierre().toString() : null);
            if (v.getCaja().getUsuario() != null) c.put("usuario", mapa("nickname", v.getCaja().getUsuario().getNickname()));
            d.put("caja", c);
        }
        Map<String, Object> cobro = new LinkedHashMap<>();
        if (v.getCobro() != null) cobro.put("id", v.getCobro().getId());
        List<Map<String, Object>> lineas = new ArrayList<>();
        if (detalles != null) {
            for (CobroDetalle cd : detalles) {
                Map<String, Object> l = new LinkedHashMap<>();
                l.put("id", cd.getId());
                l.put("valor", cd.getValor());
                l.put("cambio", cd.getCambio());
                l.put("pago", cd.getPago());
                l.put("vuelto", cd.getVuelto());
                l.put("descuento", cd.getDescuento());
                l.put("aumento", cd.getAumento());
                l.put("identificadorTransaccion", cd.getIdentificadorTransaccion());
                if (cd.getMoneda() != null) l.put("moneda", mapa("denominacion", cd.getMoneda().getDenominacion()));
                if (cd.getFormaPago() != null) l.put("formaPago", mapa("descripcion", cd.getFormaPago().getDescripcion()));
                lineas.add(l);
            }
        }
        cobro.put("cobroDetalleList", lineas);
        d.put("cobro", cobro);
        if (items != null) {
            double total = 0, desc = 0;
            List<Map<String, Object>> li = new ArrayList<>();
            for (VentaItem vi : items) {
                Map<String, Object> l = new LinkedHashMap<>();
                l.put("id", vi.getId());
                l.put("cantidad", vi.getCantidad());
                l.put("precio", vi.getPrecio());
                l.put("valorDescuento", vi.getValorDescuento());
                if (vi.getCantidad() != null && vi.getPrecio() != null) {
                    l.put("valorTotal", vi.getCantidad() * vi.getPrecio());
                    total += vi.getCantidad() * vi.getPrecio();
                }
                if (vi.getCantidad() != null && vi.getValorDescuento() != null) desc += vi.getCantidad() * vi.getValorDescuento();
                if (vi.getProducto() != null) {
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("id", vi.getProducto().getId());
                    p.put("descripcion", vi.getProducto().getDescripcion());
                    l.put("producto", p);
                }
                if (vi.getPresentacion() != null) {
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("id", vi.getPresentacion().getId());
                    p.put("descripcion", vi.getPresentacion().getDescripcion());
                    l.put("presentacion", p);
                }
                li.add(l);
            }
            d.put("valorTotal", total);
            d.put("valorDescuento", desc);
            d.put("ventaItemList", li);
        }
        return d;
    }

    /** Atributos de filtro de la venta: formas de pago y monedas cobradas (sin vueltos), total y caja. */
    public static Map<String, Object> atributosVenta(Venta v, List<CobroDetalle> detalles) {
        Set<String> formas = new LinkedHashSet<>();
        Set<String> monedas = new LinkedHashSet<>();
        if (detalles != null) {
            for (CobroDetalle cd : detalles) {
                if (Boolean.TRUE.equals(cd.getVuelto())) continue;
                if (cd.getFormaPago() != null && cd.getFormaPago().getDescripcion() != null)
                    formas.add(cd.getFormaPago().getDescripcion().trim().toUpperCase());
                if (cd.getMoneda() != null && cd.getMoneda().getDenominacion() != null)
                    monedas.add(cd.getMoneda().getDenominacion().trim().toUpperCase());
            }
        }
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("formas_pago", new ArrayList<>(formas));
        a.put("monedas", new ArrayList<>(monedas));
        a.put("total_gs", v.getTotalGs());
        a.put("caja_id", v.getCaja() != null ? v.getCaja().getId() : null);
        a.put("es_delivery", v.getDelivery() != null);
        return a;
    }

    public static Map<String, Object> usuario(com.franco.dev.domain.personas.Usuario u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("nickname", u.getNickname());
        return m;
    }

    public static Map<String, Object> mapa(String k, Object v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k, v);
        return m;
    }

    /** venta.items_cancelados (fase 2, #246): los items que estaban activos antes de cancelar. */
    public static Map<String, Object> itemsCancelados(Long ventaId, Venta venta, List<VentaItem> items) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("venta", mapa("id", ventaId));
        if (venta != null && venta.getCaja() != null) d.put("caja", mapa("id", venta.getCaja().getId()));
        if (venta != null && venta.getUsuario() != null) d.put("usuario", mapa("id", venta.getUsuario().getId()));
        List<Map<String, Object>> l = new java.util.ArrayList<>();
        for (VentaItem vi : items) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", vi.getId());
            if (vi.getProducto() != null) m.put("producto", mapa("id", vi.getProducto().getId()));
            if (vi.getPresentacion() != null) m.put("presentacion", mapa("id", vi.getPresentacion().getId()));
            m.put("cantidad", vi.getCantidad());
            m.put("precio", vi.getPrecio());
            m.put("valorTotal", vi.getCantidad() != null && vi.getPrecio() != null ? vi.getCantidad() * vi.getPrecio() : null);
            l.add(m);
        }
        d.put("items", l);
        return d;
    }
}
