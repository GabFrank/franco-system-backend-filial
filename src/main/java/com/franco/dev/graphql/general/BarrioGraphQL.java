package com.franco.dev.graphql.general;

import com.franco.dev.domain.general.Barrio;
import com.franco.dev.domain.general.Ciudad;
import com.franco.dev.graphql.general.input.BarrioInput;
import com.franco.dev.graphql.general.input.CiudadInput;
import com.franco.dev.service.general.BarrioService;
import com.franco.dev.service.general.CiudadService;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.operaciones.PrecioDeliveryService;
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
public class BarrioGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private BarrioService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private CiudadService ciudadService;

    @Autowired
    private PrecioDeliveryService precioDeliveryService;

    public Optional<Barrio> barrio(Long id) {return service.findById(id);}

    public List<Barrio> barrios(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<Barrio> barriosSearch(String texto){
        return service.findByAll(texto);
    }

    public List<Barrio> barriosPorCiudadId(Long id) { return service.findByCiudadId(id); }


    public Barrio saveBarrio(BarrioInput input){
        ModelMapper m = new ModelMapper();
        Barrio e = m.map(input, Barrio.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setCiudad(ciudadService.findById(input.getCiudadId()).orElse(null));
        e.setPrecioDelivery(precioDeliveryService.findById(input.getPrecioDeliveryId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteBarrio(Long id){
        return service.deleteById(id);
    }

    public Long countBarrio(){
        return service.count();
    }


}
