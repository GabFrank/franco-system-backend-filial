package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.input.PdvCajaBalanceDto;
import com.franco.dev.graphql.financiero.input.RetiroDetalleInput;
import com.franco.dev.graphql.financiero.input.RetiroInput;
import com.franco.dev.service.financiero.MonedaService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.financiero.RetiroService;
import com.franco.dev.service.impresion.ImpresionService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.seguridad.AuditorUsuarioId;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Alta de retiro: el efectivo se controla antes de guardar, con la caja bloqueada (issue #80). */
class RetiroGraphQLSaldoTest {

    private static final long CAJA_ID = 2155L;
    private static final long GS = 1L;
    private static final long RS = 2L;

    @Mock private RetiroService service;
    @Mock private PdvCajaService pdvCajaService;
    @Mock private AuditorUsuarioId auditorUsuarioId;
    @Mock private UsuarioService usuarioService;
    @Mock private MonedaService monedaService;
    @Mock private RetiroDetalleGraphQL retiroDetalleGraphQL;
    @Mock private ImpresionService impresionService;
    @InjectMocks private RetiroGraphQL graphQL;

    private final PdvCaja caja = new PdvCaja();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        caja.setId(CAJA_ID);
        when(pdvCajaService.findById(CAJA_ID)).thenReturn(Optional.of(caja));
        when(auditorUsuarioId.firmante(anyString(), any(), any())).thenReturn(7L);
        Usuario usuario = new Usuario();
        usuario.setId(7L);
        when(usuarioService.findById(7L)).thenReturn(Optional.of(usuario));
        when(service.saveAndSend(any(Retiro.class), anyBoolean())).thenAnswer(inv -> inv.getArgument(0));
        when(monedaService.findById(GS)).thenReturn(Optional.of(moneda("GUARANI")));
        when(monedaService.findById(RS)).thenReturn(Optional.of(moneda("REAL")));
        // 40.000 Gs y 5 R$ disponibles
        PdvCajaBalanceDto balance = new PdvCajaBalanceDto();
        balance.setTotalGsAper(100_000.0);
        balance.setTotalRetiroGs(60_000.0);
        balance.setTotalRsAper(5.0);
        when(pdvCajaService.generarBalance(caja)).thenReturn(balance);
    }

    @Test
    void unRetiroDeTodoElDisponibleSeGuardaYElSaldoSeLeeConLaCajaBloqueada() {
        graphQL.saveRetiro(nuevo(), detalles(detalle(GS, 40_000.0)), null, null, true);

        InOrder orden = inOrder(pdvCajaService, service, retiroDetalleGraphQL);
        orden.verify(pdvCajaService).bloquearSaldoCaja(CAJA_ID);
        orden.verify(pdvCajaService).generarBalance(caja);
        orden.verify(service).saveAndSend(any(Retiro.class), eq(false));
        orden.verify(retiroDetalleGraphQL).saveRetiroDetalle(any());
    }

    @Test
    void conDosMonedasSeRechazaLaQueNoAlcanzaSinGuardarNada() {
        GraphQLException e = assertThrows(GraphQLException.class, () -> graphQL.saveRetiro(nuevo(),
                detalles(detalle(GS, 30_000.0), detalle(RS, 10.0)), null, null, true));

        assertEquals("El valor de retiro es mayor al total en caja: REAL", e.getMessage());
        verify(service, never()).saveAndSend(any(Retiro.class), anyBoolean());
        verify(retiroDetalleGraphQL, never()).saveRetiroDetalle(any());
    }

    @Test
    void dosDetallesDeLaMismaMonedaSeSumanAntesDeComparar() {
        assertThrows(GraphQLException.class, () -> graphQL.saveRetiro(nuevo(),
                detalles(detalle(GS, 25_000.0), detalle(GS, 20_000.0)), null, null, true));

        verify(service, never()).saveAndSend(any(Retiro.class), anyBoolean());
    }

    @Test
    void elTicketDelServidorSaleRecienDespuesDelCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            graphQL.saveRetiro(nuevo(), detalles(detalle(GS, 10_000.0)), "frc-ticket", null, false);
            verify(impresionService, never()).printRetiro(any(), any(), any(), anyBoolean());

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(impresionService).printRetiro(any(), eq("frc-ticket"), any(), eq(false));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void editarNoVuelveAControlarElSaldo() {
        RetiroInput input = nuevo();
        input.setId(9L);
        when(service.findById(9L)).thenReturn(Optional.of(new Retiro()));
        input.setUsuarioId(7L);

        graphQL.saveRetiro(input, detalles(detalle(GS, 900_000.0)), null, null, true);

        verify(pdvCajaService, never()).generarBalance(any());
        verify(pdvCajaService, never()).bloquearSaldoCaja(anyLong());
    }

    private static RetiroInput nuevo() {
        RetiroInput input = new RetiroInput();
        input.setCajaSalidaId(CAJA_ID);
        input.setUsuarioId(7L);
        return input;
    }

    private static List<RetiroDetalleInput> detalles(RetiroDetalleInput... d) {
        return Arrays.asList(d);
    }

    private static RetiroDetalleInput detalle(long monedaId, double cantidad) {
        RetiroDetalleInput d = new RetiroDetalleInput();
        d.setMonedaId(monedaId);
        d.setCantidad(cantidad);
        return d;
    }

    private static Moneda moneda(String denominacion) {
        Moneda m = new Moneda();
        m.setDenominacion(denominacion);
        return m;
    }
}
