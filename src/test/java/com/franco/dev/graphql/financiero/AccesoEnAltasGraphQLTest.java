package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.domain.financiero.RetiroDetalle;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.input.GastoDetalleInput;
import com.franco.dev.graphql.financiero.input.GastoInput;
import com.franco.dev.graphql.financiero.input.RetiroDetalleInput;
import com.franco.dev.graphql.financiero.input.RetiroInput;
import com.franco.dev.service.financiero.GastoDetalleService;
import com.franco.dev.service.financiero.GastoService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.financiero.RetiroDetalleService;
import com.franco.dev.service.financiero.RetiroService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.seguridad.AuditorUsuarioId;
import com.franco.dev.service.seguridad.RolSesionService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Altas de gasto y retiro: el control de acceso corre antes que todo; las mutations de detalle sueltas, solo ADMIN. */
class AccesoEnAltasGraphQLTest {

    private static final long CAJA_ID = 1185L;
    private static final GraphQLException NO = new GraphQLException("No autorizado: solo el dueño o el rol ANALISIS DE CAJA.");

    @Mock private com.franco.dev.eventos.EventoEmisor eventoEmisor;

    @Mock private PdvCajaService pdvCajaService;
    @Mock private AuditorUsuarioId auditorUsuarioId;
    @Mock private UsuarioService usuarioService;
    @Mock private GastoService gastoService;
    @Mock private RetiroService retiroService;
    @Mock private RetiroDetalleService retiroDetalleService;
    @Mock private GastoDetalleService gastoDetalleService;
    @Mock private RetiroDetalleGraphQL retiroDetalleGraphQLMock;
    @Mock private RolSesionService rolSesionService;
    @Mock private com.franco.dev.service.financiero.MonedaService monedaService;
    @Mock private PlatformTransactionManager transactionManager;

    @InjectMocks private GastoGraphQL gastoGraphQL;
    @InjectMocks private RetiroGraphQL retiroGraphQL;
    @InjectMocks private RetiroDetalleGraphQL retiroDetalleGraphQL;
    @InjectMocks private GastoDetalleGraphQL gastoDetalleGraphQL;

    private final PdvCaja caja = new PdvCaja();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        caja.setId(CAJA_ID);
        when(pdvCajaService.findById(CAJA_ID)).thenReturn(Optional.of(caja));
        when(pdvCajaService.findById(999L)).thenReturn(Optional.empty());
        when(auditorUsuarioId.firmante(anyString(), any(), any())).thenReturn(7L);
        Usuario u = new Usuario();
        u.setId(7L);
        when(usuarioService.findById(7L)).thenReturn(Optional.of(u));
        // RetiroGraphQL usa su RetiroService como "service" y el mock del resolver de detalles.
        setField(retiroGraphQL, "service", retiroService);
        setField(retiroGraphQL, "retiroDetalleGraphQL", retiroDetalleGraphQLMock);
        setField(gastoGraphQL, "service", gastoService);
    }

    @Test
    void unGastoSinAccesoSeRechazaAntesDeFirmarBloquearOGuardar() {
        doThrow(NO).when(pdvCajaService).controlarAccesoEnAlta("GastoGraphQL.saveGasto", caja);

        assertThrows(GraphQLException.class, () -> gastoGraphQL.saveGasto(gasto(CAJA_ID), null, null, true));

        verify(auditorUsuarioId, never()).firmante(anyString(), any(), any());
        verify(pdvCajaService, never()).bloquearSaldoCaja(anyLong());
        verify(gastoService, never()).saveAndSend(any(Gasto.class), anyBoolean());
    }

    @Test
    void unRetiroSinAccesoSeRechazaSinGuardarNada() {
        doThrow(NO).when(pdvCajaService).controlarAccesoEnAlta("RetiroGraphQL.saveRetiro", caja);

        assertThrows(GraphQLException.class, () -> retiroGraphQL.saveRetiro(retiro(CAJA_ID),
                Collections.singletonList(detalle()), null, null, true));

        verify(pdvCajaService, never()).bloquearSaldoCaja(anyLong());
        verify(retiroService, never()).saveAndSend(any(Retiro.class), anyBoolean());
    }

    @Test
    void unRetiroConCajaInexistenteSeRechaza() {
        GraphQLException e = assertThrows(GraphQLException.class, () -> retiroGraphQL.saveRetiro(retiro(999L),
                Collections.singletonList(detalle()), null, null, true));

        assertEquals("Caja no encontrada", e.getMessage());
        verify(retiroService, never()).saveAndSend(any(Retiro.class), anyBoolean());
    }

    @Test
    void elRetiroDeUnCajeroGuardaSusDetallesSinExigirAdmin() {
        when(retiroService.saveAndSend(any(Retiro.class), anyBoolean())).thenAnswer(inv -> inv.getArgument(0));

        retiroGraphQL.saveRetiro(retiro(CAJA_ID), Collections.singletonList(detalle()), null, null, true);

        InOrder orden = inOrder(pdvCajaService, retiroDetalleGraphQLMock);
        orden.verify(pdvCajaService).controlarAccesoEnAlta("RetiroGraphQL.saveRetiro", caja);
        orden.verify(retiroDetalleGraphQLMock).guardarDetalle(any());
        verify(retiroDetalleGraphQLMock, never()).saveRetiroDetalle(any());
    }

    @Test
    void guardarDetalleNoExigeAdminYLaMutationSueltaSi() {
        when(retiroDetalleService.saveAndSend(any(RetiroDetalle.class), anyBoolean())).thenReturn(new RetiroDetalle());

        retiroDetalleGraphQL.guardarDetalle(detalle());
        verify(rolSesionService, never()).exigirAdmin();

        doThrow(new GraphQLException("No autorizado: se requiere el rol ADMIN.")).when(rolSesionService).exigirAdmin();
        assertThrows(GraphQLException.class, () -> retiroDetalleGraphQL.saveRetiroDetalle(detalle()));
        assertThrows(GraphQLException.class, () -> gastoDetalleGraphQL.saveGastoDetalle(new GastoDetalleInput()));
        verify(retiroDetalleService, times(1)).saveAndSend(any(RetiroDetalle.class), anyBoolean());
        verifyNoInteractions(gastoDetalleService);
    }

    private static GastoInput gasto(long cajaId) {
        GastoInput g = new GastoInput();
        g.setCajaId(cajaId);
        g.setUsuarioId(7L);
        g.setObservacion("PRUEBA");
        g.setRetiroGs(1000.0);
        g.setFinalizado(true);
        return g;
    }

    private static RetiroInput retiro(long cajaId) {
        RetiroInput r = new RetiroInput();
        r.setCajaSalidaId(cajaId);
        r.setUsuarioId(7L);
        return r;
    }

    private static RetiroDetalleInput detalle() {
        RetiroDetalleInput d = new RetiroDetalleInput();
        d.setMonedaId(1L);
        d.setCantidad(1000.0);
        return d;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
