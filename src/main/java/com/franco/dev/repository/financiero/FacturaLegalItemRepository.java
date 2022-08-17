package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.FacturaLegalItem;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface FacturaLegalItemRepository extends HelperRepository<FacturaLegalItem, Long> {

    default Class<FacturaLegalItem> getEntityClass() {
        return FacturaLegalItem.class;
    }

//    @Query("select m from Moneda m " +
//            "where UPPER(CAST(id as text)) like %?1% or UPPER(denominacion) like %?1%")
//    public List<Moneda> findByAll(String texto);

//    Moneda findByPaisId(Long id);

    public List<FacturaLegalItem> findByFacturaLegalId(Long id);

}