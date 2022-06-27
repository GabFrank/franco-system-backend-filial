package com.franco.dev.graphql.productos;

import com.franco.dev.domain.productos.ComboItem;
import com.franco.dev.domain.productos.Ingrediente;
import com.franco.dev.domain.productos.ProductoIngrediente;
import com.franco.dev.graphql.productos.input.ComboItemInput;
import com.franco.dev.graphql.productos.input.ProductoIngredienteInput;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.*;
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
public class ProductoIngredienteGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ProductoIngredienteService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private IngredienteService ingredienteService;

    public Optional<ProductoIngrediente> productoIngrediente(Long id) {return service.findById(id);}

    public List<ProductoIngrediente> productoIngredientePorProductoId(Long id){ return service.findByProducto(id);}
    public List<ProductoIngrediente> productoIngredientePorIngredienteId(Long id){ return service.findByIngrediente(id);}

    public List<ProductoIngrediente> productoIngredienteSearch(String texto) {return
            service.findByAll(texto);
    }

    public List<ProductoIngrediente> productoIngredientes(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public ProductoIngrediente saveProductoIngrediente(ProductoIngredienteInput input){
        ModelMapper m = new ModelMapper();
        ProductoIngrediente e = m.map(input, ProductoIngrediente.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setProducto(productoService.findById(input.getProductoId()).orElse(null));
        e.setIngrediente(ingredienteService.findById(input.getIngredienteId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteProductoIngrediente(Long id){
        return service.deleteById(id);
    }

    public Long countProductoIngrediente(){
        return service.count();
    }
}
