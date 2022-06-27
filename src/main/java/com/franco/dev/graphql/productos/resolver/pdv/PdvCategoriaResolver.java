package com.franco.dev.graphql.productos.resolver.pdv;

import com.franco.dev.domain.productos.pdv.PdvCategoria;
import com.franco.dev.domain.productos.pdv.PdvGrupo;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.pdv.PdvGrupoService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PdvCategoriaResolver implements GraphQLResolver<PdvCategoria> {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PdvGrupoService pdvGrupoService;

    public List<PdvGrupo> grupos(PdvCategoria e) { return pdvGrupoService.getRepository().findByPdvCategoriaId(e.getId()); }

}
