package com.franco.dev.graphql.personas.resolver;

import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Funcionario;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.personas.*;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PersonaResolver implements GraphQLResolver<Persona> {

    @Autowired
    private PersonaService personaService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private FuncionarioService funcionarioService;

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private ProveedorService proveedorService;

    public Boolean isFuncionario(Persona p){
        return funcionarioService.findByPersonaId(p.getId())!=null;
    }

    public Boolean isCliente(Persona p){
        return clienteService.findByPersonaId(p.getId())!=null;
    }

    public Boolean isProveedor(Persona p){
        return proveedorService.findByPersonaId(p.getId())!=null;
    }
}
