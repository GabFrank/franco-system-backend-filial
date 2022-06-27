package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.PrecioDelivery;
import com.franco.dev.graphql.operaciones.input.PrecioDeliveryInput;
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
public class PrecioDeliveryGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PrecioDeliveryService service;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<PrecioDelivery> deliveryPrecio(Long id) {return service.findById(id);}

    public List<PrecioDelivery> deliveryPrecios(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

//    public List<PrecioDelivery> deliveryPrecioSearch(String texto){
//        return service.findByAll(texto);
//    }

    public PrecioDelivery savePrecioDelivery(PrecioDeliveryInput input){
        ModelMapper m = new ModelMapper();
        PrecioDelivery e = m.map(input, PrecioDelivery.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        return service.save(e);
    }

    public Boolean deletePrecioDelivery(Long id){
        return service.deleteById(id);
    }

    public Long countPrecioDelivery(){
        return service.count();
    }


}
