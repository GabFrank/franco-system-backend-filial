package com.franco.dev.graphql.empresarial;

import com.franco.dev.domain.empresarial.ConfiguracionGeneral;
import com.franco.dev.graphql.empresarial.input.ConfiguracionGeneralInput;
import com.franco.dev.service.empresarial.ConfiguracionGeneralService;
import com.franco.dev.service.personas.UsuarioService;

import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ConfiguracionGeneralGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private ConfiguracionGeneralService service;

    @Autowired
    private UsuarioService usuarioService;

    public Optional<ConfiguracionGeneral> configuracionGeneral() {
        return service.findById((long) 1);
    }

    public Boolean solicitarResources(){
//        propagacionService.solicitarResources();
        return true;
    }

}
