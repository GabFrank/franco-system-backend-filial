package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.NecesidadItem;
import com.franco.dev.graphql.operaciones.input.NecesidadItemInput;
import com.franco.dev.service.operaciones.NecesidadItemService;
import com.franco.dev.service.operaciones.NecesidadService;
import com.franco.dev.service.personas.UsuarioService;
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
public class NecesidadItemGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private NecesidadItemService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private NecesidadService necesidadService;

    public Optional<NecesidadItem> necesidadItem(Long id) {return service.findById(id);}

    public List<NecesidadItem> necesidadItens(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }


    public NecesidadItem saveNecesidadItem(NecesidadItemInput input){
        ModelMapper m = new ModelMapper();
        NecesidadItem e = m.map(input, NecesidadItem.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setProducto(productoService.findById(input.getProductoId()).orElse(null));
        e.setNecesidad(necesidadService.findById(input.getNecesidadId()).orElse(null));
        return service.save(e);
    }

    public Boolean deleteNecesidadItem(Long id){
        return service.deleteById(id);
    }

    public Long countNecesidadItem(){
        return service.count();
    }


}
