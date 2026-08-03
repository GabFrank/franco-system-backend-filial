package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.dto.StockLoteDto;
import com.franco.dev.domain.operaciones.enums.EstadoLote;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MovimientoStockLoteServiceTest {

    @Test
    void agregaElBucketSinTrazarCuandoLaExistenciaSuperaLosLotes() {
        List<StockLoteDto> lotes = List.of(
                new StockLoteDto(7L, 1L, 24L, "L-1", null, null, EstadoLote.LIBERADO, 30.0));

        List<StockLoteDto> resultado =
                MovimientoStockLoteService.agregarSinTrazar(lotes, 50.0, 1L, 24L);

        assertEquals(2, resultado.size());
        StockLoteDto sinTrazar = resultado.get(1);
        assertNull(sinTrazar.getLoteId());
        assertEquals("SIN LOTE", sinTrazar.getNumeroLote());
        assertEquals(20.0, (double) sinTrazar.getCantidadDisponible(), 0.0001);
    }

    @Test
    void noAgregaNadaCuandoLosLotesYaCubrenLaExistencia() {
        List<StockLoteDto> lotes = List.of(
                new StockLoteDto(7L, 1L, 24L, "L-1", null, null, EstadoLote.LIBERADO, 50.0));

        assertEquals(1, MovimientoStockLoteService.agregarSinTrazar(lotes, 50.0, 1L, 24L).size());
    }

    @Test
    void expresaComoDeudaLaExistenciaMenorQueLosLotes() {
        List<StockLoteDto> lotes = List.of(
                new StockLoteDto(7L, 1L, 24L, "L-1", null, null, EstadoLote.LIBERADO, 50.0));

        List<StockLoteDto> resultado =
                MovimientoStockLoteService.agregarSinTrazar(lotes, 47.0, 1L, 24L);

        assertEquals(-3.0, (double) resultado.get(1).getCantidadDisponible(), 0.0001);
    }
}
