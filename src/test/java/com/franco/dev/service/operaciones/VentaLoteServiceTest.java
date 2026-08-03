package com.franco.dev.service.operaciones;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class VentaLoteServiceTest {

    @Test
    void devuelveLaAsignacionSinTrazarPorLoQueFefoNoCubrio() {
        LoteFefoService.AsignacionLote r = VentaLoteService.faltanteSinTrazar(3.0, 0.0);

        assertNotNull(r);
        assertNull(r.getLoteId());
        assertEquals("SIN LOTE", r.getNumeroLote());
        assertEquals(3.0, (double) r.getCantidad());
    }

    @Test
    void cubrimientoParcialDejaSoloLaDiferencia() {
        LoteFefoService.AsignacionLote r = VentaLoteService.faltanteSinTrazar(6.0, 4.0);
        assertNotNull(r);
        assertEquals(2.0, (double) r.getCantidad());
    }

    @Test
    void noDevuelveNadaCuandoFefoCubrioTodo() {
        assertNull(VentaLoteService.faltanteSinTrazar(3.0, 3.0));
    }
}
