package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.PedidoItem;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PedidoItemRepository extends HelperRepository<PedidoItem, Long> {
    default Class<PedidoItem> getEntityClass() {
        return PedidoItem.class;
    }

    public List<PedidoItem> findByPedidoId(Long id);

    public List<PedidoItem> findByProductoId(Long id);

    //@Query("select p from Producto p where CAST(id as text) like %?1% or UPPER(p.descripcion) like %?1% or UPPER(p.descripcionFactura) like %?1%")
    //public List<Producto> findbyAll(String texto);

    @Query(value = "select * from operaciones.pedido_item pi2 " +
            "where pi2.pedido_id = ?1 and pi2.nota_recepcion_id isnull", nativeQuery = true)
    public List<PedidoItem> findByPedidoIdSobrantes(Long id);


    public List<PedidoItem> findByNotaRecepcionId(Long id);
}