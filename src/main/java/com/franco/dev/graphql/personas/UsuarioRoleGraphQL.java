package com.franco.dev.graphql.personas;

import com.franco.dev.domain.personas.UsuarioRole;
import com.franco.dev.graphql.personas.input.UsuarioRoleInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.personas.RoleService;
import com.franco.dev.service.personas.UsuarioRoleService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class UsuarioRoleGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private UsuarioRoleService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private PropagacionService propagacionService;

    public List<UsuarioRole> usuarioRolePorUsuarioId(Long id) {return service.findByUserId(id);}

    public UsuarioRole saveUsuarioRole(UsuarioRoleInput input){
        UsuarioRole e = new UsuarioRole();
        if(input.getId()!=null) e.setId(input.getId());
        e.setUser(usuarioService.findById(input.getUserId()).orElse(null));
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setRole(roleService.findById(input.getRoleId()).orElse(null));
        e = service.save(e);
        return e;
    }

    public Boolean deleteUsuarioRole(Long id){
        Boolean ok = service.deleteById(id);
        return ok;
    }

    public Long countUsuarioRole(){
        return service.count();
    }

}
