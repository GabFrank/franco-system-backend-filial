package com.franco.dev.graphql.productos.resolver;

import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.Subfamilia;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.FamiliaService;
import com.franco.dev.service.productos.ProductoService;
import com.franco.dev.service.productos.SubFamiliaService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class SubFamiliaResolver implements GraphQLResolver<Subfamilia> {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private FamiliaService familiaService;

    @Autowired
    private SubFamiliaService subFamiliaService;

    public Optional<Usuario> usuario(Subfamilia e){
        return usuarioService.findById(e.getUsuarioId().getId());
    }

    public List<Subfamilia> subfamiliaList(Subfamilia e){
        return subFamiliaService.findBySubFamilia(e.getId());
    }

    public List<Producto> productos(Subfamilia e) { return productoService.findBySubFamiliaId(e.getId());}


}
