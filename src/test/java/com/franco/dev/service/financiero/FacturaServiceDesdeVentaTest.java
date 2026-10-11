package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.service.financiero.builder.BuildRequest;
import com.franco.dev.service.financiero.builder.FacturaLegalBuilder;
import com.franco.dev.service.operaciones.CobroDetalleService;
import com.franco.dev.service.operaciones.AjusteCobro;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Que significa el null de {@code crearFacturaLegalDesdeVenta} (issue #169): solo "corto una
 * validacion del builder, no se escribio nada y la venta sigue viva". Una falla posterior deja la
 * transaccion rollback-only y tiene que salir como excepcion: convertida en null, el que llama
 * imprimiria un comprobante y devolveria el turno de una venta que se va a revertir.
 */
class FacturaServiceDesdeVentaTest {

    @Mock private FacturaLegalBuilder facturaLegalBuilder;
    @Mock private CobroDetalleService cobroDetalleService;
    @InjectMocks private FacturaService service;

    private final Venta venta = new Venta();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        when(cobroDetalleService.ajusteDe(any(), any()))
                .thenReturn(AjusteCobro.deDetalles(Collections.emptyList()));
    }

    private FacturaLegal crear() {
        return service.crearFacturaLegalDesdeVenta(venta, Collections.<VentaItem>emptyList(), 3L, null);
    }

    @Test
    void validacionDelBuilder_devuelveNull() {
        when(facturaLegalBuilder.build(any(BuildRequest.class)))
                .thenThrow(new GraphQLException("TIMBRADO_SIN_TELEFONO: ..."));

        assertNull(crear());
    }

    @Test
    void fallaPosteriorAUnWrite_seRelanza() {
        when(facturaLegalBuilder.build(any(BuildRequest.class)))
                .thenThrow(new IllegalStateException("fallo el insert"));

        assertThrows(IllegalStateException.class, this::crear);
    }

    @Test
    void facturaCreada_laDevuelve() {
        FacturaLegal factura = new FacturaLegal();
        when(facturaLegalBuilder.build(any(BuildRequest.class))).thenReturn(factura);

        assertSame(factura, crear());
    }

    /** Lo que el cajero puede corregir no sigue sin factura: sale para rechazar la venta. */
    @Test
    void ventaNoFacturable_seRelanza() {
        when(facturaLegalBuilder.build(any(BuildRequest.class)))
                .thenThrow(new com.franco.dev.service.financiero.builder.VentaNoFacturableException("cargue el cliente"));

        assertThrows(com.franco.dev.service.financiero.builder.VentaNoFacturableException.class, this::crear);
    }
}
