package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.Entrada;
import com.franco.dev.domain.operaciones.EntradaItem;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.productos.enums.UnidadMedida;
import com.franco.dev.service.operaciones.EntradaItemService;
import com.franco.dev.service.operaciones.EntradaService;
import com.franco.dev.service.operaciones.VentaItemService;
import com.franco.dev.service.operaciones.VentaService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EntradaResolver implements GraphQLResolver<Entrada> {

    @Autowired
    private EntradaService service;

    @Autowired
    private EntradaItemService entradaItemService;

    public List<EntradaItem> entradaItemList(Entrada v){
        return entradaItemService.findByEntradaId(v.getId());
    }

}
