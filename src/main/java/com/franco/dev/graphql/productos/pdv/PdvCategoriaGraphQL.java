package com.franco.dev.graphql.productos.pdv;

import com.franco.dev.domain.productos.Familia;
import com.franco.dev.domain.productos.pdv.PdvCategoria;
import com.franco.dev.graphql.productos.input.FamiliaInput;
import com.franco.dev.graphql.productos.input.pdv.PdvCategoriaInput;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.FamiliaService;
import com.franco.dev.service.productos.pdv.PdvCategoriaService;
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
public class PdvCategoriaGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PdvCategoriaService service;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<PdvCategoria> pdvCategoria(Long id) {return service.findById(id);}

    public List<PdvCategoria> pdvCategoriaSearch(String texto) {return service.findByAll(texto);}

    public List<PdvCategoria> pdvCategorias(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll2();
    }

    public PdvCategoria savePdvCategoria(PdvCategoriaInput input){
        ModelMapper m = new ModelMapper();
        PdvCategoria e = m.map(input, PdvCategoria.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        return service.save(e);
    }

    public Boolean deletePdvCategoria(Long id){
        return service.deleteById(id);
    }

    public Long countPdvCategoria(){
        return service.count();
    }
}
