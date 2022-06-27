package com.franco.dev.graphql.general;

import com.franco.dev.domain.general.Pais;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.graphql.general.input.PaisInput;
import com.franco.dev.graphql.personas.input.ClienteInput;
import com.franco.dev.graphql.personas.input.ClienteUpdateInput;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.personas.ClienteService;
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
public class PaisGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PaisService service;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<Pais> pais(Long id) {return service.findById(id);}

    public List<Pais> paises(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<Pais> paisesSearch(String texto){
        return service.findByAll(texto);
    }


    public Pais savePais(PaisInput input){
        ModelMapper m = new ModelMapper();
        Pais e = m.map(input, Pais.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        return service.save(e);
    }

    public Boolean deletePais(Long id){
        return service.deleteById(id);
    }

    public Long countPais(){
        return service.count();
    }


}
