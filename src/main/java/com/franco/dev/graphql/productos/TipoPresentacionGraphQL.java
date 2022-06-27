package com.franco.dev.graphql.productos;

import com.franco.dev.domain.productos.TipoPrecio;
import com.franco.dev.domain.productos.TipoPresentacion;
import com.franco.dev.graphql.productos.input.TipoPrecioInput;
import com.franco.dev.graphql.productos.input.TipoPresentacionInput;
import com.franco.dev.service.productos.TipoPrecioService;
import com.franco.dev.service.productos.TipoPresentacionService;
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
public class TipoPresentacionGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private TipoPresentacionService service;

    public Optional<TipoPresentacion> tipoPresentacion(Long id) {return service.findById(id);}

    public List<TipoPresentacion> tipoPresentacionSearch(String texto) {return service.findByAll(texto);}

    public List<TipoPresentacion> tiposPresentacion(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public TipoPresentacion saveTipoPresentacion(TipoPresentacionInput input){
        ModelMapper m = new ModelMapper();
        TipoPresentacion e = m.map(input, TipoPresentacion.class);
        return service.save(e);
    }

    public TipoPresentacion updateTipoPresentacion(Long id, TipoPresentacionInput input){
        ModelMapper m = new ModelMapper();
        TipoPresentacion p = service.getOne(id);
        p = m.map(input, TipoPresentacion.class);
        return service.save(p);
    }

    public Boolean deleteTipoPresentacion(Long id){
        return service.deleteById(id);
    }

    public Long countTipoPresentacion(){
        return service.count();
    }
}
