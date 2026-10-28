package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.*;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.enums.DeliveryEstado;
import com.franco.dev.graphql.operaciones.DeliveryGraphQL;
import com.franco.dev.graphql.operaciones.VentaGraphQL;
import com.franco.dev.service.financiero.*;
import com.franco.dev.service.operaciones.DeliveryService;
import com.franco.dev.service.operaciones.VentaService;
import com.franco.dev.service.seguridad.RolSesionService;
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

/** Detalle de una caja (retiros, gastos, ventas, tarjeta, delivery, conteos, movimientos): dueño o ANALISIS DE CAJA. */
class CajaDetalleAccesoTest {

    private static final String ROL = RolSesionService.ANALISIS_DE_CAJA;
    private static final long AJENA = 1189L;
    private static final long PROPIA = 1190L;

    @Mock private PdvCajaService pdvCajaService;
    @Mock private RolSesionService rolSesionService;
    @Mock private RetiroService retiroService;
    @Mock private GastoService gastoService;
    @Mock private VentaTarjetaService ventaTarjetaService;
    @Mock private DeliveryService deliveryService;
    @Mock private VentaService ventaService;
    @Mock private ConteoService conteoService;
    @Mock private ConteoMonedaService conteoMonedaService;
    @Mock private MovimientoCajaService movimientoCajaService;

    @InjectMocks private RetiroGraphQL retiroGraphQL;
    @InjectMocks private GastoGraphQL gastoGraphQL;
    @InjectMocks private VentaTarjetaGraphQL ventaTarjetaGraphQL;
    @InjectMocks private DeliveryGraphQL deliveryGraphQL;
    @InjectMocks private VentaGraphQL ventaGraphQL;
    @InjectMocks private ConteoGraphQL conteoGraphQL;
    @InjectMocks private ConteoMonedaGraphQL conteoMonedaGraphQL;
    @InjectMocks private MovimientoCajaGraphQL movimientoCajaGraphQL;

