package com.franco.dev.graphql.productos.pdv;

import com.franco.dev.domain.productos.pdv.PdvCategoria;
import com.franco.dev.domain.productos.pdv.PdvGruposProductos;
import com.franco.dev.graphql.productos.input.pdv.PdvCategoriaInput;
import com.franco.dev.graphql.productos.input.pdv.PdvGruposProductosInput;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.ProductoService;
import com.franco.dev.service.productos.pdv.PdvCategoriaService;
import com.franco.dev.service.productos.pdv.PdvGrupoService;
import com.franco.dev.service.productos.pdv.PdvGruposProductosService;
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
public class PdvGruposProductosGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private PdvGruposProductosService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PdvGrupoService pdvGrupoService;

    @Autowired
    private ProductoService productoService;

    public Optional<PdvGruposProductos> pdvGrupoProductos(Long id) {return service.findById(id);}

    public List<PdvGruposProductos> pdvGruposProductosSearch(String texto) {return service.findByAll(texto);}

    public List<PdvGruposProductos> pdvGruposProductos(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public List<PdvGruposProductos> pdvGruposProductosPorGrupoId(Long id){
        return service.findByGrupoId(id);
    }

    public PdvGruposProductos savePdvGruposProductos(PdvGruposProductosInput input){
        ModelMapper m = new ModelMapper();
        PdvGruposProductos e = m.map(input, PdvGruposProductos.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setPdvGrupo(pdvGrupoService.findById(input.getGrupoId()).orElse(null));
        e.setProducto(productoService.findById(input.getProductoId()).orElse(null));
        return service.save(e);
    }

    public Boolean deletePdvGruposProductos(Long id){
        return service.deleteById(id);
    }

    public Long countPdvGruposProductos(){
        return service.count();
    }
}
