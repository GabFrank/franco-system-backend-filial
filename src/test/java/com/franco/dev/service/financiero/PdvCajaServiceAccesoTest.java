package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.PdvCajaRepository;
import com.franco.dev.service.seguridad.RolSesionService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Control del detalle de una caja: dueño o ANALISIS DE CAJA, segun de donde se llegue a la caja. */
class PdvCajaServiceAccesoTest {

    private static final String ROL = RolSesionService.ANALISIS_DE_CAJA;
    private static final long CAJA = 1189L;
    private static final long DUENIO = 5L;

    @Mock private PdvCajaRepository repository;
    @Mock private RolSesionService rolSesionService;
    @InjectMocks private PdvCajaService service;

    private PdvCaja caja;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        caja = new PdvCaja();
        caja.setId(CAJA);
        Usuario duenio = new Usuario();
        duenio.setId(DUENIO);
        caja.setUsuario(duenio);
        when(repository.findById(CAJA)).thenReturn(Optional.of(caja));
    }

    @Test
    void unaCajaExistentePideDuenioORol() {
        doThrow(new GraphQLException("No autorizado")).when(rolSesionService).exigirDuenioORol(DUENIO, ROL);
        assertThrows(GraphQLException.class, () -> service.exigirAccesoACaja(CAJA));
    }

    @Test
    void unaCajaQueNoExisteSigueComoAntes() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        service.exigirAccesoACaja(99L);
        service.exigirAccesoACaja(null);
        verifyNoInteractions(rolSesionService);
    }

    @Test
    void unRegistroSinCajaPideElRol() {
        service.exigirAccesoARegistroDeCaja(null);
        verify(rolSesionService).exigirRol(ROL);
        verify(rolSesionService, never()).exigirDuenioORol(any(), any());
    }

    @Test
    void unRegistroConCajaPideElControlDeSuCaja() {
        PdvCaja referencia = new PdvCaja();
        referencia.setId(CAJA);
        service.exigirAccesoARegistroDeCaja(referencia);
        verify(rolSesionService).exigirDuenioORol(DUENIO, ROL);
    }

    @Test
    void unConteoPideElControlDeLaCajaQueLoTiene() {
        when(repository.findFirstByConteoAperturaIdOrConteoCierreId(40L, 40L)).thenReturn(Optional.of(caja));
        service.exigirAccesoAConteo(40L);
        verify(rolSesionService).exigirDuenioORol(DUENIO, ROL);
    }

    @Test
    void unConteoSinCajaPideSoloElRol() {
        when(repository.findFirstByConteoAperturaIdOrConteoCierreId(41L, 41L)).thenReturn(Optional.empty());
        service.exigirAccesoAConteo(41L);
        // Dueño null: RolSesionService solo deja pasar al rol (o ADMIN).
        verify(rolSesionService).exigirDuenioORol(null, ROL);
    }
}
