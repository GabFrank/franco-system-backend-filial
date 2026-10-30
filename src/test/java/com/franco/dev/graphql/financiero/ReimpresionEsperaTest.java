package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.graphql.financiero.input.SenaCuponInput;
import com.franco.dev.service.financiero.GastoService;
import com.franco.dev.service.financiero.PdvCajaService;
import com.franco.dev.service.financiero.RetiroService;
import com.franco.dev.service.financiero.RetiroDetalleService;
import com.franco.dev.service.impresion.ImpresionService;
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

/** Las reimpresiones esperan al envio y devuelven si el ticket salio (#213). */
class ReimpresionEsperaTest {

    @Mock private RetiroService retiroService;
    @Mock private GastoService gastoService;
    @Mock private PdvCajaService pdvCajaService;
    @Mock private RetiroDetalleService retiroDetalleService;
    @Mock private ImpresionService impresionService;
    @InjectMocks private RetiroGraphQL retiroGraphQL;
    @InjectMocks private GastoGraphQL gastoGraphQL;
    @InjectMocks private PdvCajaGraphQL pdvCajaGraphQL;
    @InjectMocks private VentaTarjetaGraphQL ventaTarjetaGraphQL;
    @Mock private com.franco.dev.service.financiero.FacturaLegalService facturaLegalService;
    @InjectMocks private FacturaLegalGraphQL facturaLegalGraphQL;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        PdvCaja caja = new PdvCaja();
        caja.setId(662L);
        Retiro retiro = new Retiro();
        retiro.setId(9L);
        retiro.setCajaSalida(caja);
        when(retiroService.findById(9L)).thenReturn(Optional.of(retiro));
        Gasto gasto = new Gasto();
        gasto.setId(15L);
        gasto.setCaja(caja);
        when(gastoService.findById(15L)).thenReturn(Optional.of(gasto));
        when(pdvCajaService.findById(662L)).thenReturn(Optional.of(caja));
    }

    @Test
    void reimprimirRetiroEsperaYDevuelveElResultado() {
        when(impresionService.printRetiro(any(), any(), any(), any(), isNull(), eq(true))).thenReturn(false);
        assertFalse(retiroGraphQL.reimprimirRetiro(9L, "ticket", "CAJA 1", 24L));

        when(impresionService.printRetiro(any(), any(), any(), any(), isNull(), eq(true))).thenReturn(true);
        assertTrue(retiroGraphQL.reimprimirRetiro(9L, "ticket", "CAJA 1", 24L));
        verify(impresionService, times(2)).printRetiro(any(), eq("ticket"), eq("CAJA 1"), eq(true), isNull(), eq(true));
    }

    @Test
    void reimprimirGastoEsperaYDevuelveElResultado() {
        when(impresionService.printGasto(any(), any(), any(), isNull(), eq(true))).thenReturn(false);
        assertFalse(gastoGraphQL.reimprimirGasto(15L, "ticket", 24L));

        when(impresionService.printGasto(any(), any(), any(), isNull(), eq(true))).thenReturn(true);
        assertTrue(gastoGraphQL.reimprimirGasto(15L, "ticket", 24L));
    }

    @Test
    void imprimirBalanceDelDesktopAvisaSiNoSalio() {
        when(pdvCajaService.imprimirBalanceEsperando(662L, "ticket", "CAJA 1")).thenReturn(false);
        GraphQLException e = assertThrows(GraphQLException.class,
                () -> pdvCajaGraphQL.imprimirBalance(662L, "ticket", "CAJA 1", 24L));
        assertEquals("No se pudo imprimir el balance", e.getMessage());

        when(pdvCajaService.imprimirBalanceEsperando(662L, "ticket", "CAJA 1")).thenReturn(true);
        assertEquals(Long.valueOf(662L), pdvCajaGraphQL.imprimirBalance(662L, "ticket", "CAJA 1", 24L).getId());
        verify(pdvCajaService, never()).imprimirBalance(anyLong(), any(), any());
    }

    @Test
    void imprimirSenaCuponEspera() {
        SenaCuponInput input = new SenaCuponInput();
        input.setQr("FRC-SENA|80078|12");
        when(impresionService.printSenaCupon(any(), any(), any(), isNull(), eq(true))).thenReturn(false);
        assertFalse(ventaTarjetaGraphQL.imprimirSenaCupon(input, "ticket", "CAJA 1"));
    }

    @Test
    void retiroYGastoDeUnaCajaAjenaSeRechazanConErrorSinImprimir() {
        doThrow(new GraphQLException("No autorizado")).when(pdvCajaService).exigirAccesoARegistroDeCaja(any());
        // Un error y no false: el desktop muestra el motivo en vez de "intente de nuevo".
        assertThrows(GraphQLException.class, () -> retiroGraphQL.reimprimirRetiro(9L, "ticket", "CAJA 1", 24L));
        assertThrows(GraphQLException.class, () -> gastoGraphQL.reimprimirGasto(15L, "ticket", 24L));
        verifyNoInteractions(impresionService);
    }

    @Test
    void retiroYGastoInexistentesDicenQueNoExisten() {
        GraphQLException r = assertThrows(GraphQLException.class,
                () -> retiroGraphQL.reimprimirRetiro(404L, "ticket", "CAJA 1", 24L));
        assertEquals("El retiro 404 no existe", r.getMessage());
        GraphQLException g = assertThrows(GraphQLException.class, () -> gastoGraphQL.reimprimirGasto(404L, "ticket", 24L));
        assertEquals("El gasto 404 no existe", g.getMessage());
    }

    @Test
    void reimprimirRetiroYGastoPidenElAccesoPorSuCaja() {
        when(impresionService.printRetiro(any(), any(), any(), any(), isNull(), eq(true))).thenReturn(true);
        when(impresionService.printGasto(any(), any(), any(), isNull(), eq(true))).thenReturn(true);
        retiroGraphQL.reimprimirRetiro(9L, "ticket", "CAJA 1", 24L);
        gastoGraphQL.reimprimirGasto(15L, "ticket", 24L);
        verify(pdvCajaService, times(2)).exigirAccesoARegistroDeCaja(argThat(c -> c != null && c.getId() == 662L));
    }

    @Test
    void imprimirFacturasDeUnaCajaAjenaSeRechazaSinBuscarlas() {
        doThrow(new GraphQLException("No autorizado")).when(pdvCajaService).exigirAccesoACaja(662L);
        assertThrows(GraphQLException.class, () -> facturaLegalGraphQL.imprimirFacturasPorCaja(662L, "ticket", 24L));
        verifyNoInteractions(facturaLegalService);
    }
}
