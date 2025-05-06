package com.franco.dev.graphql.personas.resolver;

import com.franco.dev.domain.configuracion.InicioSesion;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Role;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.personas.UsuarioRole;
import com.franco.dev.service.configuracion.InicioSesionService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.RoleService;
import com.franco.dev.service.personas.UsuarioRoleService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    @Autowired
    private InicioSesionService inicioSesionService;

    @Autowired
    private Environment env;

    public List<String> roles(Usuario u){
        List<Role> roleList = roleService.findByUsuarioId(u.getId());
        List<String> roleStringList = new ArrayList<>();
        for(Role r: roleList){
            roleStringList.add(r.getNombre());
        }
        return roleStringList;
    }

    public InicioSesion inicioSesion(Usuario u){
        Page<InicioSesion> inicioSesionPage =
                inicioSesionService.findByUsuarioIdAndHoraFinIsNul(u.getId(), Long.valueOf(env.getProperty("sucursalId")), PageRequest.of(0, 10));
        if(inicioSesionPage.getNumberOfElements() > 0){
            return inicioSesionPage.getContent().get(0);
        } else {
            return null;
        }
    }

}
