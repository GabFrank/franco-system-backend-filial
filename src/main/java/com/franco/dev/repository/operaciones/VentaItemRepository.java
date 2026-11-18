package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VentaItemRepository extends HelperRepository<VentaItem, Long> {

    default Class<VentaItem> getEntityClass() {
        return VentaItem.class;
    }

    public List<VentaItem> findByVentaId(Long id);

    @Query(value = "select sum(vi.cantidad * vi.precio) from operaciones.venta_item vi " +
            "where vi.venta_id = ?1 and vi.sucursal_id = ?2", nativeQuery = true)
    Double totalByVentaIdAndSucId(Long id, Long sucId);

    /**
     * Descuento total de una venta (descuento unitario por cantidad), como lo sumaba
     * VentaResolver.valorDescuento en Java pero sin cargar los items: VentaItem trae en EAGER el
     * producto, la presentacion y el precio de cada uno. Mismo universo que antes: todos los items
     * de la venta con descuento, sin mirar activo.
     */
    @Query(value = "select coalesce(sum(vi.descuento_unitario * vi.cantidad), 0) from operaciones.venta_item vi " +
            "where vi.venta_id = ?1 and vi.descuento_unitario is not null", nativeQuery = true)
    Double totalDescuentoByVentaId(Long id);

    /**
     * Obtiene VentaItems por IDs con la relación Venta cargada (fetch join).
     * 
     * @param ids
     * @return
     */
    @Query("SELECT vi FROM VentaItem vi LEFT JOIN FETCH vi.venta WHERE vi.id IN :ids")
    List<VentaItem> findAllByIdWithVenta(@Param("ids") List<Long> ids);
}