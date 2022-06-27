package com.franco.dev.graphql.empresarial.resolver;

import com.franco.dev.domain.empresarial.Cargo;
import com.franco.dev.domain.general.Ciudad;
import com.franco.dev.domain.general.Pais;
import com.franco.dev.service.general.PaisService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CargoResolver implements GraphQLResolver<Cargo> {

    @Autowired
    private PaisService paisService;

//    public Optional<Pais> pais(Cargo e){
//        return paisService.findById(e.getPais().getId());
//    }

}
