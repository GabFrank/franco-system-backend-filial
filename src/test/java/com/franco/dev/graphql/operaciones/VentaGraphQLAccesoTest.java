package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.graphql.financiero.VentaCreditoGraphQL;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.financiero.VentaCreditoService;
import com.franco.dev.graphql.operaciones.input.DeliveryInput;
import com.franco.dev.graphql.operaciones.input.VentaInput;
import com.franco.dev.service.operaciones.CobroDetalleService;
import com.franco.dev.service.operaciones.DeliveryService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.seguridad.RolSesionService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** La venta por id pide la caja (también las de delivery); el crédito y el pagaré, ADMIN. */
class VentaGraphQLAccesoTest {

    @Mock private VentaService service;
    @Mock private PdvCajaService pdvCajaService;
    @Mock private RolSesionService rolSesionService;
    @Mock private VentaCreditoService ventaCreditoService;
    @Mock private DeliveryService deliveryService;
    @Mock private CobroDetalleService cobroDetalleService;
    @InjectMocks private VentaGraphQL ventaGraphQL;
    @InjectMocks private VentaCreditoGraphQL ventaCreditoGraphQL;

    private PdvCaja caja;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        caja = new PdvCaja();
        caja.setId(1185L);
    }

    private Venta venta(long id, Delivery delivery) {
        Venta v = new Venta();
        v.setId(id);
        v.setCaja(caja);
        v.setDelivery(delivery);
        when(service.findById(id)).thenReturn(Optional.of(v));
        return v;
    }

    @Test
    void laVentaDeOtraCajaSeRechaza() {
        venta(1L, null);
        doThrow(new GraphQLException("No autorizado")).when(pdvCajaService).exigirAccesoARegistroDeCaja(same(caja));
        assertThrows(GraphQLException.class, () -> ventaGraphQL.venta(1L, null));
    }

    @Test
    void laVentaPropiaPasaPidiendoSuCaja() {
        Venta v = venta(2L, null);
        assertSame(v, ventaGraphQL.venta(2L, null).orElse(null));
        verify(pdvCajaService).exigirAccesoARegistroDeCaja(same(caja));
    }

    @Test
    void laVentaDeUnDeliveryDeOtraCajaTambienSeRechaza() {
        venta(3L, new Delivery());
        doThrow(new GraphQLException("No autorizado")).when(pdvCajaService).exigirAccesoARegistroDeCaja(same(caja));
        assertThrows(GraphQLException.class, () -> ventaGraphQL.venta(3L, null));
    }

    private VentaInput ventaInput(Long id) {
        VentaInput v = new VentaInput();
        v.setId(id);
        return v;
    }

    private DeliveryInput deliveryInput(Long id) {
        DeliveryInput d = new DeliveryInput();
        d.setId(id);
        return d;
    }

    private Delivery delivery(long id) {
        Delivery d = new Delivery();
        d.setId(id);
        when(deliveryService.findById(id)).thenReturn(Optional.of(d));
        return d;
    }

    @Test
    void finalizarUnDeliveryDeOtraCajaNoEscribe() {
        venta(5L, delivery(10L));
        doThrow(new GraphQLException("No autorizado")).when(pdvCajaService).exigirAccesoARegistroDeCaja(same(caja));
        GraphQLException e = assertThrows(GraphQLException.class, () -> ventaGraphQL
                .saveVentaDelivery(ventaInput(5L), deliveryInput(10L), null, null, null));
        assertEquals("No autorizado", e.getMessage());
        verify(deliveryService, never()).save(any());
        verify(service, never()).save(any());
        verifyNoInteractions(cobroDetalleService);
    }

    @Test
    void finalizarConUnDeliveryQueNoEsElDeLaVentaSeRechaza() {
        venta(5L, delivery(10L));
        delivery(11L);
        GraphQLException e = assertThrows(GraphQLException.class, () -> ventaGraphQL
                .saveVentaDelivery(ventaInput(5L), deliveryInput(11L), null, null, null));
        assertEquals("La venta no corresponde a este delivery", e.getMessage());
        verify(deliveryService, never()).save(any());
        verify(service, never()).save(any());
    }

    @Test
    void finalizarSinIdsAvisaSinRomper() {
        assertThrows(GraphQLException.class, () -> ventaGraphQL
                .saveVentaDelivery(ventaInput(null), deliveryInput(null), null, null, null));
    }

    @Test
    void unaVentaInexistenteDevuelveVacio() {
        when(service.findById(4L)).thenReturn(Optional.empty());
        assertFalse(ventaGraphQL.venta(4L, null).isPresent());
    }

    @Test
    void elPagareSinAdminSeRechazaSinBuscarLaVenta() {
        doThrow(new GraphQLException("No autorizado")).when(rolSesionService).exigirAdmin();
        assertThrows(GraphQLException.class, () -> ventaGraphQL.imprimirPagare(1L, null, "ticket", "CAJA 1", null));
        verifyNoInteractions(service);
    }

    @Test
    void elCreditoSinAdminSeRechazaSinBuscarlo() {
        doThrow(new GraphQLException("No autorizado")).when(rolSesionService).exigirAdmin();
        assertThrows(GraphQLException.class, () -> ventaCreditoGraphQL.imprimirVentaCredito(1L, null, "ticket"));
        verifyNoInteractions(ventaCreditoService);
    }

    @Test
    void unCreditoInexistenteDiceQueNoSeEncontro() {
        when(ventaCreditoService.findById(9L)).thenReturn(Optional.empty());
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> ventaCreditoGraphQL.imprimirVentaCredito(9L, null, "ticket"));
        assertEquals("Venta credito no encontrada", e.getMessage());
    }
}