    private PdvCaja ajena;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        GraphQLException no = new GraphQLException("No autorizado");
        doThrow(no).when(pdvCajaService).exigirAccesoACaja(AJENA);
        doThrow(no).when(rolSesionService).exigirRol(ROL);
        ajena = new PdvCaja();
        ajena.setId(AJENA);
        doThrow(no).when(pdvCajaService).exigirAccesoARegistroDeCaja(ajena);
        doThrow(no).when(pdvCajaService).exigirAccesoAConteo(40L);
    }

    @Test
    void lasListasDeUnaCajaAjenaSeRechazanSinConsultar() {
        assertThrows(GraphQLException.class, () -> retiroGraphQL.retiroListPorCajaSalidaId(AJENA, 24L));
        assertThrows(GraphQLException.class, () -> gastoGraphQL.gastosPorCajaId(AJENA, 24L));
        assertThrows(GraphQLException.class, () -> ventaTarjetaGraphQL.ventasTarjetaPorCaja(AJENA, 24L));
        assertThrows(GraphQLException.class, () -> deliveryGraphQL.deliveryPorCajaIdAndEstados(AJENA,
                Collections.singletonList(DeliveryEstado.ABIERTO), 24L));
        assertThrows(GraphQLException.class, () -> ventaGraphQL.ventasPorCajaId(null, AJENA, 0, 10, false, 24L,
                null, null, null, null, null, null));
        verify(retiroService, never()).findByCajaSalidaId(anyLong());
        verify(gastoService, never()).findByCajaId(anyLong());
        verify(ventaTarjetaService, never()).findByCajaIdAndSucursalId(anyLong(), anyLong());
        verify(deliveryService, never()).getRepository();
        verify(ventaService, never()).findByCajaId(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void lasListasDeLaCajaPropiaPasan() {
        retiroGraphQL.retiroListPorCajaSalidaId(PROPIA, 24L);
        gastoGraphQL.gastosPorCajaId(PROPIA, 24L);
        ventaTarjetaGraphQL.ventasTarjetaPorCaja(PROPIA, 24L);
        verify(retiroService).findByCajaSalidaId(PROPIA);
        verify(gastoService).findByCajaId(PROPIA);
        verify(ventaTarjetaService).findByCajaIdAndSucursalId(PROPIA, 24L);
    }

    @Test
    void unaVentaPorIdVaPorSuCajaYNoPorLaQueSeManda() {
        Venta venta = new Venta();
        venta.setCaja(ajena);
        when(ventaService.findById(7L)).thenReturn(Optional.of(venta));
        assertThrows(GraphQLException.class, () -> ventaGraphQL.ventasPorCajaId(7L, PROPIA, 0, 10, false, 24L,
                null, null, null, null, null, null));
    }

    @Test
    void unaVentaSinCajaPideElRol() {
        when(ventaService.findById(8L)).thenReturn(Optional.of(new Venta()));
        ventaGraphQL.ventasPorCajaId(8L, PROPIA, 0, 10, false, 24L, null, null, null, null, null, null);
        verify(pdvCajaService).exigirAccesoARegistroDeCaja(null);
    }

    @Test
    void losRegistrosSueltosDeUnaCajaAjenaSeRechazan() {
        Retiro retiro = new Retiro();
        retiro.setCajaSalida(ajena);
        when(retiroService.findById(1L)).thenReturn(Optional.of(retiro));
        Gasto gasto = new Gasto();
        gasto.setCaja(ajena);
        when(gastoService.findById(2L)).thenReturn(Optional.of(gasto));
        MovimientoCaja mov = new MovimientoCaja();
        mov.setPdvCaja(ajena);
        when(movimientoCajaService.findById(3L)).thenReturn(Optional.of(mov));
        Conteo conteo = new Conteo();
        conteo.setId(40L);
        ConteoMoneda cm = new ConteoMoneda();
        cm.setConteo(conteo);
        when(conteoMonedaService.findById(4L)).thenReturn(Optional.of(cm));

        assertThrows(GraphQLException.class, () -> retiroGraphQL.retiro(1L, 24L));
        assertThrows(GraphQLException.class, () -> gastoGraphQL.gasto(2L, 24L));
        assertThrows(GraphQLException.class, () -> movimientoCajaGraphQL.movimientoCaja(3L, 24L));
        assertThrows(GraphQLException.class, () -> conteoMonedaGraphQL.conteoMoneda(4L, 24L));
        assertThrows(GraphQLException.class, () -> conteoGraphQL.conteo(40L, 24L));
        assertThrows(GraphQLException.class, () -> conteoMonedaGraphQL.conteoMonedasPorConteoId(40L, 24L));
        verify(conteoService, never()).findById(anyLong());
        verify(conteoMonedaService, never()).findByConteoId(anyLong());
    }

    @Test
    void lasListasGeneralesExigenElRol() {
        assertThrows(GraphQLException.class, () -> retiroGraphQL.retiros(0, 10, 24L));
        assertThrows(GraphQLException.class, () -> gastoGraphQL.gastos(0, 10, 24L));
        assertThrows(GraphQLException.class, () -> gastoGraphQL.gastosPorFecha("2026-10-01", "2026-10-28", 24L));
        assertThrows(GraphQLException.class, () -> conteoGraphQL.conteos(0, 10, 24L));
        assertThrows(GraphQLException.class, () -> conteoMonedaGraphQL.conteoMonedas(0, 10, 24L));
        assertThrows(GraphQLException.class, () -> movimientoCajaGraphQL.movimientoCajas(0, 10, 24L));
        verify(retiroService, never()).findAll(any());
        verify(gastoService, never()).findAll(any());
        verify(gastoService, never()).findByDate(any(), any());
        verify(conteoService, never()).findAll(any());
        verify(conteoMonedaService, never()).findAll(any());
        verify(movimientoCajaService, never()).findAll(any());
    }
}
