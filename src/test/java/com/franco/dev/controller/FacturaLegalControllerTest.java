package com.franco.dev.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.franco.dev.dto.factura.CrearFacturaLegalRequestDTO;
import com.franco.dev.dto.factura.ErrorResponseDTO;
import com.franco.dev.service.financiero.FacturaLegalApiService;
import graphql.GraphQLException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Un corte del builder (issue #170) no es un error interno: la API lo devuelve como 400 con el
 * motivo, que es lo que el central le muestra al usuario.
 */
class FacturaLegalControllerTest {

    private final FacturaLegalApiService service = mock(FacturaLegalApiService.class);
    private final FacturaLegalController controller = new FacturaLegalController(service, new ObjectMapper());

    @Test
    void elCorteDelBuilderEsUn400ConElMotivo() {
        when(service.crearFacturaLegal(any()))
                .thenThrow(new GraphQLException("FACTURA_NO_EMITIBLE: El precio unitario del item 'REGALO' ..."));

        ResponseEntity<?> respuesta = controller.crearFacturaLegal(new CrearFacturaLegalRequestDTO());

        assertEquals(HttpStatus.BAD_REQUEST, respuesta.getStatusCode());
        ErrorResponseDTO cuerpo = (ErrorResponseDTO) respuesta.getBody();
        assertEquals("FACTURA_NO_EMITIBLE: El precio unitario del item 'REGALO' ...", cuerpo.getMensaje());
    }

    @Test
    void unaFallaInesperadaSigueSiendoUn500() {
        when(service.crearFacturaLegal(any())).thenThrow(new RuntimeException("se cayo la base"));

        ResponseEntity<?> respuesta = controller.crearFacturaLegal(new CrearFacturaLegalRequestDTO());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, respuesta.getStatusCode());
    }
}
