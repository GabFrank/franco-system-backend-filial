package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.operaciones.Vuelto;
import com.franco.dev.domain.operaciones.VueltoItem;
import com.franco.dev.graphql.operaciones.input.VueltoItemInput;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface VueltoItemRepository extends HelperRepository<VueltoItem, Long> {
    default Class<VueltoItem> getEntityClass() {
        return VueltoItem.class;
    }

    public List<VueltoItem> findByVueltoId(Long id);
//
//    @Query("select p from Pedido p left outer join p.proveedor as pro left outer join pro.persona as per where LOWER(per.nombre) like %?1%")
//    public List<Pedido> findByProveedor(String texto);
//
//    //@Query("select p from Producto p where CAST(id as text) like %?1% or LOWER(p.descripcion) like %?1% or LOWER(p.descripcionFactura) like %?1%")
//    //public List<Producto> findbyAll(String texto);
}