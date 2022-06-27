package com.franco.dev.graphql.productos;

import com.franco.dev.domain.productos.Codigo;
import com.franco.dev.domain.productos.TipoPrecio;
import com.franco.dev.graphql.productos.input.CodigoInput;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.CodigoService;
import com.franco.dev.service.productos.ProductoService;
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
public class CodigoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private CodigoService service;

    public Optional<Codigo> codigo(Long id) {return service.findById(id);}


    public List<Codigo> codigos(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<Codigo> codigosPorPresentacionId(Long id){
        return service.findByPresentacionId(id);
    }

    public Codigo saveCodigo(CodigoInput input){
        return service.save(input);
    }


    public List<Codigo> codigoPorCodigo(String texto){ return service.findByCodigo(texto); }

    public Boolean deleteCodigo(Long id){
        return service.deleteById(id);
    }

    public Long countCodigo(){
        return service.count();
    }

}
