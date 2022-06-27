package com.franco.dev.graphql.productos;

import com.franco.dev.domain.productos.Familia;
import com.franco.dev.domain.productos.TipoPrecio;
import com.franco.dev.graphql.productos.input.FamiliaInput;
import com.franco.dev.graphql.productos.input.TipoPrecioInput;
import com.franco.dev.service.productos.FamiliaService;
import com.franco.dev.service.productos.TipoPrecioService;
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
public class TipoPrecioGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private TipoPrecioService service;

    public Optional<TipoPrecio> tipoPrecio(Long id) {return service.findById(id);}

    public List<TipoPrecio> tipoPrecioSearch(String texto) {return service.findByAll(texto);}

    public List<TipoPrecio> tipoPrecios(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public TipoPrecio saveTipoPrecio(TipoPrecioInput input){
        ModelMapper m = new ModelMapper();
        TipoPrecio e = m.map(input, TipoPrecio.class);
        return service.save(e);
    }

    public TipoPrecio updateTipoPrecio(Long id, TipoPrecioInput input){
        ModelMapper m = new ModelMapper();
        TipoPrecio p = service.getOne(id);
        p = m.map(input, TipoPrecio.class);
        return service.save(p);
    }

    public Boolean deleteTipoPrecio(Long id){
        return service.deleteById(id);
    }

    public Long countTipoPrecio(){
        return service.count();
    }
}
