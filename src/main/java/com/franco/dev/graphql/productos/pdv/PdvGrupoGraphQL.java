package com.franco.dev.graphql.productos.pdv;

import com.franco.dev.domain.productos.pdv.PdvCategoria;
import com.franco.dev.domain.productos.pdv.PdvGrupo;
import com.franco.dev.graphql.productos.input.pdv.PdvCategoriaInput;
import com.franco.dev.graphql.productos.input.pdv.PdvGrupoInput;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.pdv.PdvCategoriaService;
import com.franco.dev.service.productos.pdv.PdvGrupoService;
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
public class PdvGrupoGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PdvGrupoService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PdvCategoriaService pdvCategoriaService;

    public Optional<PdvGrupo> pdvGrupo(Long id) {return service.findById(id);}

    public List<PdvGrupo> pdvGrupoSearch(String texto) {return service.findByAll(texto);}

    public List<PdvGrupo> pdvGrupos(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public PdvGrupo savePdvGrupo(PdvGrupoInput input){
        ModelMapper m = new ModelMapper();
        PdvGrupo e = m.map(input, PdvGrupo.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setPdvCategoria(pdvCategoriaService.findById(input.getCategoriaId()).orElse(null));
        return service.save(e);
    }

    public Boolean deletePdvGrupo(Long id){
        return service.deleteById(id);
    }

    public Long countPdvGrupo(){
        return service.count();
    }
}
