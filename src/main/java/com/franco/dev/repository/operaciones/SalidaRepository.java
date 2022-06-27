package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Salida;
import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.Salida;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SalidaRepository extends HelperRepository<Salida, Long> {
    default Class<Salida> getEntityClass() {
        return Salida.class;
    }

//    public List<Venta> findByProveedorPe                         rsonaNombreContainingIgnoreCase(String texto);

//    @Query("select p from Venta p left outer join p.proveedor as pro left outer join pro.persona as per where LOWER(per.nombre) like %?1%")
//    public List<Venta> findByProveedor(String texto);

    //@Query("select p from Producto p where CAST(id as text) like %?1% or LOWER(p.descripcion) like %?1% or LOWER(p.descripcionFactura) like %?1%")
    //public List<Producto> findbyAll(String texto);

    @Query(value = "select * from operaciones.salida ms \n" +
            "where ms.creado_en between cast(?1 as timestamp) and cast(?2 as timestamp)", nativeQuery = true)
    public List<Salida> findByDate(String inicio, String fin);
}