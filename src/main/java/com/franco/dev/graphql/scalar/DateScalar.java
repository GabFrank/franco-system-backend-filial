package com.franco.dev.graphql.scalar;
import com.franco.dev.utilitarios.DateUtils;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

@Component
public class DateScalar extends GraphQLScalarType {
    private static final String NAME = "Date";

    public DateScalar() {
        super(NAME, "Date type", new Coercing<LocalDateTime, String>() {

            @Override
            public String serialize(Object input) {
                if (input instanceof LocalDateTime) {
                    return DateUtils.toString((LocalDateTime) input);
                }
                throw new CoercingSerializeException("Invalid Date: " + input);
            }

            @Override
            public LocalDateTime parseValue(Object input) {
                if (input instanceof String) {
                    LocalDateTime dt = DateUtils.toDate((String) input);
                    if(dt != null) {
                        return dt;
                    }
                }
                throw new CoercingParseValueException("Invalid Date: " + input);
            }

            @Override
            public LocalDateTime parseLiteral(Object input) {
                if (!(input instanceof StringValue)) return null;
                String s = ((StringValue) input).getValue();
                LocalDateTime dt = DateUtils.toDate(s);
                if(dt != null) {
                    return dt;
                }
                throw new CoercingParseLiteralException("Invalid Date: " + input);
            }
        });
    }
}

