package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface VentaItemRepository extends HelperRepository<VentaItem, Long> {

    default Class<VentaItem> getEntityClass() {
        return VentaItem.class;
    }

//    public List<Venta> findByProveedorPersonaNombreContainingIgnoreCase(String texto);

//    @Query("select p from Venta p left outer join p.proveedor as pro left outer join pro.persona as per where LOWER(per.nombre) like %?1%")
//    public List<Venta> findByProveedor(String texto);

    //@Query("select p from Producto p where CAST(id as text) like %?1% or UPPER(p.descripcion) like %?1% or UPPER(p.descripcionFactura) like %?1%")
    //public List<Producto> findbyAll(String texto);

    public List<VentaItem> findByVentaId(Long id);


}