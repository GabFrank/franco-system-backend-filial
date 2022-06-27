package com.franco.dev.repository.personas;

import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.UsuarioRole;
import com.franco.dev.repository.HelperRepository;

import java.util.List;

public interface UsuarioRoleRepository extends HelperRepository<UsuarioRole, Long> {

    default Class<UsuarioRole> getEntityClass() {
        return UsuarioRole.class;
    }

    List<UsuarioRole> findByUserId(Long id);

    List<UsuarioRole> findByRoleId(Long id);

}
