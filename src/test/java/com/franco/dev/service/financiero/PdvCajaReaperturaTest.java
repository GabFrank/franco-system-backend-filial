package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Conteo;
import com.franco.dev.domain.financiero.Maletin;
import com.franco.dev.domain.financiero.MovimientoCaja;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.PdvCajaReapertura;
import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.repository.financiero.PdvCajaReaperturaRepository;
import com.franco.dev.repository.financiero.PdvCajaRepository;
import com.franco.dev.service.seguridad.RolSesionService;
import com.franco.dev.service.seguridad.UsuarioSesionService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.env.Environment;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Reabrir una caja cerrada por equivocacion (issue desktop #662). */
class PdvCajaReaperturaTest {

    private static final long CAJA = 500L;
    private static final long MALETIN = 7L;
    private static final long CAJERO = 5L;
    private static final long CONTEO_CIERRE = 900L;
    private static final long ADMIN_ID = 1L;

    @Mock private PdvCajaRepository repository;
    @Mock private PdvCajaReaperturaRepository reaperturaRepository;
    @Mock private RolSesionService rolSesionService;
    @Mock private UsuarioSesionService usuarioSesionService;
    @Mock private MovimientoCajaService movimientoCajaService;
    @Mock private MaletinService maletinService;
    @Mock private Environment env;
    @InjectMocks private PdvCajaService service;

    private PdvCaja caja;
    private Maletin maletin;
    private LocalDateTime ahora;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        service.env = env;
        ahora = LocalDateTime.now();
        maletin = new Maletin();
        maletin.setId(MALETIN);
        maletin.setDescripcion("M7");
        maletin.setAbierto(false);
        Usuario cajero = new Usuario();
        cajero.setId(CAJERO);
        Conteo cierre = new Conteo();
        cierre.setId(CONTEO_CIERRE);

        caja = new PdvCaja();
        caja.setId(CAJA);
        caja.setSucursalId(2L);
        caja.setUsuario(cajero);
        caja.setMaletin(maletin);
        caja.setActivo(false);
        caja.setVerificado(false);
        caja.setConteoCierre(cierre);
        caja.setFechaCierre(ahora.minusMinutes(10));

        when(repository.findByIdForUpdate(CAJA)).thenReturn(Optional.of(caja));
        when(repository.findByUsuarioIdAndActivo(CAJERO, true)).thenReturn(Collections.emptyList());
        when(repository.save(any(PdvCaja.class))).thenAnswer(i -> i.getArgument(0));
        when(maletinService.findById(MALETIN)).thenReturn(Optional.of(maletin));
        when(usuarioSesionService.idDelUsuarioAutenticado()).thenReturn(Optional.of(ADMIN_ID));
    }

    @Test
    void reabreLaCajaYDejaElRegistro() {
        MovimientoCaja final1 = new MovimientoCaja();
        final1.setActivo(true);
        when(movimientoCajaService.findByTipoMovimientoAndReferencia(PdvCajaTipoMovimiento.CAJA_FINAL, CONTEO_CIERRE))
                .thenReturn(Arrays.asList(final1));

        PdvCaja res = service.reabrirCaja(CAJA, "  cerro sin querer ");

        assertTrue(res.getActivo());
        assertNull(res.getConteoCierre());
        assertNull(res.getFechaCierre());
        assertTrue(maletin.getAbierto());
        assertFalse(final1.getActivo());
        ArgumentCaptor<PdvCajaReapertura> reg = ArgumentCaptor.forClass(PdvCajaReapertura.class);
        verify(reaperturaRepository).save(reg.capture());
        assertEquals(CAJA, reg.getValue().getPdvCajaId());
        assertEquals(CONTEO_CIERRE, reg.getValue().getConteoCierreAnteriorId());
        assertEquals("CERRO SIN QUERER", reg.getValue().getMotivo());
        assertEquals(ADMIN_ID, reg.getValue().getUsuarioId());
        assertEquals(2L, reg.getValue().getSucursalId());
    }

    @Test
    void sinAdminNoTocaNada() {
        doThrow(new GraphQLException("No autorizado")).when(rolSesionService).exigirAdmin();
        assertThrows(GraphQLException.class, () -> service.reabrirCaja(CAJA, "x"));
        verifyNoInteractions(reaperturaRepository);
        verify(repository, never()).save(any());
    }

    @Test
    void sinMotivoSeRechaza() {
        assertMensaje("motivo", () -> service.reabrirCaja(CAJA, "   "));
        assertMensaje("motivo", () -> service.reabrirCaja(CAJA, null));
    }

    @Test
    void cajaAbiertaSeRechaza() {
        caja.setActivo(true);
        caja.setConteoCierre(null);
        assertEquals("La caja ya esta abierta", service.motivoNoSePuedeReabrir(caja, ahora));
    }

    @Test
    void cajaVerificadaSeRechaza() {
        caja.setVerificado(true);
        assertTrue(service.motivoNoSePuedeReabrir(caja, ahora).contains("verificada"));
    }

    @Test
    void cerradaOtroDiaSeRechaza() {
        caja.setFechaCierre(ahora.minusDays(1));
        assertTrue(service.motivoNoSePuedeReabrir(caja, ahora).contains("mismo dia"));
    }

    @Test
    void maletinUsadoPorUnaCajaPosteriorSeRechaza() {
        when(repository.existsByMaletinIdAndIdGreaterThan(MALETIN, CAJA)).thenReturn(true);
        assertTrue(service.motivoNoSePuedeReabrir(caja, ahora).contains("otra caja con el maletin M7"));
    }

    @Test
    void maletinEnUsoSeRechaza() {
        when(repository.existsByMaletinIdAndActivoTrueAndIdNot(MALETIN, CAJA)).thenReturn(true);
        assertTrue(service.motivoNoSePuedeReabrir(caja, ahora).contains("en uso"));
    }

    @Test
    void cajeroConOtraCajaAbiertaSeRechaza() {
        PdvCaja otra = new PdvCaja();
        otra.setId(CAJA + 1);
        when(repository.findByUsuarioIdAndActivo(CAJERO, true)).thenReturn(Arrays.asList(otra));
        assertEquals("El cajero ya tiene otra caja abierta", service.motivoNoSePuedeReabrir(caja, ahora));
    }

    @Test
    void unRechazoNoDejaRegistro() {
        caja.setVerificado(true);
        assertThrows(GraphQLException.class, () -> service.reabrirCaja(CAJA, "x"));
        verifyNoInteractions(reaperturaRepository);
        verifyNoInteractions(movimientoCajaService);
    }

    private static void assertMensaje(String parte, org.junit.jupiter.api.function.Executable e) {
        GraphQLException ex = assertThrows(GraphQLException.class, e);
        assertTrue(ex.getMessage().contains(parte), ex.getMessage());
    }
}
