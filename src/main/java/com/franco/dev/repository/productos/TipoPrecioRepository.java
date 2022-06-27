package com.franco.dev.repository.productos;

import com.franco.dev.domain.productos.Familia;
import com.franco.dev.domain.productos.TipoPrecio;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TipoPrecioRepository extends HelperRepository<TipoPrecio, Long> {

    default Class<TipoPrecio> getEntityClass() {
        return TipoPrecio.class;
    }

    @Query("select f from TipoPrecio f where CAST(id as text) like %?1% or UPPER(descripcion) like %?1%")
    public List<TipoPrecio> findByAll(String texto);

}
