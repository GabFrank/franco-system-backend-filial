package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.ConfiguracionCobro;
import com.franco.dev.repository.HelperRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConfiguracionCobroRepository extends HelperRepository<ConfiguracionCobro, Long> {

    default Class<ConfiguracionCobro> getEntityClass() {
        return ConfiguracionCobro.class;
    }

    List<ConfiguracionCobro> findAllByOrderByIdAsc();
}
