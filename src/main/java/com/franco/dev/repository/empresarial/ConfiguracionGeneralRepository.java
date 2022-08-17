package com.franco.dev.repository.empresarial;

import com.franco.dev.domain.empresarial.ConfiguracionGeneral;
import com.franco.dev.repository.HelperRepository;

public interface ConfiguracionGeneralRepository extends HelperRepository<ConfiguracionGeneral, Long> {

    default Class<ConfiguracionGeneral> getEntityClass() {
        return ConfiguracionGeneral.class;
    }

}