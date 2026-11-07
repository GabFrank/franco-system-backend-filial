package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.seguridad.RolSesionService;
import com.franco.dev.service.seguridad.UsuarioSesionService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.env.Environment;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Alta de gasto o retiro: dueño de la caja o ANALISIS DE CAJA; rechaza solo con el interruptor prendido. */
class PdvCajaAccesoEnAltaTest {

    private static final long DUENIO = 410L;

    @Mock private RolSesionService rolSesionService;
    @Mock private UsuarioSesionService usuarioSesionService;
    @Mock private Environment env;
    @InjectMocks private PdvCajaService service;

    private final PdvCaja caja = new PdvCaja();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        service.env = env; // campo de CrudService: con constructor Lombok, Mockito no lo completa
        Usuario duenio = new Usuario();
        duenio.setId(DUENIO);
        caja.setId(1185L);
        caja.setUsuario(duenio);
        when(usuarioSesionService.idDelUsuarioAutenticado()).thenReturn(Optional.of(601L));
        when(env.getProperty("sucursalId")).thenReturn("24");
    }

    private void interruptor(String valor) {
        when(env.getProperty(eq(PdvCajaService.PROPERTY_EXIGIR_ACCESO_ALTA), anyString())).thenReturn(valor);
    }

    @Test
    void elDuenioOElRolPasanConElInterruptorPrendido() {
        interruptor("true");
        when(rolSesionService.esDuenioOTieneRol(DUENIO, RolSesionService.ANALISIS_DE_CAJA)).thenReturn(true);

        assertDoesNotThrow(() -> service.controlarAccesoEnAlta("op", caja));
    }

    @Test
    void sinAccesoYApagadoSoloRegistra() {
        interruptor("false");
        when(rolSesionService.esDuenioOTieneRol(DUENIO, RolSesionService.ANALISIS_DE_CAJA)).thenReturn(false);

        assertDoesNotThrow(() -> service.controlarAccesoEnAlta("op", caja));
        verify(usuarioSesionService).idDelUsuarioAutenticado(); // armo la linea del log
    }

    @Test
    void sinAccesoYPrendidoRechaza() {
        interruptor("true");
        when(rolSesionService.esDuenioOTieneRol(DUENIO, RolSesionService.ANALISIS_DE_CAJA)).thenReturn(false);

        GraphQLException e = assertThrows(GraphQLException.class, () -> service.controlarAccesoEnAlta("op", caja));
        assertEquals("No autorizado: solo el dueño o el rol ANALISIS DE CAJA.", e.getMessage());
    }

    @Test
    void sinSesionYPrendidoRechaza() {
        interruptor("true");
        when(usuarioSesionService.idDelUsuarioAutenticado()).thenReturn(Optional.empty());
        when(rolSesionService.esDuenioOTieneRol(anyLong(), any())).thenReturn(false);

        assertThrows(GraphQLException.class, () -> service.controlarAccesoEnAlta("op", caja));
    }

    @Test
    void sinPropertyQuedaApagado() {
        when(env.getProperty(eq(PdvCajaService.PROPERTY_EXIGIR_ACCESO_ALTA), anyString()))
                .thenAnswer(inv -> inv.getArgument(1));
        when(rolSesionService.esDuenioOTieneRol(DUENIO, RolSesionService.ANALISIS_DE_CAJA)).thenReturn(false);

        assertDoesNotThrow(() -> service.controlarAccesoEnAlta("op", caja));
    }
}
