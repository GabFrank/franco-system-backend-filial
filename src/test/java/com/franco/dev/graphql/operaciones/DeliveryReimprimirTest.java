package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Delivery;
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
    void sinDeliveryDiceQueNoExiste() {
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> deliveryGraphQL.reimprimirDelivery(99L, "ticket", "CAJA 1"));
        assertEquals("Delivery no encontrado", e.getMessage());
    }
}
