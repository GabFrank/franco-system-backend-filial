package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.VentaTarjeta;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.input.VentaTarjetaInput;
import com.franco.dev.service.empresarial.SucursalService;
import com.franco.dev.service.financiero.VentaTarjetaService;
import com.franco.dev.service.personas.UsuarioService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** saveVentaTarjeta solo da altas PENDIENTE de la sucursal propia, como las arma el PDV (#147). */
class VentaTarjetaSaveSoloAltaTest {

    private static final long SUC = 24L;
    private static final long USUARIO = 410L;

    @Mock private VentaTarjetaService service;
    @Mock private SucursalService sucursalService;
    @Mock private UsuarioService usuarioService;
    @InjectMocks private VentaTarjetaGraphQL graphQL;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        when(sucursalService.exigirSucursalPropia(SUC)).thenReturn(SUC);
        when(sucursalService.exigirSucursalPropia(99L)).thenThrow(new IllegalArgumentException("sucursal ajena"));
        usuario = new Usuario();
        usuario.setId(USUARIO);
        when(usuarioService.findById(USUARIO)).thenReturn(Optional.of(usuario));
        when(service.guardarConAviso(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /** El input que manda venta-touch.component.ts#registrarPagosConTarjeta. */
    private VentaTarjetaInput delPdv(String estado) {
        VentaTarjetaInput in = new VentaTarjetaInput();
        in.setSucursalId(SUC);
        in.setVentaId(79285L);
        in.setCajaId(1185L);
        in.setMonto(new BigDecimal("3000"));
        in.setEstado(estado);
        in.setUsuarioId(USUARIO);
        return in;
    }

    private void assertRechazaSinGuardar(VentaTarjetaInput in, Class<? extends Throwable> tipo) {
        assertThrows(tipo, () -> graphQL.saveVentaTarjeta(in));
        verify(service, never()).guardarConAviso(any());
    }

    @Test
    void conIdRechazaSinGuardar() {
        VentaTarjetaInput in = delPdv("PENDIENTE");
        in.setId(1080L);
        assertRechazaSinGuardar(in, GraphQLException.class);
    }

    @Test
    void altaCompletadaRechazaSinGuardar() {
        assertRechazaSinGuardar(delPdv("COMPLETADO"), GraphQLException.class);
    }

    @Test
    void altaNoCompletadaRechazaSinGuardar() {
        assertRechazaSinGuardar(delPdv("NO_COMPLETADO"), GraphQLException.class);
    }

    @Test
    void sucursalAjenaRechazaSinGuardar() {
        VentaTarjetaInput in = delPdv("PENDIENTE");
        in.setSucursalId(99L);
        assertRechazaSinGuardar(in, IllegalArgumentException.class);
    }

    @Test
    void elAltaDelPdvGuardaPendienteConSuSucursalYUsuario() {
        graphQL.saveVentaTarjeta(delPdv("PENDIENTE"));
        ArgumentCaptor<VentaTarjeta> guardada = ArgumentCaptor.forClass(VentaTarjeta.class);
        verify(service).guardarConAviso(guardada.capture());
        assertNull(guardada.getValue().getId());
        assertEquals("PENDIENTE", guardada.getValue().getEstado());
        assertEquals(SUC, guardada.getValue().getSucursalId());
        assertEquals(1185L, guardada.getValue().getCajaId());
        assertSame(usuario, guardada.getValue().getUsuario());
    }

    @Test
    void estadoNuloVacioOEnMinusculaGuardaPendiente() {
        for (String estado : new String[]{null, "", "  ", " pendiente "}) {
            graphQL.saveVentaTarjeta(delPdv(estado));
        }
        ArgumentCaptor<VentaTarjeta> guardadas = ArgumentCaptor.forClass(VentaTarjeta.class);
        verify(service, times(4)).guardarConAviso(guardadas.capture());
        guardadas.getAllValues().forEach(vt -> assertEquals("PENDIENTE", vt.getEstado()));
    }
}
