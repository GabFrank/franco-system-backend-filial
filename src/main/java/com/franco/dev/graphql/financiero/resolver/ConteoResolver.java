package com.franco.dev.graphql.financiero.resolver;

import com.franco.dev.domain.financiero.Conteo;
import com.franco.dev.domain.financiero.ConteoMoneda;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.service.financiero.CambioService;
import com.franco.dev.service.financiero.ConteoMonedaService;
import com.franco.dev.service.financiero.ConteoService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConteoResolver implements GraphQLResolver<Conteo> {

    @Autowired
    private ConteoMonedaService conteoMonedaService;

    @Autowired
    private ConteoService conteoService;

    public List<ConteoMoneda> conteoMonedaList(Conteo e){
        return conteoMonedaService.findByConteoId(e.getId());
    }

    public Double totalGs(Conteo e){
        return conteoService.getTotalPorMoneda(e.getId(), (long) 1);
    }

    public Double totalRs(Conteo e){
        return conteoService.getTotalPorMoneda(e.getId(), (long) 2);
    }

    public Double totalDs(Conteo e){
        return conteoService.getTotalPorMoneda(e.getId(), (long) 3);
    }
}
