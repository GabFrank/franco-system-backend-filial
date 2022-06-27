package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.EntradaItem;
import com.franco.dev.domain.operaciones.SalidaItem;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface SalidaItemRepository extends HelperRepository<SalidaItem, Long> {
    default Class<SalidaItem> getEntityClass() {
        return SalidaItem.class;
    }

    public List<SalidaItem> findBySalidaId(Long id);

//    @Query("select p from Venta p left outer join p.proveedor as pro left outer join pro.persona as per where LOWER(per.nombre) like %?1%")
//    public List<Venta> findByProveedor(String texto);

    //@Query("select p from Producto p where CAST(id as text) like %?1% or LOWER(p.descripcion) like %?1% or LOWER(p.descripcionFactura) like %?1%")
    //public List<Producto> findbyAll(String texto);
}