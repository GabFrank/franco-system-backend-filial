package com.franco.dev.service.operaciones;

import com.franco.dev.service.financiero.BloqueoTransaccionalService;
import graphql.GraphQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.IllegalTransactionStateException;

import javax.persistence.PessimisticLockException;
import java.sql.SQLException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * #185: el saldo de un lote se lee con el lock de la sucursal tomado. Sin el lock, dos ventas en
 * paralelo leían el mismo saldo y asignaban dos veces las mismas unidades.
 *
 * Lo que importa es el ORDEN: un lock tomado después de leer no protege nada. Por eso los
 * {@code InOrder} son el test, no un adorno.
 */
class LoteFefoServiceBloqueoTest {

    private static final long PRODUCTO = 4680L;
    private static final long SUCURSAL = 24L;

    private MovimientoStockLoteService ledger;
    private BloqueoTransaccionalService bloqueo;
    private LoteFefoService service;

    @BeforeEach
    void setUp() {
        ledger = mock(MovimientoStockLoteService.class);
        bloqueo = mock(BloqueoTransaccionalService.class);
        when(ledger.stockPorLote(anyLong(), anyLong())).thenReturn(Collections.emptyList());
        service = new LoteFefoService(ledger, bloqueo);
    }

    private void verificaLockAntesDeLeer() {
        InOrder orden = inOrder(bloqueo, ledger);
        orden.verify(bloqueo).tomar("lote-saldo:" + SUCURSAL, "15s");
        orden.verify(ledger).stockPorLote(PRODUCTO, SUCURSAL);
    }

    @Test
    void asignarTomaElLockDeLaSucursalAntesDeLeerElSaldo() {
        service.asignar(PRODUCTO, SUCURSAL, 20.0);
        verificaLockAntesDeLeer();
    }

    @Test
    void asignarConPreferenciaSinPreferenciasTomaElLockAntesDeLeer() {
        service.asignarConPreferencia(PRODUCTO, SUCURSAL, 20.0, Collections.emptyList());
        verificaLockAntesDeLeer();
    }

    @Test
    void asignarConPreferenciaConPreferenciasTomaElLockAntesDeLeer() {
        service.asignarConPreferencia(PRODUCTO, SUCURSAL, 20.0, Collections.singletonList(
                new LoteFefoService.AsignacionLote(7L, "L-7", 10.0)));
        verificaLockAntesDeLeer();
    }

    @Test
    void conDatosInvalidosNoTomaNadaNiLee() {
        service.asignar(PRODUCTO, SUCURSAL, 0.0);
        service.asignar(null, SUCURSAL, 5.0);
        service.asignarConPreferencia(PRODUCTO, null, 5.0, Collections.singletonList(
                new LoteFefoService.AsignacionLote(7L, "L-7", 10.0)));
        verifyZeroInteractions(bloqueo, ledger);
    }

    @Test
    void siVenceLaEsperaAvisaParaReintentarYNoLee() {
        doThrow(new PessimisticLockException("espera vencida",
                new RuntimeException(new SQLException("canceling statement due to lock timeout", "55P03"))))
                .when(bloqueo).tomar(anyString(), anyString());

        GraphQLException error = assertThrows(GraphQLException.class,
                () -> service.asignar(PRODUCTO, SUCURSAL, 20.0));

        assertTrue(error.getMessage().contains("Reintentá"));
        verify(ledger, never()).stockPorLote(anyLong(), anyLong());
    }

    @Test
    void otroErrorDelLockPasaTalCual() {
        IllegalTransactionStateException sinTransaccion = new IllegalTransactionStateException("sin transaccion");
        doThrow(sinTransaccion).when(bloqueo).tomar(anyString(), anyString());

        assertSame(sinTransaccion, assertThrows(IllegalTransactionStateException.class,
                () -> service.bloquearSaldo(SUCURSAL)));
    }

    @Test
    void reconoceLaEsperaVencidaSoloPorSuSqlState() {
        assertTrue(LoteFefoService.esEsperaVencida(new RuntimeException(new SQLException("x", "55P03"))));
        // 40P01 es deadlock: no es una espera vencida y no se disfraza de "reintentá".
        assertFalse(LoteFefoService.esEsperaVencida(new RuntimeException(new SQLException("x", "40P01"))));
        assertFalse(LoteFefoService.esEsperaVencida(new RuntimeException("sin causa SQL")));
    }
}
