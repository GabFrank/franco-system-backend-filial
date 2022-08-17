package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.Timbrado;
import com.franco.dev.repository.HelperRepository;

public interface TimbradoRepository extends HelperRepository<Timbrado, Long> {

    default Class<Timbrado> getEntityClass() {
        return Timbrado.class;
    }

}