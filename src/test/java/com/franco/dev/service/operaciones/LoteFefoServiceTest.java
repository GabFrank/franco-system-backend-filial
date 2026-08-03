package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.dto.StockLoteDto;
import com.franco.dev.domain.operaciones.enums.EstadoLote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoteFefoServiceTest {

    private MovimientoStockLoteService movimientoStockLoteService;
    private LoteFefoService service;

    @BeforeEach
    void setUp() {
        movimientoStockLoteService = mock(MovimientoStockLoteService.class);
        service = new LoteFefoService(movimientoStockLoteService);
    }

    private StockLoteDto lote(Long id, String numero, double saldo) {
        return new StockLoteDto(id, 1L, 24L, numero, null, null, EstadoLote.LIBERADO, saldo);
    }

    private StockLoteDto sinTrazar(double saldo) {
        return new StockLoteDto(null, 1L, 24L, "SIN LOTE", null, null, null, saldo);
    }

    @Test
    void agotaLosLotesRealesAntesDeTocarElBucketSinTrazar() {
        when(movimientoStockLoteService.stockPorLote(anyLong(), anyLong()))
                .thenReturn(List.of(sinTrazar(100.0), lote(7L, "L-1", 4.0)));

        List<LoteFefoService.AsignacionLote> r = service.asignar(1L, 24L, 6.0);

        assertEquals(2, r.size());
        assertEquals((Long) 7L, r.get(0).getLoteId());
        assertEquals((Double) 4.0, r.get(0).getCantidad());
        assertNull(r.get(1).getLoteId());
        assertEquals("SIN LOTE", r.get(1).getNumeroLote());
        assertEquals((Double) 2.0, r.get(1).getCantidad());
    }

    @Test
    void noTocaElBucketSiLosLotesRealesAlcanzan() {
        when(movimientoStockLoteService.stockPorLote(anyLong(), anyLong()))
                .thenReturn(List.of(lote(7L, "L-1", 10.0), sinTrazar(100.0)));

        List<LoteFefoService.AsignacionLote> r = service.asignar(1L, 24L, 6.0);

        assertEquals(1, r.size());
        assertEquals((Long) 7L, r.get(0).getLoteId());
    }

    @Test
    void ignoraElBucketCuandoEsDeuda() {
        when(movimientoStockLoteService.stockPorLote(anyLong(), anyLong()))
                .thenReturn(List.of(sinTrazar(-3.0)));

        assertEquals(0, service.asignar(1L, 24L, 5.0).size());
    }
}
