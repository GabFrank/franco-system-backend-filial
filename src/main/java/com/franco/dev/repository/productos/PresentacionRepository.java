package com.franco.dev.repository.productos;

import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.domain.productos.TipoPrecio;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PresentacionRepository extends HelperRepository<Presentacion, Long> {

    default Class<Presentacion> getEntityClass() {
        return Presentacion.class;
    }

    @Query("select f from Presentacion f where CAST(id as text) like %?1% or UPPER(descripcion) like %?1%")
    public List<Presentacion> findByAll(String texto);

    public List<Presentacion> findByProductoId(Long id);

    public Presentacion findByPrincipalAndProductoId(Boolean principal, Long id);

}
