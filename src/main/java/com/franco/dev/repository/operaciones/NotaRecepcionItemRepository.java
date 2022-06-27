package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.NotaRecepcion;
import com.franco.dev.domain.operaciones.NotaRecepcionItem;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NotaRecepcionItemRepository extends HelperRepository<NotaRecepcionItem, Long> {
    default Class<NotaRecepcionItem> getEntityClass() {
        return NotaRecepcionItem.class;
    }

    public List<NotaRecepcionItem> findByNotaRecepcionId(Long id);
//
//    @Query("select p from Pedido p left outer join p.proveedor as pro left outer join pro.persona as per where LOWER(per.nombre) like %?1%")
//    public List<Pedido> findByProveedor(String texto);
//
//    //@Query("select p from Producto p where CAST(id as text) like %?1% or LOWER(p.descripcion) like %?1% or LOWER(p.descripcionFactura) like %?1%")
//    //public List<Producto> findbyAll(String texto);

    @Query( value = "select * from operaciones.pedido_item pi2 " +
            "left join operaciones.nota_recepcion_item nri on nri.pedido_item_id = pi2.id " +
            "where pi2.pedido_id = ?1 and nri.pedido_item_id isnull", nativeQuery = true)
    public List<NotaRecepcionItem> findByPedidoIdAndPedidoItemIsNull(Long id);
}