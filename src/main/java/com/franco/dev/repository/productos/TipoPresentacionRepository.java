package com.franco.dev.repository.productos;

import com.franco.dev.domain.productos.TipoPrecio;
import com.franco.dev.domain.productos.TipoPresentacion;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TipoPresentacionRepository extends HelperRepository<TipoPresentacion, Long> {

    default Class<TipoPresentacion> getEntityClass() {
        return TipoPresentacion.class;
    }

    @Query("select f from TipoPresentacion f where CAST(id as text) like %?1% or UPPER(descripcion) like %?1%")
    public List<TipoPresentacion> findByAll(String texto);

}
