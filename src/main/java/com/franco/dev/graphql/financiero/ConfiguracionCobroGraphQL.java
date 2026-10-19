package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.ConfiguracionCobro;
import com.franco.dev.service.financiero.ConfiguracionCobroService;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Solo lectura: la fila llega por replicacion desde el central, donde vive saveConfiguracionCobro.
 * Sin rol a proposito: la lee el cobro de cualquier cajero.
 */
@Component
@AllArgsConstructor
public class ConfiguracionCobroGraphQL implements GraphQLQueryResolver {

    private final ConfiguracionCobroService service;

    public ConfiguracionCobro configuracionCobro() {
        return service.findOrDefault();
    }
}
