package com.franco.dev.graphql.productos.resolver;

import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Ingrediente;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.ProductoService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class IngredienteResolver implements GraphQLResolver<Ingrediente> {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProductoService productoService;

    public Optional<Usuario> usuario(Ingrediente e){
        return usuarioService.findById(e.getUsuario().getId());
    }

//    public Optional<Producto> producto(Ingrediente e){
//        return productoService.findById(e.getProducto().getId());
//    }

}
