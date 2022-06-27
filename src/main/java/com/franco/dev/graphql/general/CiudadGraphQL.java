package com.franco.dev.graphql.general;

import com.franco.dev.domain.general.Ciudad;
import com.franco.dev.domain.general.Pais;
import com.franco.dev.graphql.general.input.CiudadInput;
import com.franco.dev.graphql.general.input.PaisInput;
import com.franco.dev.service.general.CiudadService;
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
public class CiudadGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private CiudadService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PaisService ciudadService;

    public Optional<Ciudad> ciudad(Long id) {return service.findById(id);}

    public List<Ciudad> ciudades(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<Ciudad> ciudadesSearch(String texto){
        return service.findByAll(texto);
    }


    public Ciudad saveCiudad(CiudadInput input){
        ModelMapper m = new ModelMapper();
        Ciudad e = m.map(input, Ciudad.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setPais(ciudadService.findById(input.getPaisId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteCiudad(Long id){
        return service.deleteById(id);
    }

    public Long countCiudad(){
        return service.count();
    }


}
