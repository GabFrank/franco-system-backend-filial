package com.franco.dev.graphql.financiero.resolver;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.TipoGasto;
import com.franco.dev.service.financiero.CambioService;
import com.franco.dev.service.financiero.TipoGastoService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TipoGastoResolver implements GraphQLResolver<TipoGasto> {

    @Autowired
    private TipoGastoService tipoGastoService;

    public List<TipoGasto> subtipoList(TipoGasto e){
        return tipoGastoService.findByClasificacionGastoId(e.getId());
    }

}
