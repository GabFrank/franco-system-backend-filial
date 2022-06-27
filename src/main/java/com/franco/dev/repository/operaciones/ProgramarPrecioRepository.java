package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.ProgramarPrecio;
import com.franco.dev.repository.HelperRepository;

public interface ProgramarPrecioRepository extends HelperRepository<ProgramarPrecio, Long> {
    default Class<ProgramarPrecio> getEntityClass() {
        return ProgramarPrecio.class;
    }

//    public List<ProgramarPrecio> findByProveedorPersonaNombreContainingIgnoreCase(String texto);

//    @Query("select p from Venta p left outer join p.proveedor as pro left outer join pro.persona as per where LOWER(per.nombre) like %?1%")
//    public List<Venta> findByProveedor(String texto);

    //@Query("select p from Producto p where CAST(id as text) like %?1% or LOWER(p.descripcion) like %?1% or LOWER(p.descripcionFactura) like %?1%")
    //public List<Producto> findbyAll(String texto);
}