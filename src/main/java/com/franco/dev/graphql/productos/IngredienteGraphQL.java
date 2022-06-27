package com.franco.dev.graphql.productos;

import com.franco.dev.domain.productos.Ingrediente;
import com.franco.dev.graphql.productos.input.IngredienteInput;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.IngredienteService;
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
public class IngredienteGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private IngredienteService service;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<Ingrediente> ingrediente(Long id) {return service.findById(id);}

    public List<Ingrediente> ingredientesSearch(String texto) {return service.findByAll(texto);}

    public List<Ingrediente> ingredientes(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public Ingrediente saveIngrediente(IngredienteInput input){
        ModelMapper m = new ModelMapper();
        Ingrediente e = m.map(input, Ingrediente.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setProducto(productoService.findById(input.getProductoId()).orElse(null));
        return service.save(e);
    }

    public Ingrediente updateIngrediente(Long id, IngredienteInput input){
        ModelMapper m = new ModelMapper();
        Ingrediente p = service.getOne(id);
        p = m.map(input, Ingrediente.class);
        return service.save(p);
    }

    public Boolean deleteIngrediente(Long id){
        return service.deleteById(id);
    }

    public Long countIngrediente(){
        return service.count();
    }
}
