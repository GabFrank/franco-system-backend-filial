package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface VentaRepository extends HelperRepository<Venta, Long> {
    default Class<Venta> getEntityClass() {
        return Venta.class;
    }

//    public List<Venta> findByProveedorPersonaNombreContainingIgnoreCase(String texto);

//    @Query("select p from Venta p left outer join p.proveedor as pro left outer join pro.persona as per where LOWER(per.nombre) like %?1%")
//    public List<Venta> findByProveedor(String texto);

    //@Query("select p from Producto p where CAST(id as text) like %?1% or LOWER(p.descripcion) like %?1% or LOWER(p.descripcionFactura) like %?1%")
    //public List<Producto> findbyAll(String texto);

    public List<Venta> findByCajaIdOrderByIdAsc(Long id, Pageable pageable);

    public List<Venta> findByCajaIdOrderByIdDesc(Long id, Pageable pageable);

    public List<Venta> findByCajaId(Long id);

    public List<Venta> findByCreadoEnBetween(LocalDateTime inicio, LocalDateTime fin);

}