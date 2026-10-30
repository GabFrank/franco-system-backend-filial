package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.financiero.FacturaLegal;
import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.enums.DeliveryEstado;
import com.franco.dev.graphql.financiero.FacturaLegalGraphQL;
import com.franco.dev.repository.operaciones.VentaRepository;
import com.franco.dev.domain.financiero.ConfiguracionFacturacion;
import com.franco.dev.service.financiero.BloqueoTransaccionalService;
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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Reintentar PARA ENTREGA despues de una falla de impresion no crea otra factura (issue #207), ni dos pedidos juntos
 * (issue #210: lock por venta y transaccion; la carrera real la cubre la prueba de runtime del plan).
 */
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
    @Mock private BloqueoTransaccionalService bloqueo;
    @Mock private PlatformTransactionManager transactionManager;

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

    /** #210: buscar y crear van bajo el lock de la venta y en una transaccion; la impresion, despues del commit. */
    @Test
    void buscaYCreaBajoElLockDeLaVentaEImprimeDespuesDelCommit() throws Exception {
        FacturaLegal nueva = new FacturaLegal();
        when(facturaService.crearFacturaLegalDesdeVenta(eq(venta), any(), eq(PDV), isNull())).thenReturn(nueva);

        deliveryGraphQL.saveDeliveryEstado(10L, DeliveryEstado.PARA_ENTREGA, "ticket", null, PDV, false);

        InOrder orden = inOrder(configuracionFacturacionLector, transactionManager, bloqueo, facturaLegalService,
                politicaFacturacionService, facturaService, facturaLegalGraphQL);
        orden.verify(configuracionFacturacionLector).resolver();
        orden.verify(transactionManager).getTransaction(any());
        orden.verify(bloqueo).tomar("factura-venta:24:20", "15s");
        orden.verify(facturaLegalService).findVigenteDeVenta(20L, 24L);
        orden.verify(politicaFacturacionService).facturarDelivery(eq(PDV), any());
        orden.verify(facturaService).crearFacturaLegalDesdeVenta(eq(venta), any(), eq(PDV), isNull());
        orden.verify(transactionManager).commit(any());
        orden.verify(facturaLegalGraphQL).printTicket58mmFactura(eq(venta), same(nueva), isNull(), eq("ticket"),
                isNull());
    }

    @Test
    void siVenceLaEsperaDelLockAvisaYNoFactura() {
        doThrow(new RuntimeException(new SQLException("canceling statement due to lock timeout", "55P03")))
                .when(bloqueo).tomar(anyString(), anyString());

        GraphQLException e = assertThrows(GraphQLException.class, () -> deliveryGraphQL
                .saveDeliveryEstado(10L, DeliveryEstado.PARA_ENTREGA, "ticket", null, PDV, false));

        assertEquals(DeliveryGraphQL.MENSAJE_FACTURA_EN_CURSO, e.getMessage());
        verify(transactionManager).rollback(any());
        verify(politicaFacturacionService, never()).facturarDelivery(any(), any());
        verify(facturaService, never()).crearFacturaLegalDesdeVenta(any(), any(), any(), any());
    }

    /** Una falla del DE sale recien en el commit: sigue siendo un problema de la factura, y no se imprime. */
    @Test
    void siFallaElCommitEsUnProblemaDeLaFacturaYNoImprime() throws Exception {
        when(facturaService.crearFacturaLegalDesdeVenta(eq(venta), any(), eq(PDV), isNull()))
                .thenReturn(new FacturaLegal());
        doThrow(new UnexpectedRollbackException("rollback-only")).when(transactionManager).commit(any());

        GraphQLException e = assertThrows(GraphQLException.class, () -> deliveryGraphQL
                .saveDeliveryEstado(10L, DeliveryEstado.PARA_ENTREGA, "ticket", null, PDV, false));

        assertTrue(e.getMessage().startsWith("Problema al generar factura electrónica"), e.getMessage());
        verify(facturaLegalGraphQL, never()).printTicket58mmFactura(any(), any(), any(), any(), any());
    }

    /** Turno del modo intervalo: se devuelve solo si la factura fallo en una validacion previa a escribir. */
    private void politicaIntervaloQueRespeta() {
        when(configuracionFacturacionLector.resolver()).thenReturn(new PoliticaFacturacion(
                ConfiguracionFacturacion.MODO_INTERVALO, 3, true, PoliticaFacturacion.Origen.GLOBAL));
    }

    @Test
    void siElBuilderRechazaAntesDeEscribirDevuelveElTurno() {
        politicaIntervaloQueRespeta();
        when(facturaService.crearFacturaLegalDesdeVenta(eq(venta), any(), eq(PDV), isNull())).thenReturn(null);

        GraphQLException e = assertThrows(GraphQLException.class, () -> deliveryGraphQL
                .saveDeliveryEstado(10L, DeliveryEstado.PARA_ENTREGA, "ticket", null, PDV, false));

        assertEquals("Problema al generar factura electrónica: Problema al crear factura legal", e.getMessage());
        verify(politicaFacturacionService).devolverTurno();
    }

    @Test
    void siVenceLaEsperaNoDevuelveUnTurnoQueNoTomo() {
        politicaIntervaloQueRespeta();
        doThrow(new RuntimeException(new SQLException("canceling statement due to lock timeout", "55P03")))
                .when(bloqueo).tomar(anyString(), anyString());

        assertThrows(GraphQLException.class, () -> deliveryGraphQL
                .saveDeliveryEstado(10L, DeliveryEstado.PARA_ENTREGA, "ticket", null, PDV, false));

        verify(politicaFacturacionService, never()).devolverTurno();
    }

    @Test
    void siFallaElDeEnElCommitNoDevuelveElTurno() {
        politicaIntervaloQueRespeta();
        when(facturaService.crearFacturaLegalDesdeVenta(eq(venta), any(), eq(PDV), isNull()))
                .thenReturn(new FacturaLegal());
        doThrow(new UnexpectedRollbackException("rollback-only")).when(transactionManager).commit(any());

        assertThrows(GraphQLException.class, () -> deliveryGraphQL
                .saveDeliveryEstado(10L, DeliveryEstado.PARA_ENTREGA, "ticket", null, PDV, false));

        verify(politicaFacturacionService, never()).devolverTurno();
    }
}
