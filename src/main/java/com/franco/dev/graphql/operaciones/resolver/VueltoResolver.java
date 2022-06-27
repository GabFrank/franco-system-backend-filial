package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.operaciones.Vuelto;
import com.franco.dev.domain.operaciones.VueltoItem;
import com.franco.dev.domain.productos.enums.UnidadMedida;
import com.franco.dev.service.operaciones.VentaItemService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.operaciones.VueltoItemService;
import com.franco.dev.service.operaciones.VueltoService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VueltoResolver implements GraphQLResolver<Vuelto> {

    @Autowired
    private VueltoService service;

    @Autowired
    private VueltoItemService ventaItemService;

    public List<VueltoItem> vueltoItemList(Vuelto v){
        return ventaItemService.findByVueltoId(v.getId());
    }
}
