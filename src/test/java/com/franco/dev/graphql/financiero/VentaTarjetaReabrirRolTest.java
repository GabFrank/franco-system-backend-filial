package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.VentaTarjeta;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.financiero.VentaTarjetaService;
import com.franco.dev.service.personas.UsuarioService;
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

/** Reabrir un cobro exige el rol segun el estado de su caja, igual que el desktop (#147). */
class VentaTarjetaReabrirRolTest {

    private static final long ID = 77L;
    private static final long SUC_CLIENTE = 99L;
    private static final long SUC = 24L;
    private static final long CAJA = 1189L;
    private static final String COMPLETAR = VentaTarjetaGraphQL.VENTA_TARJETA_COMPLETAR;
    private static final String ANALISIS = RolSesionService.ANALISIS_DE_CAJA;

    @Mock private VentaTarjetaService service;
    @Mock private SucursalService sucursalService;
    @Mock private PdvCajaService pdvCajaService;
    @Mock private RolSesionService rolSesionService;
    @Mock private UsuarioService usuarioService;
    @InjectMocks private VentaTarjetaGraphQL graphQL;

    private VentaTarjeta vt;
    private PdvCaja caja;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        when(sucursalService.exigirSucursalPropia(SUC_CLIENTE)).thenReturn(SUC);
        vt = new VentaTarjeta();
        vt.setId(ID);
        vt.setCajaId(CAJA);
        when(service.findByIdAndSucursalId(ID, SUC)).thenReturn(vt);
        caja = new PdvCaja();
        caja.setId(CAJA);
        when(pdvCajaService.findById(CAJA)).thenReturn(Optional.of(caja));
    }

    private void rechazar(String rol) {
        doThrow(new GraphQLException("No autorizado")).when(rolSesionService).exigirRol(rol);
    }

    private void assertRechazado() {
        assertThrows(GraphQLException.class, () -> graphQL.reabrirVentaTarjeta(ID, SUC_CLIENTE, null));
        verify(service, never()).reabrir(anyLong(), anyLong(), any());
    }

    @Test
    void cajaAbiertaPideCompletarYSinElRolNoReabre() {
        caja.setActivo(true);
        rechazar(COMPLETAR);
        assertRechazado();
        verify(rolSesionService).exigirRol(COMPLETAR);
    }

    @Test
    void cajaCerradaPideAnalisisYSinElRolNoReabre() {
        caja.setActivo(false);
        rechazar(ANALISIS);
        assertRechazado();
        verify(rolSesionService).exigirRol(ANALISIS);
    }

    @Test
    void cajaConActivoNullCuentaComoCerrada() {
        rechazar(ANALISIS);
        assertRechazado();
    }

    @Test
    void cajaInexistentePideAnalisis() {
        when(pdvCajaService.findById(CAJA)).thenReturn(Optional.empty());
        rechazar(ANALISIS);
        assertRechazado();
    }

    @Test
    void cobroInexistentePideAnalisisAntesDelErrorDeReabrir() {
        when(service.findByIdAndSucursalId(ID, SUC)).thenReturn(null);
        rechazar(ANALISIS);
        assertRechazado();
        verify(pdvCajaService, never()).findById(anyLong());
    }

    @Test
    void cajaAbiertaConElRolDelegaEnReabrirConLaSucursalPropia() {
        caja.setActivo(true);
        VentaTarjeta reabierta = new VentaTarjeta();
        when(service.reabrir(eq(ID), eq(SUC), any())).thenReturn(reabierta);
        assertSame(reabierta, graphQL.reabrirVentaTarjeta(ID, SUC_CLIENTE, null));
        verify(rolSesionService).exigirRol(COMPLETAR);
        verify(rolSesionService, never()).exigirRol(ANALISIS);
    }

    @Test
    void cajaCerradaConElRolDelegaEnReabrir() {
        caja.setActivo(false);
        VentaTarjeta reabierta = new VentaTarjeta();
        when(service.reabrir(eq(ID), eq(SUC), any())).thenReturn(reabierta);
        assertSame(reabierta, graphQL.reabrirVentaTarjeta(ID, SUC_CLIENTE, null));
        verify(rolSesionService).exigirRol(ANALISIS);
        verify(rolSesionService, never()).exigirRol(COMPLETAR);
    }

    @Test
    void cajaQueNoSePuedeLeerFallaSinReabrir() {
        when(pdvCajaService.findById(CAJA)).thenThrow(new RuntimeException("More than one row"));
        assertThrows(RuntimeException.class, () -> graphQL.reabrirVentaTarjeta(ID, SUC_CLIENTE, null));
        verify(service, never()).reabrir(anyLong(), anyLong(), any());
    }
}
