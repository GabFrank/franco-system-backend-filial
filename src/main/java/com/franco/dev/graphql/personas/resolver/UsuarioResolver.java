package com.franco.dev.graphql.personas.resolver;

import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.personas.UsuarioRole;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.RoleService;
import com.franco.dev.service.personas.UsuarioRoleService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.swing.text.html.Option;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class UsuarioResolver implements GraphQLResolver<Usuario> {

    @Autowired
    private PersonaService personaService;

    @Autowired
    private UsuarioService service;

    @Autowired
    private RoleService roleService;

    public List<String> roles(Usuario u){
        List<Role> roleList = roleService.findByUsuarioId(u.getId());
        List<String> roleStringList = new ArrayList<>();
        for(Role r: roleList){
            roleStringList.add(r.getNombre());
        }
        return roleStringList;
    }

}
