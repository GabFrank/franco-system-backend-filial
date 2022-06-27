package com.franco.dev.service.personas;

import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.personas.RoleRepository;
import com.franco.dev.repository.personas.UsuarioRepository;
import com.franco.dev.service.CrudService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class RoleService extends CrudService<Role, RoleRepository> {

    @Autowired
    private final RoleRepository repository;

    @Override
    public RoleRepository getRepository() {
        return repository;
    }

    public Role findByNombre(String email){
        return repository.findByNombre(email);
    }

    public List<Role> findByUsuarioId(Long id){
        return repository.findByUsuarioId(id);
    }

}
