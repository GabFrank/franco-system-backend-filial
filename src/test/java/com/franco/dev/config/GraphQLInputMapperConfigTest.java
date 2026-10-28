package com.franco.dev.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.FrancoSystemsApplication;
import com.franco.dev.graphql.operaciones.input.CobroInput;
import graphql.kickstart.tools.PerFieldObjectMapperProvider;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * El POS manda CobroInput con sucursalId, que la clase Java no tiene. Con graphql-java-tools 11
 * eso tiraba UnrecognizedPropertyException y la venta no se guardaba.
 */
class GraphQLInputMapperConfigTest {

    @Test
    void ignoraCamposDelSchemaQueLaClaseNoTiene() {
        ObjectMapper appMapper = new FrancoSystemsApplication().objectMapper();
        PerFieldObjectMapperProvider provider = new GraphQLInputMapperConfig().perFieldObjectMapperProvider(appMapper);

        Map<String, Object> argumento = new HashMap<>();
        argumento.put("id", "15");
        argumento.put("creadoEn", LocalDateTime.of(2026, 10, 28, 13, 34, 59));
        argumento.put("usuarioId", 3);
        argumento.put("totalGs", 7000.0);
        argumento.put("sucursalId", 24);

        CobroInput input = provider.provide(null).convertValue(argumento, CobroInput.class);

        assertEquals(15L, input.getId());
        assertEquals(LocalDateTime.of(2026, 10, 28, 13, 34, 59), input.getCreadoEn());
        assertEquals(3L, input.getUsuarioId());
        assertEquals(7000.0, input.getTotalGs());
    }

    @Test
    void noCambiaElMapperDeLaApp() {
        ObjectMapper appMapper = new FrancoSystemsApplication().objectMapper();
        new GraphQLInputMapperConfig().perFieldObjectMapperProvider(appMapper);

        assertEquals(true, appMapper.isEnabled(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }
}
