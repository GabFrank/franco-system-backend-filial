package com.franco.dev.graphql.personas.resolver;

import com.franco.dev.domain.general.Contacto;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Persona;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.general.ContactoService;
import com.franco.dev.service.personas.PersonaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ClienteResolver implements GraphQLResolver<Cliente> {

    @Autowired
    private PersonaService personaService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ContactoService contactoService;

    public List<Contacto> contactos(Cliente e){
        return contactoService.findByPersonaId(e.getPersona().getId());
    }

    public String nombre(Cliente e){ return e.getPersona().getNombre(); }

    public Optional<Usuario> usuarioId(Cliente e){
        return usuarioService.findById(e.getUsuarioId().getId());
    }

}
