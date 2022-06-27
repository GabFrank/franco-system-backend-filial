package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.Entrada;
import com.franco.dev.domain.operaciones.EntradaItem;
import com.franco.dev.domain.operaciones.Salida;
import com.franco.dev.domain.operaciones.SalidaItem;
import com.franco.dev.service.operaciones.EntradaItemService;
import com.franco.dev.service.operaciones.EntradaService;
import com.franco.dev.service.operaciones.SalidaItemService;
import com.franco.dev.service.operaciones.SalidaService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SalidaResolver implements GraphQLResolver<Salida> {

    @Autowired
    private SalidaService service;

    @Autowired
    private SalidaItemService salidaItemService;

    public List<SalidaItem> salidaItemList(Salida v){
        return salidaItemService.findBySalidaId(v.getId());
    }

}
