package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.MovimientoStockLote;
import com.franco.dev.repository.operaciones.MovimientoStockLoteRepository;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * Regresion (lotes fase 3, entrega 1): anular una venta en la filial tiene que apagar el desglose
 * por lote de su movimiento, y reactivarla tiene que volver a encenderlo. Si no, el stock agregado
 * vuelve pero el saldo por lote queda descontado y los dos numeros cuentan distinto.
 */
class MovimientoStockLoteServiceAnulacionTest {

    private MovimientoStockLote hija(long id, boolean estado) {
        MovimientoStockLote m = new MovimientoStockLote();
        m.setId(id);
        m.setEstado(estado);
        return m;
    }

    @Test
    void anularVentaApagaTodasLasHijasDelMovimiento() {
        MovimientoStockLoteRepository repo = mock(MovimientoStockLoteRepository.class);
        List<MovimientoStockLote> hijas = Arrays.asList(hija(2, true), hija(4, true));
        when(repo.findByMovimientoStockIdAndSucursalId(10L, 4L)).thenReturn(hijas);

        int cambiadas = new MovimientoStockLoteService(repo).cambiarEstadoPorMovimiento(10L, 4L, false);

        assertEquals(2, cambiadas);
        assertFalse(hijas.get(0).getEstado());
        assertFalse(hijas.get(1).getEstado());
        verify(repo).saveAll(hijas);
    }

    @Test
    void reactivarVentaVuelveAEncenderLasHijas() {
        MovimientoStockLoteRepository repo = mock(MovimientoStockLoteRepository.class);
        List<MovimientoStockLote> hijas = Arrays.asList(hija(2, false));
        when(repo.findByMovimientoStockIdAndSucursalId(10L, 4L)).thenReturn(hijas);

        assertEquals(1, new MovimientoStockLoteService(repo).cambiarEstadoPorMovimiento(10L, 4L, true));
        assertTrue(hijas.get(0).getEstado());
    }

    @Test
    void sinCambiosNoEscribe() {
        MovimientoStockLoteRepository repo = mock(MovimientoStockLoteRepository.class);
        when(repo.findByMovimientoStockIdAndSucursalId(10L, 4L)).thenReturn(Arrays.asList(hija(2, false)));

        assertEquals(0, new MovimientoStockLoteService(repo).cambiarEstadoPorMovimiento(10L, 4L, false));
        verify(repo, never()).saveAll(anyList());
    }

    @Test
    void datosIncompletosNoTocanElLedger() {
        MovimientoStockLoteRepository repo = mock(MovimientoStockLoteRepository.class);
        MovimientoStockLoteService s = new MovimientoStockLoteService(repo);
        assertEquals(0, s.cambiarEstadoPorMovimiento(null, 4L, false));
        assertEquals(0, s.cambiarEstadoPorMovimiento(10L, null, false));
        assertEquals(0, s.cambiarEstadoPorMovimiento(10L, 4L, null));
        verifyZeroInteractions(repo);
    }
}
