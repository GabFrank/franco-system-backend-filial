package com.franco.dev.repository.financiero;

import com.franco.dev.domain.financiero.MonedaBilletes;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface MonedaBilleteRepository extends HelperRepository<MonedaBilletes, Long> {

    default Class<MonedaBilletes> getEntityClass() {
        return MonedaBilletes.class;
    }

    public List<MonedaBilletes> findByMonedaId(Long id);

}