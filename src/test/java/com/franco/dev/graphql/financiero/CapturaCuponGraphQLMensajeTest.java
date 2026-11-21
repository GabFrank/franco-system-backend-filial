package com.franco.dev.graphql.financiero;

import com.franco.dev.service.financiero.CapturaCuponService;
import graphql.GraphQLException;
import org.junit.jupiter.api.Test;
import org.springframework.objenesis.ObjenesisStd;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * #151: el rechazo del servicio llega al desktop con su texto. GraphqlExceptionHandler solo deja ver
 * el de una GraphQLException; una IllegalStateException salia como "error generico".
 */
class CapturaCuponGraphQLMensajeTest {

    private static final String APAGADO = "la venta con tarjeta esta deshabilitada";

    private CapturaCuponGraphQL resolver(CapturaCuponService service) {
        CapturaCuponGraphQL g = new ObjenesisStd().newInstance(CapturaCuponGraphQL.class);
        ReflectionTestUtils.setField(g, "service", service);
        return g;
    }

    @Test
    void crearRechazadoLlegaComoGraphQLExceptionConElTexto() {
        CapturaCuponService service = mock(CapturaCuponService.class);
        when(service.crear(any(), any(), any(), any())).thenThrow(new IllegalStateException(APAGADO));
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> resolver(service).crearCapturaCupon(10L, 24L, null, null));
        assertEquals(APAGADO, e.getMessage());
    }

    @Test
    void derivarRechazadoLlegaComoGraphQLExceptionConElTexto() {
        CapturaCuponService service = mock(CapturaCuponService.class);
        when(service.derivarMapa("tok")).thenThrow(new IllegalStateException(APAGADO));
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> resolver(service).derivarMapaDeCaptura("tok"));
        assertEquals(APAGADO, e.getMessage());
    }
}
