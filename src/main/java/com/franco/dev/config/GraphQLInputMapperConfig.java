package com.franco.dev.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.kickstart.tools.PerFieldObjectMapperProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mapper con el que graphql-java-tools convierte los argumentos de una mutation a las clases
 * {@code *Input}.
 *
 * Con kickstart 11 el starter usa por defecto el ObjectMapper de Spring, y el de la filial
 * ({@code FrancoSystemsApplication#objectMapper}) falla ante un campo desconocido. Hay inputs
 * cuyo .graphqls tiene campos que la clase Java no (ej. {@code CobroInput.sucursalId}): con
 * graphql-java-tools 6 se ignoraban y con 11 la venta no se podia guardar. Se usa una copia del
 * mapper de la app que los ignora, sin cambiar el comportamiento del resto (REST, etc.).
 */
@Configuration
public class GraphQLInputMapperConfig {

    @Bean
    public PerFieldObjectMapperProvider perFieldObjectMapperProvider(ObjectMapper objectMapper) {
        ObjectMapper inputMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return field -> inputMapper;
    }
}
