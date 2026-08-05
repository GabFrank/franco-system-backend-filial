package com.franco.dev.graphql.financiero.resolver;

import com.franco.dev.domain.financiero.Cambio;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MonedaBilletes;
import com.franco.dev.domain.general.Ciudad;
import com.franco.dev.domain.general.Pais;
import com.franco.dev.service.financiero.CambioService;
import com.franco.dev.service.financiero.MonedaBilleteService;
import com.franco.dev.service.general.PaisService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class MonedaResolver implements GraphQLResolver<Moneda> {

    @Autowired
    private CambioService cambioService;
    @Autowired
    private MonedaBilleteService monedaBilleteService;


    public Double cambio(Moneda e){
        // Null-safe: si esta moneda no tiene ninguna cotización registrada,
        // devolver null en lugar de lanzar NullPointerException. Antes, una
        // sola moneda sin cotización tumbaba toda la query `monedas`, y el POS
        // caía a su cotización default (1) para TODAS las monedas. Devolviendo
        // null, las demás monedas conservan su cotización real.
        return cambioService.findLastValorEnGsByMonedaId(e.getId());
    }

    public List<MonedaBilletes> monedaBilleteList(Moneda e){
        List<MonedaBilletes> monedaBilletesList = monedaBilleteService.findByMonedaId(e.getId());
        if(!monedaBilletesList.isEmpty()){
            monedaBilletesList.sort((a, b)-> {
                if(a.getValor().compareTo(b.getValor()) < 0){
                    return 1;
                } else {
                    return -1;
                }
            });
        }
        return monedaBilletesList;
    }

}
