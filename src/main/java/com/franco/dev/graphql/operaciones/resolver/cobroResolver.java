package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.domain.operaciones.Vuelto;
import com.franco.dev.domain.operaciones.VueltoItem;
import com.franco.dev.service.operaciones.CobroDetalleService;
import com.franco.dev.service.operaciones.VueltoItemService;
import com.franco.dev.service.operaciones.VueltoService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class cobroResolver implements GraphQLResolver<Cobro> {

    @Autowired
    private CobroDetalleService cobroDetalleService;

    public List<CobroDetalle> cobroDetalleList(Cobro c){
        return cobroDetalleService.findByCobroId(c.getId());
    }
}
