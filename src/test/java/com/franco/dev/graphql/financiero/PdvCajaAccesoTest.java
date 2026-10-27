package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.CajaBalance;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.input.PdvCajaInput;
import com.franco.dev.graphql.financiero.resolver.CajaResolver;
import com.franco.dev.graphql.financiero.resolver.PdvCajaResolver;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.seguridad.AuditorUsuarioId;
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

/** Balance, impresion, transferencia y edicion de una caja: solo el dueño o ANALISIS DE CAJA. */
class PdvCajaAccesoTest {

    private static final String ROL = RolSesionService.ANALISIS_DE_CAJA;
    private static final long CAJA = 1189L;
    private static final long DUENIO = 5L;

    @Mock private PdvCajaService service;
    @Mock private RolSesionService rolSesionService;
    @Mock private UsuarioService usuarioService;
    @Mock private AuditorUsuarioId auditorUsuarioId;
    @InjectMocks private PdvCajaGraphQL graphQL;
    @InjectMocks private PdvCajaResolver pdvCajaResolver;
    @InjectMocks private CajaResolver cajaResolver;

    private PdvCaja caja;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        caja = new PdvCaja();
        caja.setId(CAJA);
        Usuario duenio = new Usuario();
        duenio.setId(DUENIO);
        caja.setUsuario(duenio);
        when(service.findById(CAJA)).thenReturn(Optional.of(caja));
    }

    private void rechazarAjeno() {
        doThrow(new GraphQLException("No autorizado")).when(rolSesionService).exigirDuenioORol(DUENIO, ROL);
    }

    @Test
    void balancePorCajaIdAjenoSeRechazaSinCalcular() {
        rechazarAjeno();
        assertThrows(GraphQLException.class, () -> graphQL.balancePorCajaId(CAJA));
        verify(service, never()).getBalance(anyLong());
    }

    @Test
    void balancePorCajaIdDelDuenioDevuelveElBalance() {
        CajaBalance balance = new CajaBalance();
        when(service.getBalance(CAJA)).thenReturn(balance);
        assertSame(balance, graphQL.balancePorCajaId(CAJA));
        verify(rolSesionService).exigirDuenioORol(DUENIO, ROL);
    }

    @Test
    void imprimirBalanceAjenoSeRechazaSinImprimir() {
        rechazarAjeno();
        assertThrows(GraphQLException.class, () -> graphQL.imprimirBalance(CAJA, "frc-ticket", "CAJA 1", 24L));
        verify(service, never()).imprimirBalance(anyLong(), any(), any());
        verify(service, never()).imprimirBalanceVerificando(anyLong(), any(), any());
    }

    @Test
    void balancePorFechaExigeElRol() {
        doThrow(new GraphQLException("No autorizado")).when(rolSesionService).exigirRol(ROL);
        assertThrows(GraphQLException.class, () -> graphQL.balancePorFecha("2026-10-01", "2026-10-27", 24L));
        verify(service, never()).findByDate(any(), any());
    }

    @Test
    void transferirUnaCajaAjenaSeRechaza() {
        rechazarAjeno();
        assertThrows(GraphQLException.class, () -> graphQL.transferirCaja(CAJA, 9L));
        verify(service, never()).transferirCaja(anyLong(), anyLong());
    }

    @Test
    void elDestinoConSesionIniciadaRecibeLaCaja() {
        // El desktop inicia sesion con el usuario destino antes de llamar transferirCaja.
        rechazarAjeno();
        when(rolSesionService.esDuenioOTieneRol(9L)).thenReturn(true);
        graphQL.transferirCaja(CAJA, 9L);
        verify(service).transferirCaja(CAJA, 9L);
    }

    @Test
    void elDuenioTransfiereSuCaja() {
        graphQL.transferirCaja(CAJA, 9L);
        verify(service).transferirCaja(CAJA, 9L);
    }

    @Test
    void editarUnaCajaAjenaSeRechazaSinGuardar() {
        rechazarAjeno();
        assertThrows(GraphQLException.class, () -> graphQL.savePdvCaja(input(CAJA, DUENIO)));
        verify(service, never()).saveAndSend(any(), any());
    }

    @Test
    void pasarUnaCajaAOtroUsuarioExigeElRol() {
        doThrow(new GraphQLException("No autorizado")).when(rolSesionService).exigirRol(ROL);
        assertThrows(GraphQLException.class, () -> graphQL.savePdvCaja(input(CAJA, 9L)));
        verify(service, never()).saveAndSend(any(), any());
    }

    @Test
    void elDuenioGuardaSuCajaSinPedirRol() {
        graphQL.savePdvCaja(input(CAJA, DUENIO));
        verify(rolSesionService, never()).exigirRol(any());
        verify(service).saveAndSend(any(), eq(false));
    }

    @Test
    void unaCajaNuevaNoPasaPorElControl() {
        graphQL.savePdvCaja(input(null, 9L));
        verifyZeroInteractions(rolSesionService);
        verify(service).saveAndSend(any(), eq(false));
    }

    @Test
    void elCampoBalanceDeUnaCajaAjenaEsNullSinCalcular() {
        when(rolSesionService.esDuenioOTieneRol(DUENIO, ROL)).thenReturn(false);
        assertNull(pdvCajaResolver.balance(caja));
        assertNull(cajaResolver.balance(caja));
        verify(service, never()).getBalance(anyLong());
    }

    @Test
    void elCampoBalanceDelDuenioDevuelveElBalance() {
        CajaBalance balance = new CajaBalance();
        when(rolSesionService.esDuenioOTieneRol(DUENIO, ROL)).thenReturn(true);
        when(service.getBalance(CAJA)).thenReturn(balance);
        assertSame(balance, pdvCajaResolver.balance(caja));
        assertSame(balance, cajaResolver.balance(caja));
    }

    private static PdvCajaInput input(Long id, Long usuarioId) {
        PdvCajaInput in = new PdvCajaInput();
        in.setId(id);
        in.setUsuarioId(usuarioId);
        in.setSucursalId(24L);
        return in;
    }
}
