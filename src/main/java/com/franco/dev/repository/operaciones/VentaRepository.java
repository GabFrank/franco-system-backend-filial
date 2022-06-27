package com.franco.dev.repository.operaciones;

import com.franco.dev.domain.operaciones.Pedido;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.dto.VentaPorPeriodoV1Dto;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.repository.HelperRepository;
import org.apache.tomcat.jni.Local;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Date;
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

    @Query(value = "select * from operaciones.venta v " +
            "where v.caja_id = ?1 " +
            "ORDER BY v.id desc " +
            "limit 20 " +
            "offset ?2", nativeQuery = true)
    public List<Venta> findByCajaId(Long id, int offset);

    public List<Venta> findByCajaId(Long id);

    @Query(value = "select " +
            "* " +
            "from " +
            "operaciones.venta v " +
            "where " +
            "v.creado_en between cast(?1 as timestamp) and cast(?2 as timestamp)" +
            "order by " +
            "v.id", nativeQuery = true)
    public List<Venta> ventaPorPeriodo(LocalDateTime inicio, LocalDateTime fin);

}