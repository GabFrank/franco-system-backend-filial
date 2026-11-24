package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.graphql.financiero.input.VentaCreditoInput;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import com.franco.dev.graphql.operaciones.input.DeliveryInput;
import com.franco.dev.graphql.operaciones.input.VentaInput;
import com.franco.dev.service.financiero.FormaPagoService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.operaciones.CobroDetalleService;
import com.franco.dev.service.operaciones.DeliveryService;
import com.franco.dev.service.operaciones.VentaDuplicadaCacheService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.seguridad.AuditorUsuarioId;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Una venta por convenio se paga solo por convenio y sin descuento: el filial rechaza antes de escribir nada. */
class VentaGraphQLConvenioSoloTest {

    private static final String MEZCLA = "El convenio solo se puede usar como \u00fanica forma de pago.";
    private static final String DESCUENTO = "No se puede aplicar descuento a una venta por convenio.";

    @Mock private VentaService service;
    @Mock private PdvCajaService pdvCajaService;
    @Mock private DeliveryService deliveryService;
    @Mock private CobroDetalleService cobroDetalleService;
    @Mock private CobroDetalleGraphQL cobroDetalleGraphQL;
    @Mock private CobroGraphQL cobroGraphQL;
    @Mock private FormaPagoService formaPagoService;
    @Mock private VentaDuplicadaCacheService ventaDuplicadaCacheService;
    @Mock private AuditorUsuarioId auditorUsuarioId;
    @InjectMocks private VentaGraphQL ventaGraphQL;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        com.franco.dev.service.seguridad.AuditorUsuarioIdMocks.firmarElInput(auditorUsuarioId);
        when(formaPagoService.findById(1L)).thenReturn(Optional.of(formaPago("EFECTIVO")));
        when(formaPagoService.findById(2L)).thenReturn(Optional.of(formaPago("TARJETA")));
        when(formaPagoService.findById(3L)).thenReturn(Optional.of(formaPago("CONVENIO")));
    }

    private static FormaPago formaPago(String descripcion) {
        FormaPago f = new FormaPago();
        f.setDescripcion(descripcion);
        return f;
    }

    private static CobroDetalleInput linea(long formaPagoId, boolean descuento) {
        CobroDetalleInput cd = new CobroDetalleInput();
        cd.setFormaPagoId(formaPagoId);
        cd.setDescuento(descuento);
        cd.setPago(!descuento);
        return cd;
    }

    private static String mensaje(Executable e) {
        return assertThrows(GraphQLException.class, e).getMessage();
    }

    // --- la regla ---

    @Test
    void soloConvenioPasa() {
        assertDoesNotThrow(() -> VentaGraphQL.exigirConvenioSolo(Arrays.asList("CONVENIO"), false));
    }

    @Test
    void convenioConOtraFormaEnCualquierOrdenSeRechaza() {
        assertEquals(MEZCLA, mensaje(() -> VentaGraphQL.exigirConvenioSolo(Arrays.asList("CONVENIO", "EFECTIVO"), false)));
        assertEquals(MEZCLA, mensaje(() -> VentaGraphQL.exigirConvenioSolo(Arrays.asList("TARJETA", "CONVENIO"), false)));
    }

    @Test
    void formaDePagoDesconocidaSeRechaza() {
        assertEquals(MEZCLA, mensaje(() -> VentaGraphQL.exigirConvenioSolo(Arrays.asList("CONVENIO", null), false)));
    }

    @Test
    void convenioConDescuentoSeRechaza() {
        assertEquals(DESCUENTO, mensaje(() -> VentaGraphQL.exigirConvenioSolo(Arrays.asList("CONVENIO"), true)));
    }

    // --- saveVenta ---

    private VentaInput ventaInput() {
        VentaInput v = new VentaInput();
        v.setCajaId(1185L);
        v.setUsuarioId(7L);
        return v;
    }

    private Venta saveVenta(List<CobroDetalleInput> lineas, VentaCreditoInput credito, List cuotas) throws Exception {
        return ventaGraphQL.saveVenta(ventaInput(), new ArrayList<>(), null, lineas, false, false, null, null, null,
                credito, cuotas, null, null);
    }

    @Test
    void saveVentaConvenioMasEfectivoSeRechazaSinEscribir() {
        List<CobroDetalleInput> lineas = Arrays.asList(linea(1L, false), linea(3L, false));
        assertEquals(MEZCLA, mensaje(() -> saveVenta(lineas, new VentaCreditoInput(), new ArrayList<>())));
        verifyNoInteractions(ventaDuplicadaCacheService, cobroGraphQL);
    }

    @Test
    void saveVentaConvenioConDescuentoSeRechazaSinEscribir() {
        List<CobroDetalleInput> lineas = Arrays.asList(linea(3L, false), linea(1L, true));
        assertEquals(DESCUENTO, mensaje(() -> saveVenta(lineas, new VentaCreditoInput(), new ArrayList<>())));
        verifyNoInteractions(ventaDuplicadaCacheService, cobroGraphQL);
    }

    @Test
    void saveVentaSinCreditoNoValida() throws Exception {
        // Sin cuotas el filial no guarda credito: tampoco valida (misma condicion).
        when(ventaDuplicadaCacheService.reservar(any(), any())).thenThrow(new IllegalStateException("siguio"));
        List<CobroDetalleInput> lineas = Arrays.asList(linea(1L, false), linea(1L, true));
        assertThrows(IllegalStateException.class, () -> saveVenta(lineas, null, null));
        assertThrows(IllegalStateException.class, () -> saveVenta(lineas, new VentaCreditoInput(), null));
    }

    // --- saveVentaDelivery ---

    private Venta ventaDelivery(List<CobroDetalle> guardadas) {
        Delivery d = new Delivery();
        d.setId(10L);
        when(deliveryService.findById(10L)).thenReturn(Optional.of(d));
        Venta v = new Venta();
        v.setId(5L);
        v.setCaja(new PdvCaja());
        v.setDelivery(d);
        Cobro c = new Cobro();
        c.setId(30L);
        v.setCobro(c);
        when(service.findById(5L)).thenReturn(Optional.of(v));
        when(cobroDetalleService.findByCobroId(30L)).thenReturn(guardadas);
        return v;
    }

    private Venta saveVentaDelivery(List<CobroDetalleInput> lineas, VentaCreditoInput credito) {
        VentaInput vi = new VentaInput();
        vi.setId(5L);
        DeliveryInput di = new DeliveryInput();
        di.setId(10L);
        return ventaGraphQL.saveVentaDelivery(vi, di, lineas, credito, new ArrayList<>());
    }

    private static CobroDetalle guardada(String formaPago, boolean descuento) {
        CobroDetalle cd = new CobroDetalle();
        cd.setFormaPago(formaPago(formaPago));
        cd.setDescuento(descuento);
        return cd;
    }

    @Test
    void deliveryConEfectivoYaGuardadoSeRechazaSinGuardarLineas() {
        // La linea ya guardada vuelve en la lista (replay) y tambien esta en la base: contarla dos veces no cambia nada.
        ventaDelivery(Collections.singletonList(guardada("EFECTIVO", false)));
        assertEquals(MEZCLA, mensaje(() -> saveVentaDelivery(Arrays.asList(linea(1L, false), linea(3L, false)), new VentaCreditoInput())));
        verifyNoInteractions(cobroDetalleGraphQL);
    }

    @Test
    void deliveryConDescuentoGuardadoSeRechazaAunqueNoVuelvaEnLaLista() {
        ventaDelivery(Collections.singletonList(guardada("EFECTIVO", true)));
        assertEquals(DESCUENTO, mensaje(() -> saveVentaDelivery(Collections.singletonList(linea(3L, false)), new VentaCreditoInput())));
        verifyNoInteractions(cobroDetalleGraphQL);
    }

    @Test
    void deliverySinConvenioNoValida() {
        ventaDelivery(Collections.singletonList(guardada("EFECTIVO", true)));
        // Sin credito sigue de largo hasta guardar las lineas (aca falla mas adelante por los mocks, no por la regla).
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> saveVentaDelivery(Collections.singletonList(linea(1L, false)), null));
        assertNotEquals(MEZCLA, e.getMessage());
        assertNotEquals(DESCUENTO, e.getMessage());
    }
}
