package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.graphql.financiero.FacturaLegalGraphQL;
import com.franco.dev.service.financiero.FacturaLegalService;
import org.mockito.InOrder;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.repository.operaciones.VentaRepository;
import com.franco.dev.service.operaciones.DeliveryService;
import com.franco.dev.service.operaciones.VentaService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Reimprimir un delivery espera al envio y dice por que fallo, no «Delivery no encontrado» (#213). */
class DeliveryReimprimirTest {

    @Mock private DeliveryService service;
    @Mock private VentaService ventaService;
    @Mock private VentaRepository ventaRepository;
    @Mock private VentaItemGraphQL ventaItemGraphQL;
    @Mock private VentaGraphQL ventaGraphQL;
    @Mock private com.franco.dev.service.financiero.PdvCajaService pdvCajaService;
    @Mock private FacturaLegalService facturaLegalService;
    @Mock private FacturaLegalGraphQL facturaLegalGraphQL;
    @InjectMocks private DeliveryGraphQL deliveryGraphQL;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        Delivery delivery = new Delivery();
        delivery.setId(10L);
        delivery.setSucursalId(24L);
        Venta venta = new Venta();
        venta.setId(20L);
        when(service.findById(10L)).thenReturn(Optional.of(delivery));
        when(ventaService.getRepository()).thenReturn(ventaRepository);
        when(ventaRepository.findByDeliveryIdAndSucursalId(10L, 24L)).thenReturn(venta);
        when(ventaItemGraphQL.ventaItemListPorVentaId(20L, null)).thenReturn(Collections.emptyList());
    }

    @Test
    void esperaElEnvioYDevuelveElResultado() throws Exception {
        when(ventaGraphQL.printTicket58mm(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                isNull(), eq(true))).thenReturn(true);
        assertTrue(deliveryGraphQL.reimprimirDelivery(10L, "ticket", "CAJA 1"));

        when(ventaGraphQL.printTicket58mm(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                isNull(), eq(true))).thenReturn(null);
        assertFalse(deliveryGraphQL.reimprimirDelivery(10L, "ticket", "CAJA 1"));
    }

    @Test
    void unaFallaDeImpresionDiceElMotivo() throws Exception {
        when(ventaGraphQL.printTicket58mm(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                isNull(), eq(true))).thenThrow(new IOException("No se pudo imprimir: Printer is not accepting job."));
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> deliveryGraphQL.reimprimirDelivery(10L, "ticket", "CAJA 1"));
        assertEquals("No se pudo reimprimir el delivery: No se pudo imprimir: Printer is not accepting job.",
                e.getMessage());
    }

    @Test
    void unDeliveryDeOtraCajaSeRechazaSinImprimir() throws Exception {
        doThrow(new GraphQLException("No autorizado")).when(pdvCajaService).exigirAccesoARegistroDeCaja(any());
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> deliveryGraphQL.reimprimirDelivery(10L, "ticket", "CAJA 1"));
        assertEquals("No autorizado", e.getMessage());
        verifyNoInteractions(ventaGraphQL);
    }

    /** #211: con factura, primero la factura y despues el ticket. */
    @Test
    void conFacturaReimprimeFacturaYDespuesElTicket() throws Exception {
        FacturaLegal factura = new FacturaLegal();
        factura.setId(30L);
        when(facturaLegalService.findVigenteDeVenta(20L, 24L)).thenReturn(factura);
        when(facturaLegalGraphQL.reimprimirFacturaLegal(30L, 24L, "ticket")).thenReturn(true);
        when(ventaGraphQL.printTicket58mm(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                isNull(), eq(true))).thenReturn(true);

        assertTrue(deliveryGraphQL.reimprimirDelivery(10L, "ticket", "CAJA 1"));
        InOrder orden = inOrder(facturaLegalGraphQL, ventaGraphQL);
        orden.verify(facturaLegalGraphQL).reimprimirFacturaLegal(30L, 24L, "ticket");
        orden.verify(ventaGraphQL).printTicket58mm(any(), isNull(), any(), isNull(), eq(true), eq("ticket"),
                eq("CAJA 1"), eq(false), isNull(), any(), isNull(), eq(true));
    }

    /** Una factura que no sale (impresora, electronica sin DE) no frena el ticket, pero se avisa. */
    @Test
    void siLaFacturaFallaElTicketSaleIgualYSeAvisa() throws Exception {
        FacturaLegal factura = new FacturaLegal();
        factura.setId(30L);
        when(facturaLegalService.findVigenteDeVenta(20L, 24L)).thenReturn(factura);
        when(facturaLegalGraphQL.reimprimirFacturaLegal(30L, 24L, "ticket")).thenReturn(false);
        when(ventaGraphQL.printTicket58mm(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                isNull(), eq(true))).thenReturn(true);

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> deliveryGraphQL.reimprimirDelivery(10L, "ticket", "CAJA 1"));
        assertEquals("No se pudo reimprimir la factura del delivery", e.getMessage());
        verify(ventaGraphQL).printTicket58mm(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                isNull(), eq(true));
    }

    @Test
    void siFallanLaFacturaYElTicketElMensajeNombraLosDos() throws Exception {
        FacturaLegal factura = new FacturaLegal();
        factura.setId(30L);
        when(facturaLegalService.findVigenteDeVenta(20L, 24L)).thenReturn(factura);
        when(facturaLegalGraphQL.reimprimirFacturaLegal(30L, 24L, "ticket")).thenReturn(false);
        when(ventaGraphQL.printTicket58mm(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                isNull(), eq(true))).thenThrow(new IOException("No se pudo imprimir: Printer is not accepting job."));

        GraphQLException e = assertThrows(GraphQLException.class,
                () -> deliveryGraphQL.reimprimirDelivery(10L, "ticket", "CAJA 1"));
        assertEquals("No se pudo reimprimir la factura ni el delivery: No se pudo imprimir: Printer is not accepting job.",
                e.getMessage());
    }

    @Test
    void sinFacturaNoReimprimeNingunaFactura() throws Exception {
        when(ventaGraphQL.printTicket58mm(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                isNull(), eq(true))).thenReturn(true);
        assertTrue(deliveryGraphQL.reimprimirDelivery(10L, "ticket", "CAJA 1"));
        verifyNoInteractions(facturaLegalGraphQL);
    }

    @Test
    void sinDeliveryDiceQueNoExiste() {
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> deliveryGraphQL.reimprimirDelivery(99L, "ticket", "CAJA 1"));
        assertEquals("Delivery no encontrado", e.getMessage());
    }
}
