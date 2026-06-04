package com.franco.dev.repository.activos;

import com.franco.dev.domain.activos.Ente;
import com.franco.dev.repository.HelperRepository;

public interface EnteRepository extends HelperRepository<Ente, Long> {

    default Class<Ente> getEntityClass() {
        return Ente.class;
    }
}
