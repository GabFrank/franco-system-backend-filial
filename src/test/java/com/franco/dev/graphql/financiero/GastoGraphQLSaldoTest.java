package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.graphql.financiero.input.GastoInput;
import com.franco.dev.graphql.financiero.input.PdvCajaBalanceDto;
import com.franco.dev.service.financiero.GastoService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.personas.UsuarioService;
import com.franco.dev.service.seguridad.AuditorUsuarioId;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Alta de gasto: el efectivo se controla con la cuenta del cierre, con la caja bloqueada (issue #80). */
class GastoGraphQLSaldoTest {

    private static final long CAJA_ID = 2155L;

    @Mock private com.franco.dev.eventos.EventoEmisor eventoEmisor;

    @Mock private GastoService service;
    @Mock private PdvCajaService pdvCajaService;
    @Mock private AuditorUsuarioId auditorUsuarioId;
    @Mock private UsuarioService usuarioService;
    @Mock private PlatformTransactionManager transactionManager;
    @InjectMocks private GastoGraphQL graphQL;

    private final PdvCaja caja = new PdvCaja();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        caja.setId(CAJA_ID);
        when(pdvCajaService.findById(CAJA_ID)).thenReturn(Optional.of(caja));
        when(auditorUsuarioId.firmante(anyString(), any(), any())).thenReturn(7L);
        when(service.saveAndSend(any(Gasto.class), anyBoolean())).thenAnswer(inv -> inv.getArgument(0));
        // apertura 100.000 - retiro 60.000 = 40.000 disponibles
        PdvCajaBalanceDto balance = new PdvCajaBalanceDto();
        balance.setTotalGsAper(100_000.0);
        balance.setTotalRetiroGs(60_000.0);
        when(pdvCajaService.generarBalance(caja)).thenReturn(balance);
    }

    @Test
    void unGastoIgualAlDisponibleSeGuardaConLaCajaBloqueadaAntesDeLeerElSaldo() {
        Gasto g = graphQL.saveGasto(nuevo(40_000.0), null, null, true);

        assertNotNull(g);
        InOrder orden = inOrder(pdvCajaService, service);
        orden.verify(pdvCajaService).bloquearSaldoCaja(CAJA_ID);
        orden.verify(pdvCajaService).generarBalance(caja);
        orden.verify(service).saveAndSend(any(Gasto.class), eq(false));
        verify(transactionManager).commit(any());
    }

    @Test
    void unGastoMayorAlDisponibleSeRechazaSinGuardar() {
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> graphQL.saveGasto(nuevo(50_000.0), null, null, true));

        assertEquals("El valor de gasto es mayor al total en caja: Guaraníes", e.getMessage());
        verify(service, never()).saveAndSend(any(Gasto.class), anyBoolean());
        verify(transactionManager).rollback(any());
    }

    @Test
    void editarNoVuelveAControlarElSaldo() {
        GastoInput input = nuevo(90_000.0);
        input.setId(5L);
        when(service.findById(5L)).thenReturn(Optional.of(new Gasto()));

        graphQL.saveGasto(input, null, null, true);

        verify(pdvCajaService, never()).generarBalance(any());
        verify(pdvCajaService, never()).bloquearSaldoCaja(anyLong());
        verify(service).saveAndSend(any(Gasto.class), eq(false));
    }

    @Test
    void unIdInventadoNoEsquivaElControl() {
        GastoInput input = nuevo(50_000.0);
        input.setId(123456L); // no existe

        assertThrows(GraphQLException.class, () -> graphQL.saveGasto(input, null, null, true));

        verify(service, never()).saveAndSend(any(Gasto.class), anyBoolean());
    }

    @Test
    void sinCajaSeAvisaEnVezDeRomper() {
        GastoInput input = nuevo(1_000.0);
        input.setCajaId(999L);
        when(pdvCajaService.findById(999L)).thenReturn(Optional.empty());

        GraphQLException e = assertThrows(GraphQLException.class, () -> graphQL.saveGasto(input, null, null, true));

        assertEquals("Caja no encontrada", e.getMessage());
        verify(service, never()).saveAndSend(any(Gasto.class), anyBoolean());
    }

    private static GastoInput nuevo(double retiroGs) {
        GastoInput input = new GastoInput();
        input.setCajaId(CAJA_ID);
        input.setUsuarioId(7L);
        input.setObservacion("COMPRA DE HIELO");
        input.setRetiroGs(retiroGs);
        input.setFinalizado(true);
        return input;
    }
}
