package com.franco.dev.repository.personas;

import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.UsuarioGrupo;
import com.franco.dev.repository.HelperRepository;

public interface UsuarioGrupoRepository extends HelperRepository<UsuarioGrupo, Long> {

    default Class<UsuarioGrupo> getEntityClass() {
        return UsuarioGrupo.class;
    }

}
