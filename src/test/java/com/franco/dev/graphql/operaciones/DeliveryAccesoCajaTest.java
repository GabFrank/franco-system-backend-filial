package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.enums.DeliveryEstado;
import com.franco.dev.graphql.operaciones.input.CobroInput;
import com.franco.dev.graphql.operaciones.input.DeliveryInput;
import com.franco.dev.graphql.operaciones.input.VentaInput;
import com.franco.dev.repository.operaciones.VentaRepository;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.operaciones.DeliveryService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.seguridad.AuditorUsuarioId;
import com.franco.dev.service.seguridad.RolSesionService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Un delivery lo opera solo el cajero de la caja de su venta, o ANALISIS DE CAJA; sin escribir nada si no. */
class DeliveryAccesoCajaTest {

    @Mock private DeliveryService service;
    @Mock private VentaService ventaService;
    @Mock private VentaRepository ventaRepository;
    @Mock private PdvCajaService pdvCajaService;
    @Mock private RolSesionService rolSesionService;
    @Mock private AuditorUsuarioId auditorUsuarioId;
    @Mock private CobroGraphQL cobroGraphQL;
    @Mock private VentaGraphQL ventaGraphQL;
    @InjectMocks private DeliveryGraphQL deliveryGraphQL;

    private PdvCaja cajaAjena;
    private Delivery delivery;
    private Venta venta;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        cajaAjena = new PdvCaja();
        cajaAjena.setId(1185L);
        delivery = new Delivery();
        delivery.setId(10L);
        delivery.setSucursalId(24L);
        venta = new Venta();
        venta.setId(20L);
        venta.setCaja(cajaAjena);
        venta.setDelivery(delivery);
        Cobro cobro = new Cobro();
        cobro.setId(30L);
        venta.setCobro(cobro);
        when(service.findById(10L)).thenReturn(Optional.of(delivery));
        when(ventaService.getRepository()).thenReturn(ventaRepository);
        when(ventaRepository.findByDeliveryIdAndSucursalId(10L, 24L)).thenReturn(venta);
    }

    private void cajaAjenaRechaza() {
        doThrow(new GraphQLException("No autorizado")).when(pdvCajaService).exigirAccesoARegistroDeCaja(same(cajaAjena));
        doThrow(new GraphQLException("No autorizado")).when(pdvCajaService).exigirAccesoACaja(1185L);
    }

    private void sinEscrituras() {
        verify(service, never()).saveAndSend(any(), anyBoolean());
        verify(service, never()).save(any());
        verify(cobroGraphQL, never()).saveCobro(any(), any(), any());
        verify(ventaGraphQL, never()).saveVenta2(any());
    }

    private static String mensaje(Executable e) {
        return assertThrows(GraphQLException.class, e).getMessage();
    }

    private DeliveryInput deliveryInput(Long id) {
        DeliveryInput d = new DeliveryInput();
        d.setId(id);
        return d;
    }

    private VentaInput ventaInput(Long id, Long cajaId) {
        VentaInput v = new VentaInput();
        v.setId(id);
        v.setCajaId(cajaId);
        return v;
    }

    private CobroInput cobroInput(Long id) {
        CobroInput c = new CobroInput();
        c.setId(id);
        return c;
    }

    @Test
    void verUnDeliveryDeOtraCajaSeRechaza() {
        cajaAjenaRechaza();
        assertThrows(GraphQLException.class, () -> deliveryGraphQL.delivery(10L, null));
    }

    @Test
    void verUnDeliverySinVentaPasa() {
        when(ventaRepository.findByDeliveryIdAndSucursalId(10L, 24L)).thenReturn(null);
        assertSame(delivery, deliveryGraphQL.delivery(10L, null).orElse(null));
        verifyNoInteractions(pdvCajaService);
    }

    @Test
    void marcarPorEstadoUnDeliveryDeOtraCajaNoEscribe() {
        cajaAjenaRechaza();
        assertThrows(GraphQLException.class, () -> deliveryGraphQL
                .saveDeliveryEstado(10L, DeliveryEstado.PARA_ENTREGA, "x", null, 25L, false));
        assertNotEquals(DeliveryEstado.PARA_ENTREGA, delivery.getEstado());
        sinEscrituras();
    }

    @Test
    void marcarPorEstadoUnDeliveryInexistenteLoDice() {
        assertEquals("Delivery no encontrado", mensaje(() -> deliveryGraphQL
                .saveDeliveryEstado(99L, DeliveryEstado.PARA_ENTREGA, "x", null, 25L, false)));
    }

    @Test
    void editarUnDeliveryDeOtraCajaNoEscribe() {
        cajaAjenaRechaza();
        assertThrows(GraphQLException.class, () -> deliveryGraphQL.saveDeliveryAndVenta(deliveryInput(10L),
                ventaInput(20L, 5L), null, null, null, cobroInput(30L), null));
        sinEscrituras();
    }

    @Test
    void editarElDeliveryPropioConUnaVentaQueNoEsLaSuyaSeRechaza() {
        assertEquals("La venta no corresponde a este delivery", mensaje(() -> deliveryGraphQL
                .saveDeliveryAndVenta(deliveryInput(10L), ventaInput(77L, 5L), null, null, null, null, null)));
        sinEscrituras();
    }

    @Test
    void unDeliveryNuevoConUnaVentaExistenteSeRechaza() {
        assertEquals("La venta no corresponde a este delivery", mensaje(() -> deliveryGraphQL
                .saveDeliveryAndVenta(deliveryInput(null), ventaInput(20L, 5L), null, null, null, null, null)));
        sinEscrituras();
    }

    @Test
    void editarConUnCobroQueNoEsElDeLaVentaSeRechaza() {
        assertEquals("El cobro no corresponde a esta venta", mensaje(() -> deliveryGraphQL
                .saveDeliveryAndVenta(deliveryInput(10L), ventaInput(20L, 5L), null, null, null, cobroInput(88L),
                        null)));
        sinEscrituras();
    }

    @Test
    void unDeliveryNuevoEnLaCajaDeOtroSeRechaza() {
        cajaAjenaRechaza();
        when(pdvCajaService.findById(1185L)).thenReturn(Optional.of(cajaAjena));
        assertThrows(GraphQLException.class, () -> deliveryGraphQL.saveDeliveryAndVenta(deliveryInput(null),
                ventaInput(null, 1185L), null, null, null, cobroInput(null), null));
        sinEscrituras();
    }

    @Test
    void unDeliveryNuevoSinCajaSeRechaza() {
        assertEquals("Caja no encontrada", mensaje(() -> deliveryGraphQL.saveDeliveryAndVenta(deliveryInput(null),
                ventaInput(null, null), null, null, null, cobroInput(null), null)));
        sinEscrituras();
    }

    /** El movimiento de caja del cobro va a la caja de la venta guardada, no a la que manda el cliente. */
    @Test
    void alEditarElCobroUsaLaCajaDeLaVentaGuardada() {
        assertThrows(GraphQLException.class, () -> deliveryGraphQL.saveDeliveryAndVenta(deliveryInput(10L),
                ventaInput(20L, 5L), null, null, null, cobroInput(30L), null));
        verify(pdvCajaService).exigirAccesoARegistroDeCaja(same(cajaAjena));
        verify(cobroGraphQL).saveCobro(any(), isNull(), eq(1185L));
    }

    @Test
    void lasOperacionesSinLlamadoresPidenAnalisisDeCaja() {
        doThrow(new GraphQLException("No autorizado")).when(rolSesionService).exigirRol(RolSesionService.ANALISIS_DE_CAJA);
        assertThrows(GraphQLException.class, () -> deliveryGraphQL.deliverys(0, 10, null));
        assertThrows(GraphQLException.class, () -> deliveryGraphQL.deliverysByEstado(DeliveryEstado.ABIERTO, null));
        assertThrows(GraphQLException.class, () -> deliveryGraphQL.deliverysByEstadoList(null, null));
        assertThrows(GraphQLException.class, () -> deliveryGraphQL.deliverysByEstadoNotIn(DeliveryEstado.ABIERTO, null));
        assertThrows(GraphQLException.class, () -> deliveryGraphQL.deliverysUltimos10(null));
        assertThrows(GraphQLException.class, () -> deliveryGraphQL.countDelivery());
        assertThrows(GraphQLException.class, () -> deliveryGraphQL.deleteDelivery(10L, null));
        assertThrows(GraphQLException.class, () -> deliveryGraphQL.saveDelivery(deliveryInput(null)));
        verify(service, never()).deleteById(any());
        verify(service, never()).findAll(any());
        sinEscrituras();
    }
}
