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
        return cambioService.findLastByMonedaId(e.getId()).getValorEnGs();
    }

    public List<MonedaBilletes> monedaBilleteList(Moneda e){
        List<MonedaBilletes> monedaBilletesList = monedaBilleteService.findByMonedaId(e.getId());
        if(!monedaBilletesList.isEmpty()){
            monedaBilletesList.sort((a, b)-> {
                if(a.getValor() > b.getValor()){
                    return 1;
                } else {
                    return -1;
                }
            });
        }
        return monedaBilletesList;
    }

}
