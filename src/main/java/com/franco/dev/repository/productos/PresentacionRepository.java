package com.franco.dev.repository.productos;

import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PresentacionRepository extends HelperRepository<Presentacion, Long> {

    default Class<Presentacion> getEntityClass() {
        return Presentacion.class;
    }

    @Query("select f from Presentacion f where CAST(id as text) like %?1% or UPPER(descripcion) like %?1%")
    public List<Presentacion> findByAll(String texto);

    public List<Presentacion> findByProductoId(Long id);

    public Presentacion findByPrincipalAndProductoId(Boolean principal, Long id);

    List<Presentacion> findByProductoIdOrderByCantidadAsc(Long id);

    @Query("SELECT p FROM Presentacion p WHERE p.producto.id = :productoId ORDER BY p.cantidad ASC")
    List<Presentacion> findByProductoIdOrderedByCantidad(@Param("productoId") Long productoId);
}