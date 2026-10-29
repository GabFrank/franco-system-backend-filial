package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.enums.DeliveryEstado;
import com.franco.dev.graphql.financiero.FacturaLegalGraphQL;
import com.franco.dev.repository.operaciones.VentaRepository;
import com.franco.dev.domain.financiero.ConfiguracionFacturacion;
import com.franco.dev.service.financiero.ConfiguracionFacturacionLector;
import com.franco.dev.service.financiero.FacturaLegalService;
import com.franco.dev.service.financiero.FacturaService;
import com.franco.dev.service.financiero.PoliticaFacturacion;
import com.franco.dev.service.financiero.PoliticaFacturacionService;
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

/** Reintentar PARA ENTREGA despues de una falla de impresion no crea otra factura (issue #207). */
class DeliveryParaEntregaReintentoTest {

    private static final long PDV = 5L;

    @Mock private DeliveryService service;
    @Mock private VentaService ventaService;
    @Mock private VentaRepository ventaRepository;
    @Mock private VentaItemGraphQL ventaItemGraphQL;
    @Mock private VentaGraphQL ventaGraphQL;
    @Mock private FacturaLegalGraphQL facturaLegalGraphQL;
    @Mock private FacturaLegalService facturaLegalService;
    @Mock private FacturaService facturaService;
    @Mock private ConfiguracionFacturacionLector configuracionFacturacionLector;
    @Mock private PoliticaFacturacionService politicaFacturacionService;

    @InjectMocks private DeliveryGraphQL deliveryGraphQL;

    private Venta venta;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        Delivery delivery = new Delivery();
        delivery.setId(10L);
        delivery.setSucursalId(24L);
        delivery.setEstado(DeliveryEstado.ABIERTO);
        venta = new Venta();
        venta.setId(20L);
        when(service.findById(10L)).thenReturn(Optional.of(delivery));
        when(service.saveAndSend(any(), anyBoolean())).thenAnswer(inv -> inv.getArgument(0));
        when(ventaService.getRepository()).thenReturn(ventaRepository);
        when(ventaRepository.findByDeliveryIdAndSucursalId(10L, 24L)).thenReturn(venta);
        when(ventaItemGraphQL.ventaItemListPorVentaId(20L, null)).thenReturn(Collections.emptyList());
        when(configuracionFacturacionLector.resolver()).thenReturn(new PoliticaFacturacion(
                ConfiguracionFacturacion.MODO_TODAS, 0, false, PoliticaFacturacion.Origen.GLOBAL));
        when(politicaFacturacionService.facturarDelivery(eq(PDV), any())).thenReturn(true);
    }

    @Test
    void siLaVentaYaTieneFacturaLaReimprimeSinCrearOtraNiTomarTurno() throws Exception {
        FacturaLegal existente = new FacturaLegal();
        when(facturaLegalService.findVigenteDeVenta(20L, 24L)).thenReturn(existente);

        deliveryGraphQL.saveDeliveryEstado(10L, DeliveryEstado.PARA_ENTREGA, "ticket", null, PDV, false);

        verify(facturaLegalGraphQL).printTicket58mmFactura(eq(venta), same(existente), isNull(), eq("ticket"), isNull());
        verify(facturaService, never()).crearFacturaLegalDesdeVenta(any(), any(), any(), any());
        verify(politicaFacturacionService, never()).facturarDelivery(any(), any());
    }

    @Test
    void siFallaLaImpresoraElErrorLoDiceYNoHablaDeLaFactura() throws Exception {
        FacturaLegal nueva = new FacturaLegal();
        when(facturaService.crearFacturaLegalDesdeVenta(eq(venta), any(), eq(PDV), isNull())).thenReturn(nueva);
        doThrow(new IOException("No se pudo imprimir: Printer is not accepting job."))
                .when(facturaLegalGraphQL).printTicket58mmFactura(any(), any(), any(), any(), any());

        GraphQLException e = assertThrows(GraphQLException.class, () -> deliveryGraphQL
                .saveDeliveryEstado(10L, DeliveryEstado.PARA_ENTREGA, "ticket", null, PDV, false));

        assertTrue(e.getMessage().startsWith("No se pudo imprimir: Printer is not accepting job."), e.getMessage());
    }
}
