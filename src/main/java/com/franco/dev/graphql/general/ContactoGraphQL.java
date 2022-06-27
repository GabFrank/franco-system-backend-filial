package com.franco.dev.graphql.general;

import com.franco.dev.domain.general.Contacto;
import com.franco.dev.domain.general.Pais;
import com.franco.dev.graphql.general.input.ContactoInput;
import com.franco.dev.graphql.general.input.PaisInput;
import com.franco.dev.service.general.ContactoService;
import com.franco.dev.service.general.PaisService;
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
public class ContactoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ContactoService service;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<Contacto> contacto(Long id) {return service.findById(id);}

    public List<Contacto> contactos(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<Contacto> contactoPorTelefonoONombre(String texto){
        return service.findByTelefonoOrNombre(texto);
    }

    public Contacto saveContacto(ContactoInput input){
        ModelMapper m = new ModelMapper();
        Contacto e = m.map(input, Contacto.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteContacto(Long id){
        return service.deleteById(id);
    }

    public Long countContacto(){
        return service.count();
    }

}
