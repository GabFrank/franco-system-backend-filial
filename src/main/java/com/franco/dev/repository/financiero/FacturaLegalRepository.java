package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface FacturaLegalRepository extends HelperRepository<FacturaLegal, Long> {

    default Class<FacturaLegal> getEntityClass() {
        return FacturaLegal.class;
    }

//    @Query("select m from Moneda m " +
//            "where UPPER(CAST(id as text)) like %?1% or UPPER(denominacion) like %?1%")
//    public List<Moneda> findByAll(String texto);

//    Moneda findByPaisId(Long id);

    public List<FacturaLegal> findByCajaId(Long id);

}