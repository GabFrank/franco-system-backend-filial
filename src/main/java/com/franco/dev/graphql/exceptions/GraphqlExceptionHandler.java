package com.franco.dev.graphql.exceptions;

import graphql.ExceptionWhileDataFetching;
import graphql.GraphQLError;
import graphql.GraphQLException;
import graphql.GraphqlErrorBuilder;
import graphql.kickstart.execution.error.GenericGraphQLError;
import graphql.kickstart.execution.error.GraphQLErrorHandler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Los errores que el código arma a propósito ({@link GraphQLException}) llegan al cliente con su texto, sin el
 * prefijo de graphql-java. Cualquier otra excepción (NPE, SQL, ...) sigue saliendo como el genérico de siempre, para
 * no mostrar detalles técnicos; el detalle queda en el log.
 */
@Component
public class GraphqlExceptionHandler implements GraphQLErrorHandler {

    static final String GENERICO = "Internal Server Error(s) while executing query";
    private static final int PROFUNDIDAD = 10;

    @Override
    public List<GraphQLError> processErrors(List<GraphQLError> errores) {
        List<GraphQLError> salida = new ArrayList<>();
        boolean hayInternos = false;
        for (GraphQLError error : errores) {
            GraphQLError visible = visible(error);
            if (visible != null) {
                salida.add(visible);
            } else {
                hayInternos = true;
            }
        }
        // Como el handler por defecto: un solo genérico al final, sin path.
        if (hayInternos) {
            salida.add(new GenericGraphQLError(GENERICO));
        }
        return salida;
    }

    /** El error a mostrar, o null si es interno. */
    private static GraphQLError visible(GraphQLError error) {
        if (!(error instanceof ExceptionWhileDataFetching)) {
            return error; // validación, sintaxis: como siempre
        }
        ExceptionWhileDataFetching enResolver = (ExceptionWhileDataFetching) error;
        Throwable causa = enResolver.getException();
        if (causa instanceof GraphQLError) {
            return (GraphQLError) causa; // ya era visible con el handler por defecto
        }
        for (int i = 0; causa != null && i < PROFUNDIDAD; i++, causa = causa.getCause()) {
            if (causa instanceof GraphQLException && causa.getMessage() != null) {
                return GraphqlErrorBuilder.newError()
                        .message(causa.getMessage())
                        .path(enResolver.getPath())
                        .locations(enResolver.getLocations())
                        .build();
            }
        }
        return null;
    }
}
