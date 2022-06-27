package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.Conteo;
import com.franco.dev.domain.financiero.ConteoMoneda;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ConteoMonedaRepository extends HelperRepository<ConteoMoneda, Long> {

    default Class<ConteoMoneda> getEntityClass() {
        return ConteoMoneda.class;
    }

//    @Query("select m from ConteoMoneda m " +
//            "where UPPER(CAST(id as text)) like %?1% or UPPER(nombre) like %?1% or UPPER(codigo) like %?1%")
//    public List<ConteoMoneda> findByAll(String texto);

    public List<ConteoMoneda> findByConteoId(Long id);

//    Moneda findByPaisId(Long id);

}