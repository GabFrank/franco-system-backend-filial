package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MonedaRepository extends HelperRepository<Moneda, Long> {

    default Class<Moneda> getEntityClass() {
        return Moneda.class;
    }

    @Query("select m from Moneda m " +
            "where UPPER(CAST(id as text)) like %?1% or UPPER(denominacion) like %?1%")
    public List<Moneda> findByAll(String texto);

    public Moneda findByDenominacion(String texto);

    List<Moneda> findAllByOrderByIdAsc();

    //    Moneda findByPaisId(Long id);

}