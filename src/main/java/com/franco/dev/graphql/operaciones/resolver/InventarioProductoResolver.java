package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.Inventario;
import com.franco.dev.domain.operaciones.InventarioProducto;
import com.franco.dev.domain.operaciones.InventarioProductoItem;
import com.franco.dev.service.operaciones.InventarioProductoItemService;
import com.franco.dev.service.operaciones.InventarioProductoService;
import com.franco.dev.service.operaciones.InventarioService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InventarioProductoResolver implements GraphQLResolver<InventarioProducto> {

    @Autowired
    private InventarioProductoService service;

    @Autowired
    private InventarioProductoItemService inventarioProductoItemService;

    public List<InventarioProductoItem> inventarioProductoItemList(InventarioProducto v){
        return inventarioProductoItemService.findByInventarioProductoId(v.getId());
    }

}
