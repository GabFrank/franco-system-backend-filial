package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.Entrada;
import com.franco.dev.domain.operaciones.EntradaItem;
import com.franco.dev.domain.operaciones.Inventario;
import com.franco.dev.domain.operaciones.InventarioProducto;
import com.franco.dev.service.operaciones.EntradaItemService;
import com.franco.dev.service.operaciones.EntradaService;
import com.franco.dev.service.operaciones.InventarioProductoService;
import com.franco.dev.service.operaciones.InventarioService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InventarioResolver implements GraphQLResolver<Inventario> {

    @Autowired
    private InventarioService service;

    @Autowired
    private InventarioProductoService inventarioProductoService;

    public List<InventarioProducto> inventarioProductoList(Inventario v){
        return inventarioProductoService.findByInventarioId(v.getId());
    }

}
