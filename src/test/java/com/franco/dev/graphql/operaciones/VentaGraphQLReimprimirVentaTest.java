package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.graphql.financiero.FacturaLegalGraphQL;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.operaciones.VentaService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** «Reimprimir» de «Ultimas ventas»: true solo si el papel salio; si no, el motivo (#213). */
class VentaGraphQLReimprimirVentaTest {

    @Mock private VentaService service;
    @Mock private FacturaLegalService facturaLegalService;
    @Mock private FacturaLegalGraphQL facturaLegalGraphQL;
    @Mock private CobroGraphQL cobroGraphQL;
    @Mock private VentaItemGraphQL ventaItemGraphQL;
    @Mock private com.franco.dev.service.financiero.PdvCajaService pdvCajaService;
    @InjectMocks private VentaGraphQL ventaGraphQL;

    private VentaGraphQL spy;
    private Venta venta;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        ventaGraphQL.cobroGraphQL = cobroGraphQL;
        ventaGraphQL.ventaItemGraphQL = ventaItemGraphQL;
        spy = spy(ventaGraphQL);
        venta = new Venta();
        venta.setId(770L);
        Cobro cobro = new Cobro();
        cobro.setId(5L);
        venta.setCobro(cobro);
        when(service.findById(770L)).thenReturn(Optional.of(venta));
        when(cobroGraphQL.cobro(5L, null)).thenReturn(Optional.of(cobro));
        when(ventaItemGraphQL.ventaItemListPorVentaId(770L, null)).thenReturn(Collections.emptyList());
    }

    private void conFactura(Boolean reimpresa) {
        FacturaLegal factura = new FacturaLegal();
        factura.setId(9L);
        when(facturaLegalService.findByVentaId(770L)).thenReturn(factura);
        when(facturaLegalGraphQL.reimprimirFacturaLegal(9L, 1L, "ticket")).thenReturn(reimpresa);
    }

    private void ticketDevuelve(Boolean impreso) throws Exception {
        doReturn(impreso).when(spy).printTicket58mm(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                isNull(), anyBoolean());
    }

    @Test
    void conFacturaImpresaDevuelveTrue() throws Exception {
        conFactura(true);
        assertTrue(spy.reimprimirVenta(770L, "ticket", "Caja 1", 1L));
    }

    @Test
    void conFacturaQueNoSalioAvisa() {
        conFactura(false);
        GraphQLException e = assertThrows(GraphQLException.class, () -> spy.reimprimirVenta(770L, "ticket", "Caja 1", 1L));
        assertEquals("No se pudo reimprimir la factura", e.getMessage());
    }

    @Test
    void sinFacturaElTicketImpresoDevuelveTrueYEsperaElEnvio() throws Exception {
        ticketDevuelve(true);
        assertTrue(spy.reimprimirVenta(770L, "ticket", "Caja 1", 1L));
        verify(spy).printTicket58mm(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), isNull(), eq(true));
    }

    @Test
    void sinFacturaElTicketQueNoSalioAvisa() throws Exception {
        ticketDevuelve(false);
        GraphQLException e = assertThrows(GraphQLException.class, () -> spy.reimprimirVenta(770L, "ticket", "Caja 1", 1L));
        assertEquals("No se pudo reimprimir el ticket", e.getMessage());
    }

    @Test
    void sinFacturaUnaFallaAlEscribirAvisaConElMotivo() throws Exception {
        doThrow(new java.io.IOException("No se pudo imprimir: Printer is not accepting job.")).when(spy)
                .printTicket58mm(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), isNull(), anyBoolean());
        GraphQLException e = assertThrows(GraphQLException.class, () -> spy.reimprimirVenta(770L, "ticket", "Caja 1", 1L));
        assertEquals("No se pudo reimprimir el ticket: No se pudo imprimir: Printer is not accepting job.", e.getMessage());
    }

    @Test
    void ventaInexistenteAvisa() {
        GraphQLException e = assertThrows(GraphQLException.class, () -> spy.reimprimirVenta(1L, "ticket", "Caja 1", 1L));
        assertEquals("Venta no encontrada", e.getMessage());
    }

    @Test
    void ventaSinCobroAvisa() {
        venta.setCobro(null);
        GraphQLException e = assertThrows(GraphQLException.class, () -> spy.reimprimirVenta(770L, "ticket", "Caja 1", 1L));
        assertEquals("La venta no tiene cobro", e.getMessage());
    }

    @Test
    void unaVentaDeOtraCajaSeRechazaSinImprimir() throws Exception {
        doThrow(new GraphQLException("No autorizado")).when(pdvCajaService).exigirAccesoARegistroDeCaja(any());
        assertThrows(GraphQLException.class, () -> ventaGraphQL.reimprimirVenta(770L, "ticket", "CAJA 1", null));
        verifyNoInteractions(facturaLegalService, facturaLegalGraphQL, cobroGraphQL);
    }
}
