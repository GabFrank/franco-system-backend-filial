package com.franco.dev.repository.personas;

import com.franco.dev.domain.personas.GrupoRole;
import com.franco.dev.repository.HelperRepository;

public interface GrupoPrivilegioRepository extends HelperRepository<GrupoRole, Long> {

    default Class<GrupoRole> getEntityClass() {
        return GrupoRole.class;
    }

}
