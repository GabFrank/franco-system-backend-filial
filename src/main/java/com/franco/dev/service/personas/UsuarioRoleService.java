package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.UsuarioRole;
import com.franco.dev.repository.personas.RoleRepository;
import com.franco.dev.repository.personas.UsuarioRoleRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class UsuarioRoleService extends CrudService<UsuarioRole, UsuarioRoleRepository> {

    @Autowired
    private final UsuarioRoleRepository repository;

    @Override
    public UsuarioRoleRepository getRepository() {
        return repository;
    }


    public List<UsuarioRole> findByUserId(Long id) {
        return repository.findByUserId(id);
    }

    public List<UsuarioRole> findByRoleId(Long id){
        return repository.findByRoleId(id);
    }
}
