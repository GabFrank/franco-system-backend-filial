package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.SencilloDetalle;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SencilloDetalleRepository extends HelperRepository<SencilloDetalle, Long> {

    default Class<SencilloDetalle> getEntityClass() {
        return SencilloDetalle.class;
    }

//    @Query("select m from SencilloDetalle m " +
//            "where UPPER(CAST(id as text)) like %?1% or UPPER(nombre) like %?1% or UPPER(codigo) like %?1%")
//    public List<SencilloDetalle> findByAll(String texto);

    public List<SencilloDetalle> findBySencilloId(Long id);

//    Moneda findByPaisId(Long id);

}