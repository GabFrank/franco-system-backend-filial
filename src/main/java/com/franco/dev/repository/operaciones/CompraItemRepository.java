package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Compra;
import com.franco.dev.domain.operaciones.CompraItem;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CompraItemRepository extends HelperRepository<CompraItem, Long> {
    default Class<CompraItem> getEntityClass() {
        return CompraItem.class;
    }

    public List<CompraItem> findByCompraId(Long id);

    public List<CompraItem> findByProductoIdOrderByCreadoEnDesc(Long id);

    public CompraItem findByPedidoItemId(Long id);

    @Query(value = "select * from operaciones.compra_item ci " +
            "left join operaciones.pedido_item pi2 on pi2.id = ci.pedido_item_id " +
            "left join operaciones.nota_recepcion nr on nr.id = pi2.nota_recepcion_id " +
            "where nr.id = ?1", nativeQuery = true)
    public List<CompraItem> findByNotaRecepcionId(Long id);

    //@Query("select p from Producto p where CAST(id as text) like %?1% or UPPER(p.descripcion) like %?1% or LOWER(p.descripcionFactura) like %?1%")
    //public List<Producto> findbyAll(String texto);
}