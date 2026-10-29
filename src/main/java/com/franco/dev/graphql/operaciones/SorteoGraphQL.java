package com.franco.dev.graphql.operaciones;

import com.franco.dev.graphql.operaciones.input.SorteoEvaluacionInput;
import com.franco.dev.service.sorteo.SorteoAplicable;
import com.franco.dev.service.sorteo.SorteoLector;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SorteoGraphQL implements GraphQLQueryResolver {

    @Autowired
    private SorteoLector sorteoLector;

    public List<SorteoAplicable> sorteosAplicables(SorteoEvaluacionInput input) {
        if (input == null || input.getTotalGs() == null) {
            return new ArrayList<>();
        }
        return sorteoLector.aplicables(input.getTotalGs(), input.getCobroDetalleList());
    }
}
