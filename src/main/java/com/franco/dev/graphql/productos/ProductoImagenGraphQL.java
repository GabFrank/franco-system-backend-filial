package com.franco.dev.graphql.productos;

import com.franco.dev.domain.productos.Familia;
import com.franco.dev.domain.productos.ProductoImagen;
import com.franco.dev.graphql.productos.input.FamiliaInput;
import com.franco.dev.graphql.productos.input.ProductoImagenInput;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.FamiliaService;
import com.franco.dev.service.productos.ProductoImagenService;
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
public class ProductoImagenGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ProductoImagenService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProductoService productoService;

    public Optional<ProductoImagen> productoImagen(Long id) {return service.findById(id);}

    public List<ProductoImagen> productoImagenes(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public ProductoImagen saveProductoImagen(ProductoImagenInput input){
        ModelMapper m = new ModelMapper();
        ProductoImagen e = m.map(input, ProductoImagen.class);
        return service.save(e);
    }

    public List<ProductoImagen> productoImagenesPorProducto(Long id){
        return service.findByProductoId(id);
    }

    public ProductoImagen productoImagenPrincipalPorProducto(Long id){
        return service.findImagenPrincipalByProductoId(id);
    }

    public ProductoImagen updateProductoImagen(Long id, ProductoImagenInput input){
        ModelMapper m = new ModelMapper();
        ProductoImagen p = service.getOne(id);
        p.setProducto(productoService.findById(input.getProductoId()).orElse(null));
        p.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        p = m.map(input, ProductoImagen.class);
        return service.save(p);
    }

    public Boolean deleteProductoImagen(Long id){
        return service.deleteById(id);
    }

    public Long countProductoImagen(){
        return service.count();
    }
}
