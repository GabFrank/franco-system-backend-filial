package com.franco.dev.graphql.personas.resolver;

import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.personas.FuncionarioService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FuncionarioResolver implements GraphQLResolver<Funcionario> {

    @Autowired
    private FuncionarioService personaService;

    @Autowired
    private UsuarioService usuarioService;


    public String nombrePersona(Funcionario e) {
        if(e.getPersona()!=null) return e.getPersona().getNombre();
        return "Sin nombre";
    }

    public String nombreCargo(Funcionario e) {
        if(e.getCargo()!=null) return e.getCargo().getNombre();
        return "Sin nombre";
    }

    public String nombreSupervisor(Funcionario e) {

        if(e.getSupervisadoPor()!=null) return e.getSupervisadoPor().getPersona().getNombre();
        return "Sin supervisor";
    }

    public String nombreSucursal(Funcionario e) {

        if(e.getSucursal()!=null) return e.getSucursal().getNombre();
        return "Sin sucursal";
    }

    public String nickname(Funcionario f){
        Usuario usuario = usuarioService.findByPersonaId(f.getPersona().getId());
        return usuario.getNickname().toUpperCase();
    }

}
