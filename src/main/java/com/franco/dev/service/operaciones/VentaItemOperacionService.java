package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.eventos.EventoEmisor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Eliminar un item de una venta en una sola transaccion (filial #246, plan de eventos §8). Antes
 * VentaItemGraphQL.deleteVentaItem corria sin TX: cada saveAndSend commiteaba por su lado y, si el
 * borrado fallaba, los movimientos de stock ya quedaban desactivados. Ahora o pasa todo o nada.
 *
 * El aviso venta.item_eliminado se anota recien despues de confirmar el borrado (flush), dentro de
 * la misma TX: si la TX se revierte, el aviso tampoco queda.
 */
@Service
public class VentaItemOperacionService {

    private final VentaItemService ventaItemService;
    private final MovimientoStockService movimientoStockService;
    private final EventoEmisor emisor;

    @PersistenceContext
    private EntityManager em;

    public VentaItemOperacionService(VentaItemService ventaItemService, MovimientoStockService movimientoStockService,
                                     EventoEmisor emisor) {
        this.ventaItemService = ventaItemService;
        this.movimientoStockService = movimientoStockService;
        this.emisor = emisor;
    }

    void setEntityManager(EntityManager em) {
        this.em = em;
    }

    /**
     * @param recalcularTotal recalcula el total de la venta dentro de la misma TX (lo pasa el resolver)
     * @return false si el item no existe o el borrado no se pudo hacer; en ese caso no se toca nada
     */
    @Transactional
    public boolean eliminar(Long id, Consumer<Venta> recalcularTotal) {
        VentaItem ventaItem = ventaItemService.findById(id).orElse(null);
        if (ventaItem == null) return false;
        Venta venta = ventaItem.getVenta();

        // Antes de eliminar el VentaItem, desactivar sus movimientos de stock de VENTA para que no queden huérfanos
        List<MovimientoStock> movimientosStock = movimientoStockService.findByReferencia(id);
        for (MovimientoStock ms : movimientosStock) {
            if (ms.getTipoMovimiento() == TipoMovimiento.VENTA && ms.getEstado() != null && ms.getEstado()) {
                ms.setEstado(false);
                movimientoStockService.saveAndSend(ms, false);
            }
        }
        if (!Boolean.TRUE.equals(ventaItemService.deleteById(id))) {
            throw new BorradoFallido(id);
        }
        // Confirma el DELETE contra la base antes de avisar. Si falla, la TX entera se revierte.
        em.flush();

        if (venta != null && recalcularTotal != null) recalcularTotal.accept(venta);

        emisor.emitir("venta.item_eliminado", "media",
                ventaItem.getUsuario() != null ? ventaItem.getUsuario().getId() : null,
                venta != null ? "venta/" + venta.getId() : "venta-item/" + id,
                datos(ventaItem, venta));
        return true;
    }

    static Map<String, Object> datos(VentaItem vi, Venta venta) {
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

    /** Hace que la TX se revierta entera (los movimientos de stock vuelven a quedar activos). */
    public static class BorradoFallido extends RuntimeException {
        public BorradoFallido(Long id) {
            super("No se pudo eliminar el item " + id);
        }
    }
}
