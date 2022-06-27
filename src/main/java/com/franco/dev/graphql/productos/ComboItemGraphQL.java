package com.franco.dev.graphql.productos;

import com.franco.dev.domain.productos.Combo;
import com.franco.dev.domain.productos.ComboItem;
import com.franco.dev.graphql.productos.input.ComboInput;
import com.franco.dev.graphql.productos.input.ComboItemInput;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.ComboItemService;
import com.franco.dev.service.productos.ComboService;
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
public class ComboItemGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ComboItemService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private ComboService comboService;

    public Optional<ComboItem> comboItem(Long id) {return service.findById(id);}

    public List<ComboItem> comboItemPorProductoId(Long id){ return service.findByProducto(id);}
    public List<ComboItem> comboItemPorComboId(Long id){ return service.findByCombo(id);}

//    public List<ComboItem> comboItemSearch(String texto) {return
//            service.findByAll(texto);
//    }

    public List<ComboItem> comboItems(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public ComboItem saveComboItem(ComboItemInput input){
        ModelMapper m = new ModelMapper();
        ComboItem e = m.map(input, ComboItem.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setProducto(productoService.findById(input.getProductoId()).orElse(null));
        e.setCombo(comboService.findById(input.getComboId()).orElse(null));
        return service.save(e);
    }

    public ComboItem updateComboItem(Long id, ComboItemInput input){
        ModelMapper m = new ModelMapper();
        ComboItem p = service.getOne(id);
        p = m.map(input, ComboItem.class);
        return service.save(p);
    }

    public Boolean deleteComboItem(Long id){
        return service.deleteById(id);
    }

    public Long countComboItem(){
        return service.count();
    }
}
