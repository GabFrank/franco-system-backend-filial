package com.franco.dev.graphql.empresarial;

import com.franco.dev.domain.empresarial.ConfiguracionGeneral;
import com.franco.dev.graphql.empresarial.input.ConfiguracionGeneralInput;
import com.franco.dev.rabbit.enums.TipoEntidad;
import com.franco.dev.service.empresarial.ConfiguracionGeneralService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.rabbitmq.PropagacionService;
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

    @Autowired
    private PropagacionService propagacionService;

    public Optional<ConfiguracionGeneral> configuracionGeneral() {
        return service.findById((long) 1);
    }

}
