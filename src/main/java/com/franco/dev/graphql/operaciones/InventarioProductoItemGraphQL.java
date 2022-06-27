package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.InventarioProducto;
import com.franco.dev.domain.operaciones.InventarioProductoItem;
import com.franco.dev.graphql.operaciones.input.InventarioProductoItemInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.operaciones.InventarioProductoItemService;
import com.franco.dev.service.operaciones.InventarioProductoService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.productos.PresentacionService;
import com.franco.dev.service.rabbitmq.PropagacionService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.franco.dev.utilitarios.DateUtils.toDate;

@Component
public class InventarioProductoItemGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private InventarioProductoItemService service;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PresentacionService presentacionService;

    @Autowired
    private InventarioProductoService inventarioProductoService;

    @Autowired
    private PropagacionService propagacionService;


    public Optional<InventarioProductoItem> inventarioProductoItem(Long id) {
        return service.findById(id);
    }

    public List<InventarioProductoItem> inventarioProductosItem(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.findAll(pageable);
    }

    public InventarioProductoItem saveInventarioProductoItem(InventarioProductoItemInput input) {
        ModelMapper m = new ModelMapper();
        m.getConfiguration()
                .setMatchingStrategy(MatchingStrategies.STRICT);
        InventarioProductoItem e = m.map(input, InventarioProductoItem.class);
        if(input.getVencimiento() != null) e.setVencimiento(toDate(input.getVencimiento()));
        if (input.getUsuarioId() != null) e.setUsuario(usuarioService.findById(input.getUsuarioId()).orElse(null));
        if (input.getPresentacionId() != null)
            e.setPresentacion(presentacionService.findById(input.getPresentacionId()).orElse(null));
        if (input.getInventarioProductoId() != null)
            e.setInventarioProducto(inventarioProductoService.findById(input.getInventarioProductoId()).orElse(null));
        e = service.save(e);
        return e;
    }

    public Boolean deleteInventarioProductoItem(Long id) {
        Boolean ok = false;
        InventarioProductoItem i = service.findById(id).orElse(null);
        if(i!=null) {
            ok = service.deleteById(id);
            propagacionService.deleteEntidad(i, TipoEntidad.INVENTARIO_PRODUCTO_ITEM);
        }
        return ok;
    }

    public Long countInventarioProductoItem() {
        return service.count();
    }


}
