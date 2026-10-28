package com.franco.dev.graphql.scalar;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Scalar {@code Date} del schema. graphql-java 18 ya no deja extender GraphQLScalarType,
 * por eso se arma con el builder (igual que en el central). El formato no cambia: ISO local.
 */
@Configuration
public class DateScalarConfiguration {

    @Bean
    public GraphQLScalarType dateScalar() {
        return GraphQLScalarType.newScalar()
                .name("Date")
                .description("LocalDateTime scalar type")
                .coercing(new Coercing<LocalDateTime, String>() {
                    @Override
                    public String serialize(Object input) throws CoercingSerializeException {
                        if (input instanceof LocalDateTime) {
                            return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format((LocalDateTime) input);
                        }
                        // El maestro de lotes guarda vencimiento y retiro como LocalDate: son fechas de
                        // calendario, no instantes. Salen por el mismo scalar Date, asi que se completan
                        // al inicio del dia en vez de reventar la query entera.
                        if (input instanceof LocalDate) {
                            return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(((LocalDate) input).atStartOfDay());
                        }
                        throw new CoercingSerializeException("Unable to serialize " + input + " as LocalDateTime.");
                    }

                    @Override
                    public LocalDateTime parseValue(Object input) throws CoercingParseValueException {
                        try {
                            return LocalDateTime.parse((String) input, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                        } catch (Exception e) {
                            throw new CoercingParseValueException("Unable to parse value " + input + " as LocalDateTime.");
                        }
                    }

                    @Override
                    public LocalDateTime parseLiteral(Object input) throws CoercingParseLiteralException {
                        if (input instanceof StringValue) {
                            try {
                                return LocalDateTime.parse(((StringValue) input).getValue(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                            } catch (Exception e) {
                                throw new CoercingParseLiteralException("Unable to parse literal " + input + " as LocalDateTime.");
                            }
                        }
                        throw new CoercingParseLiteralException("Unable to parse literal " + input + " as LocalDateTime.");
                    }
                })
                .build();
    }
}
