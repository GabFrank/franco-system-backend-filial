package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.ConfiguracionVentaTarjeta;
import com.franco.dev.service.financiero.ConfiguracionVentaTarjetaService;
import graphql.kickstart.tools.GraphQLQueryResolver;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * ConfiguracionVentaTarjeta en filial es de solo lectura: la fila llega
 * unicamente por replicacion logica desde el central (MAIN_TO_ALL).
 * La administracion (saveConfiguracionVentaTarjeta) vive solo en el central.
 */
@Component
@AllArgsConstructor
public class ConfiguracionVentaTarjetaGraphQL implements GraphQLQueryResolver {

    private final ConfiguracionVentaTarjetaService service;

    public ConfiguracionVentaTarjeta configuracionVentaTarjeta() {
        return service.findOrDefault();
    }
}
