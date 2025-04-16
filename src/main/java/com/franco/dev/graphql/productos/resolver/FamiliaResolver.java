package com.franco.dev.graphql.productos.resolver;

import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.domain.productos.Familia;
import com.franco.dev.domain.productos.Subfamilia;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.SubFamiliaService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class FamiliaResolver implements GraphQLResolver<Familia> {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private SubFamiliaService subFamiliaService;

    public Optional<Usuario> usuario(Familia e){
        return usuarioService.findById(e.getUsuarioId().getId());
    }

    public List<Subfamilia> subfamilias(Familia e) { return subFamiliaService.findByFamiliaId(e.getId() ); }


}
