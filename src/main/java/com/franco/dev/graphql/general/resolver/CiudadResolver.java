package com.franco.dev.graphql.general.resolver;

import com.franco.dev.domain.general.Ciudad;
import com.franco.dev.domain.general.Pais;
import com.franco.dev.domain.personas.Cliente;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.general.PaisService;
import com.franco.dev.service.personas.PersonaService;
import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CiudadResolver implements GraphQLResolver<Ciudad> {

    @Autowired
    private PaisService paisService;

    public Optional<Pais> pais(Ciudad e){
        return paisService.findById(e.getPais().getId());
    }

}
