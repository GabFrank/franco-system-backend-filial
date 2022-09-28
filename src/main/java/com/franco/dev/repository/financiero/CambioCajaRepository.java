package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.CambioCaja;
import com.franco.dev.repository.HelperRepository;

public interface CambioCajaRepository extends HelperRepository<CambioCaja, Long> {

    default Class<CambioCaja> getEntityClass() {
        return CambioCaja.class;
    }

}