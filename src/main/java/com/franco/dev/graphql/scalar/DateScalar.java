package com.franco.dev.graphql.scalar;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class DateScalar extends GraphQLScalarType {

    public DateScalar() {
        super("Date", "LocalDateTime scalar type", new Coercing<LocalDateTime, String>() {
            @Override
            public String serialize(Object input) throws CoercingSerializeException {
                if (input instanceof LocalDateTime) {
                    LocalDateTime localDateTime = (LocalDateTime) input;
                    return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(localDateTime);
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
        });
    }
}


