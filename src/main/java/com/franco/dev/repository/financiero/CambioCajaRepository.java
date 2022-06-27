package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.Banco;
import com.franco.dev.domain.financiero.CambioCaja;
import com.franco.dev.repository.HelperRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CambioCajaRepository extends HelperRepository<CambioCaja, Long> {

    default Class<CambioCaja> getEntityClass() {
        return CambioCaja.class;
    }

//    @Query("select m from CambioCaja m " +
//            "where UPPER(CAST(id as text)) like %?1% or UPPER(nombre) like %?1% or UPPER(codigo) like %?1%")
//    public List<CambioCaja> findByAll(String texto);

//    Moneda findByPaisId(Long id);

}