package com.franco.dev.graphql.operaciones.resolver;

import com.franco.dev.domain.operaciones.Transferencia;
import com.franco.dev.domain.operaciones.TransferenciaItem;
import com.franco.dev.service.operaciones.TransferenciaItemService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TransferenciaResolver implements GraphQLResolver<Transferencia> {

    @Autowired
    private TransferenciaItemService transferenciaItemService;

    public List<TransferenciaItem> transferenciaItemList(Transferencia e){
        return transferenciaItemService.findByTransferenciaItemId(e.getId());
    }

}
