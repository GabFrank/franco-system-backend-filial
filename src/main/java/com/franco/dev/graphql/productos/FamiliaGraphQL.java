package com.franco.dev.graphql.productos;

import com.franco.dev.domain.productos.Familia;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.productos.input.FamiliaInput;
import com.franco.dev.graphql.productos.input.ProductoInput;
import com.franco.dev.service.productos.FamiliaService;
import com.franco.dev.service.productos.ProductoService;
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
public class FamiliaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private FamiliaService service;

    public Optional<Familia> familia(Long id) {return service.findById(id);}

    public List<Familia> familiaSearch(String texto) {return service.findByAll(texto);}

    public List<Familia> familias(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public Familia saveFamilia(FamiliaInput input){
        ModelMapper m = new ModelMapper();
        Familia e = m.map(input, Familia.class);
        return service.save(e);
    }

    public Familia updateFamilia(Long id, FamiliaInput input){
        ModelMapper m = new ModelMapper();
        Familia p = service.getOne(id);
        p = m.map(input, Familia.class);
        return service.save(p);
    }

    public Boolean deleteFamilia(Long id){
        return service.deleteById(id);
    }

    public Long countFamilia(){
        return service.count();
    }
}
