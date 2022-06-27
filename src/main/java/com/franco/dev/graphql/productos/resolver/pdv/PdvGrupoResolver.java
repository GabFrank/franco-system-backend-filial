package com.franco.dev.graphql.productos.resolver.pdv;

import com.franco.dev.domain.productos.Producto;
import com.franco.dev.domain.productos.pdv.PdvCategoria;
import com.franco.dev.domain.productos.pdv.PdvGrupo;
import com.franco.dev.domain.productos.pdv.PdvGruposProductos;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.ProductoService;
import com.franco.dev.service.productos.pdv.PdvGrupoService;
import com.franco.dev.service.productos.pdv.PdvGruposProductosService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PdvGrupoResolver implements GraphQLResolver<PdvGrupo> {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PdvGruposProductosService pdvGruposProductosService;


    public List<PdvGruposProductos> pdvGruposProductos(PdvGrupo e) {
        return pdvGruposProductosService.findByGrupoId(e.getId());
    }

}
