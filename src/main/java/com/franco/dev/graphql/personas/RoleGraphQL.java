package com.franco.dev.graphql.personas;

import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.personas.input.RoleInput;
import com.franco.dev.graphql.personas.input.UsuarioInput;
import com.franco.dev.service.personas.RoleService;
import com.franco.dev.service.personas.UsuarioRoleService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class RoleGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private RoleService service;

    @Autowired
    private UsuarioRoleService usuarioRoleService;

    public Optional<Role> role(Long id) {return service.findById(id);}

    public List<Role> roles(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public Role saveRole(RoleInput input){
        ModelMapper m = new ModelMapper();
        Role e = m.map(input, Role.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteRole(Long id){
        return service.deleteById(id);
    }

    public Long countRole(){
        return service.count();
    }
}
