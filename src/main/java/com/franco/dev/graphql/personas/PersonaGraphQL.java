package com.franco.dev.graphql.personas;

import com.franco.dev.domain.personas.Persona;
import com.franco.dev.graphql.personas.input.PersonaInput;
import com.franco.dev.service.personas.PersonaService;
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
public class PersonaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PersonaService service;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<Persona> persona(Long id) {return service.findById(id);}

    public List<Persona> personaSearch(String texto) {return service.findByAll(texto);}

    public List<Persona> personas(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public Persona savePersona(PersonaInput input){
        ModelMapper m = new ModelMapper();
        Persona e = m.map(input, Persona.class);
        if(input.getUsuarioId()!=null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        return service.save(e);
    }

    public Persona updatePersona(Long id, PersonaInput personaInput){
        ModelMapper m = new ModelMapper();
        Persona persona = service.getOne(id);
        persona = m.map(personaInput, Persona.class);
        return service.save(persona);
    }

    public Boolean deletePersona(Long id){
        return service.deleteById(id);
    }

    public Long countPersona(){
        return service.count();
    }
}
