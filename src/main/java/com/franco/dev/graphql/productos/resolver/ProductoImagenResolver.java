package com.franco.dev.graphql.productos.resolver;

import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Ingrediente;
import com.franco.dev.domain.productos.ProductoImagen;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.ProductoService;
import com.franco.dev.service.utils.ImageService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@Component
public class ProductoImagenResolver implements GraphQLResolver<ProductoImagen> {

    @Autowired
    private ImageService imageService;

    public String imagen(ProductoImagen p) throws IOException {
        return imageService.getImageWithMediaType(p.getRuta());
    }


}
