package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import com.franco.dev.graphql.operaciones.input.CobroInput;
import com.franco.dev.service.financiero.FormaPagoService;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.operaciones.CobroDetalleService;
import com.franco.dev.service.operaciones.CobroService;
import com.franco.dev.service.seguridad.AuditorUsuarioId;
import graphql.GraphQLException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Una línea de cobro sin monto no se graba (#286): la fila 297400 de la filial 18 (pago con valor NULL de un
 * delivery) rompía el balance de toda la caja y nadie podía hacer su retiro.
 */
class CobroDetalleValorNuloTest {

    private final CobroDetalleService detalleService = Mockito.mock(CobroDetalleService.class);
    private final CobroDetalleGraphQL detalleGraphQL = new CobroDetalleGraphQL();

    CobroDetalleValorNuloTest() {
        ReflectionTestUtils.setField(detalleGraphQL, "service", detalleService);
        ReflectionTestUtils.setField(detalleGraphQL, "auditorUsuarioId", Mockito.mock(AuditorUsuarioId.class));
        // findById de los mocks devuelve Optional.empty(): basta para el alta
        ReflectionTestUtils.setField(detalleGraphQL, "monedaService", Mockito.mock(MonedaService.class));
        ReflectionTestUtils.setField(detalleGraphQL, "formaPagoService", Mockito.mock(FormaPagoService.class));
        ReflectionTestUtils.setField(detalleGraphQL, "cobroService", Mockito.mock(CobroService.class));
        when(detalleService.saveAndSend(any(CobroDetalle.class), any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void guardarDetalleSinMontoNoGraba() {
        assertNull(detalleGraphQL.guardarDetalle(linea(null, false)));
        verify(detalleService, never()).saveAndSend(any(), any());
    }

    @Test
    void guardarDetalleConMontoGrabaComoSiempre() {
        CobroDetalle cd = detalleGraphQL.guardarDetalle(linea(100_000.0, false));
        assertEquals(100_000.0, cd.getValor());
        verify(detalleService, times(1)).saveAndSend(any(), any());
    }

    @Test
    void saveCobroDetalleSinMontoRechazaConMensaje() {
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> detalleGraphQL.saveCobroDetalle(linea(null, false)));
        assertEquals("La línea de cobro no tiene monto: no se guardó.", e.getMessage());
        verify(detalleService, never()).saveAndSend(any(), any());
    }

    @Test
    void editarUnaLineaGuardadaDejandolaSinMontoSeRechaza() {
        // Antes pisaba el monto guardado con NULL
        CobroDetalle guardada = new CobroDetalle();
        guardada.setId(297400L);
        when(detalleService.findById(297400L)).thenReturn(java.util.Optional.of(guardada));
        CobroDetalleInput in = linea(null, false);
        in.setId(297400L);
        assertThrows(GraphQLException.class, () -> detalleGraphQL.saveCobroDetalle(in));
        verify(detalleService, never()).saveAndSend(any(), any());
    }

    @Test
    void laListaDevuelveNullEnLaLineaSinMonto() {
        java.util.List<CobroDetalle> r = detalleGraphQL.saveCobroDetalleList(
                Arrays.asList(linea(100_000.0, false), linea(null, false)));
        assertEquals(2, r.size());
        assertEquals(100_000.0, r.get(0).getValor());
        assertNull(r.get(1));
        verify(detalleService, times(1)).saveAndSend(any(), any());
    }

    @Test
    void alConcluirElDeliveryUnDescuentoSinMontoNoDescartaAlValido() {
        // saveVentaDelivery tiene su propio control de un solo descuento
        com.franco.dev.service.operaciones.VentaService ventaService = Mockito.mock(com.franco.dev.service.operaciones.VentaService.class);
        com.franco.dev.service.operaciones.DeliveryService deliveryService = Mockito.mock(com.franco.dev.service.operaciones.DeliveryService.class);
        CobroDetalleGraphQL detalles = Mockito.mock(CobroDetalleGraphQL.class);
        Cobro cobro = new Cobro();
        cobro.setId(238449L);
        com.franco.dev.domain.personas.Usuario cajero = new com.franco.dev.domain.personas.Usuario();
        cajero.setId(553L);
        com.franco.dev.domain.operaciones.Delivery delivery = new com.franco.dev.domain.operaciones.Delivery();
        delivery.setId(21244L);
        delivery.setUsuario(cajero);
        delivery.setSucursalId(18L);
        com.franco.dev.domain.operaciones.Venta venta = new com.franco.dev.domain.operaciones.Venta();
        venta.setId(237983L);
        venta.setCobro(cobro);
        venta.setDelivery(delivery);
        when(ventaService.findById(237983L)).thenReturn(java.util.Optional.of(venta));
        when(ventaService.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(deliveryService.findById(21244L)).thenReturn(java.util.Optional.of(delivery));
        when(detalleService.findByCobroId(238449L)).thenReturn(new java.util.ArrayList<>());
        VentaGraphQL ventaGraphQL = new VentaGraphQL();
        ReflectionTestUtils.setField(ventaGraphQL, "service", ventaService);
        ReflectionTestUtils.setField(ventaGraphQL, "deliveryService", deliveryService);
        ReflectionTestUtils.setField(ventaGraphQL, "pdvCajaService", Mockito.mock(com.franco.dev.service.financiero.PdvCajaService.class));
        ReflectionTestUtils.setField(ventaGraphQL, "cobroDetalleService", detalleService);
        ReflectionTestUtils.setField(ventaGraphQL, "cobroDetalleGraphQL", detalles);
        ReflectionTestUtils.setField(ventaGraphQL, "eventoEmisor", Mockito.mock(com.franco.dev.eventos.EventoEmisor.class));
        com.franco.dev.graphql.operaciones.input.VentaInput vi = new com.franco.dev.graphql.operaciones.input.VentaInput();
        vi.setId(237983L);
        com.franco.dev.graphql.operaciones.input.DeliveryInput di = new com.franco.dev.graphql.operaciones.input.DeliveryInput();
        di.setId(21244L);

        CobroDetalleInput descuento = linea(2_000.0, true);
        ventaGraphQL.saveVentaDelivery(vi, di, Arrays.asList(linea(100_000.0, false), linea(null, true), descuento), null, null);

        ArgumentCaptor<CobroDetalleInput> enviados = ArgumentCaptor.forClass(CobroDetalleInput.class);
        verify(detalles, times(3)).guardarDetalle(enviados.capture());
        assertSame(descuento, enviados.getAllValues().get(2), "el descuento con monto no se descarta");
    }

    @Test
    void unDescuentoSinMontoNoDescartaAlDescuentoValido() {
        // El camino de saveVenta y de saveDeliveryAndVenta (alta del delivery)
        CobroDetalleGraphQL detalles = Mockito.mock(CobroDetalleGraphQL.class);
        CobroService cobroService = Mockito.mock(CobroService.class);
        Cobro cobro = new Cobro();
        cobro.setId(238449L);
        when(cobroService.saveAndSend(any(Cobro.class), any())).thenReturn(cobro);
        CobroGraphQL cobroGraphQL = new CobroGraphQL();
        ReflectionTestUtils.setField(cobroGraphQL, "service", cobroService);
        ReflectionTestUtils.setField(cobroGraphQL, "cobroDetalleGraphQL", detalles);
        ReflectionTestUtils.setField(cobroGraphQL, "auditorUsuarioId", Mockito.mock(AuditorUsuarioId.class));

        CobroDetalleInput pago = linea(100_000.0, false);
        CobroDetalleInput descuentoSinMonto = linea(null, true);
        CobroDetalleInput descuento = linea(2_000.0, true);
        assertSame(cobro, cobroGraphQL.guardarCobro(new CobroInput(),
                Arrays.asList(pago, descuentoSinMonto, descuento), null, null));

        ArgumentCaptor<CobroDetalleInput> enviados = ArgumentCaptor.forClass(CobroDetalleInput.class);
        verify(detalles, times(3)).guardarDetalle(enviados.capture());
        assertSame(descuento, enviados.getAllValues().get(2), "el descuento con monto no se descarta");
    }

    private static CobroDetalleInput linea(Double valor, boolean descuento) {
        CobroDetalleInput in = new CobroDetalleInput();
        in.setCobroId(238449L);
        in.setMonedaId(1L);
        in.setFormaPagoId(1L);
        in.setCambio(1.0);
        in.setValor(valor);
        in.setPago(!descuento);
        in.setDescuento(descuento);
        in.setVuelto(false);
        in.setAumento(false);
        return in;
    }
}
