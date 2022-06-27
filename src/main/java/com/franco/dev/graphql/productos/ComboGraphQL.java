package com.franco.dev.graphql.productos;

import com.franco.dev.domain.productos.Combo;
import com.franco.dev.domain.productos.Producto;
import com.franco.dev.graphql.productos.input.ComboInput;
import com.franco.dev.graphql.productos.input.ProductoInput;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.ComboService;
import com.franco.dev.service.productos.ProductoService;
import com.franco.dev.service.productos.SubFamiliaService;
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
public class ComboGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ComboService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProductoService productoService;

    public Optional<Combo> combo(Long id) {return service.findById(id);}

    public Combo comboPorProductoId(Long id){ return service.findByProductoId(id);}

//    public List<Combo> comboSearch(String texto) {return
//            service.findByAll(texto);
//    }

    public List<Combo> combos(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        return service.findAll(pageable);
    }

    public Combo saveCombo(ComboInput input){
        ModelMapper m = new ModelMapper();
        Combo e = m.map(input, Combo.class);
        e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        e.setProducto(productoService.findById(input.getProductoId()).orElse(null));
        return service.save(e);
    }

    public Combo updateCombo(Long id, ComboInput input){
        ModelMapper m = new ModelMapper();
        Combo p = service.getOne(id);
        p = m.map(input, Combo.class);
        return service.save(p);
    }

    public Boolean deleteCombo(Long id){
        return service.deleteById(id);
    }

    public Long countCombo(){
        return service.count();
    }
}
