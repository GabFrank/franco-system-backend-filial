package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.repository.HelperRepository;

public interface FacturaLegalRepository extends HelperRepository<FacturaLegal, Long> {

    default Class<FormaPago> getEntityClass() {
        return FormaPago.class;
    }

//    @Query("select m from Moneda m " +
//            "where UPPER(CAST(id as text)) like %?1% or UPPER(denominacion) like %?1%")
//    public List<Moneda> findByAll(String texto);

//    Moneda findByPaisId(Long id);

}