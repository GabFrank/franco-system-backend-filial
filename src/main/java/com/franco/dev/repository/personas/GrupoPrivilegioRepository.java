package com.franco.dev.repository.personas;

import com.franco.dev.domain.personas.GrupoPrivilegio;
import com.franco.dev.domain.personas.Role;
import com.franco.dev.repository.HelperRepository;

public interface GrupoPrivilegioRepository extends HelperRepository<GrupoPrivilegio, Long> {

    default Class<GrupoPrivilegio> getEntityClass() {
        return GrupoPrivilegio.class;
    }

}
