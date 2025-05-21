package com.franco.dev.repository.operaciones;

import java.util.List;

import org.springframework.data.jpa.repository.Query;

import com.franco.dev.domain.operaciones.PrecioDelivery;
import com.franco.dev.repository.HelperRepository;

public interface PrecioDeliveryRepository extends HelperRepository<PrecioDelivery, Long> {
    default Class<PrecioDelivery> getEntityClass() {
        return PrecioDelivery.class;
    }

    // return a list of PrecioDelivery where active is true, order by id asc
    List<PrecioDelivery> findAllByActivoTrueOrderByIdAsc();

//    public List<PrecioDelivery> findByProveedorPersonaNombreContainingIgnoreCase(String texto);

//    @Query("select p from PrecioDelivery p left outer join p.proveedor as pro left outer join pro.persona as per where LOWER(per.nombre) like %?1%")
//    public List<PrecioDelivery> findByProveedor(String texto);

    //@Query("select p from Producto p where CAST(id as text) like %?1% or LOWER(p.descripcion) like %?1% or LOWER(p.descripcionFactura) like %?1%")
    //public List<Producto> findbyAll(String texto);
}