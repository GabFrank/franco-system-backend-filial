package com.franco.dev.graphql.exceptions;

import com.franco.dev.security.UnauthenticatedAccessException;
import graphql.ExceptionWhileDataFetching;
import graphql.GraphQLError;
import graphql.GraphQLException;
import graphql.execution.ResultPath;
import graphql.language.SourceLocation;
import graphql.validation.ValidationError;
import graphql.validation.ValidationErrorType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Los avisos propios llegan con su texto; el resto, como el genérico de siempre. */
class GraphqlExceptionHandlerTest {

    private final GraphqlExceptionHandler handler = new GraphqlExceptionHandler();
    private static final SourceLocation LUGAR = new SourceLocation(1, 3);

    private static GraphQLError enResolver(Throwable e) {
        return new ExceptionWhileDataFetching(ResultPath.parse("/r"), e, LUGAR);
    }

    @Test
    void unaGraphQLExceptionLlegaConSuTextoLimpioYSuPath() {
        List<GraphQLError> salida = handler.processErrors(Collections.singletonList(
                enResolver(new GraphQLException("No se pudo imprimir el balance"))));

        assertEquals(1, salida.size());
        assertEquals("No se pudo imprimir el balance", salida.get(0).getMessage());
        assertEquals(Collections.singletonList("r"), salida.get(0).getPath());
        assertEquals(Collections.singletonList(LUGAR), salida.get(0).getLocations());
    }

    @Test
    void unaGraphQLExceptionEnvueltaTambien() {
        Throwable envuelta = new RuntimeException(new IllegalStateException(new GraphQLException("No autorizado: x")));
        List<GraphQLError> salida = handler.processErrors(Collections.singletonList(enResolver(envuelta)));
        assertEquals("No autorizado: x", salida.get(0).getMessage());
    }

    @Test
    void unErrorTecnicoSigueGenerico() {
        List<GraphQLError> salida = handler.processErrors(Collections.singletonList(
                enResolver(new NullPointerException("campo x de la entidad y"))));
        assertEquals(1, salida.size());
        assertEquals(GraphqlExceptionHandler.GENERICO, salida.get(0).getMessage());
    }

    @Test
    void unErrorQueYaEsGraphQLErrorPasaTalCual() {
        UnauthenticatedAccessException sinSesion = new UnauthenticatedAccessException("sin sesion");
        List<GraphQLError> salida = handler.processErrors(Collections.singletonList(enResolver(sinSesion)));
        assertSame(sinSesion, salida.get(0));
    }

    @Test
    void validacionPasaTalCualYLosTecnicosSeJuntanEnUnGenericoAlFinal() {
        GraphQLError validacion = ValidationError.newValidationError()
                .validationErrorType(ValidationErrorType.FieldUndefined).description("campo inexistente").build();
        List<GraphQLError> salida = handler.processErrors(Arrays.asList(
                enResolver(new NullPointerException()), validacion, enResolver(new IllegalStateException("sql")),
                enResolver(new GraphQLException("Venta no encontrada"))));

        assertEquals(3, salida.size());
        assertSame(validacion, salida.get(0));
        assertEquals("Venta no encontrada", salida.get(1).getMessage());
        assertEquals(GraphqlExceptionHandler.GENERICO, salida.get(2).getMessage());
    }
}
