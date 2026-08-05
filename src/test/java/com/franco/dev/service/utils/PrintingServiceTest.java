package com.franco.dev.service.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

class PrintingServiceTest {

    private static final String IMPRESORA_INEXISTENTE = "impresora-inexistente-frc-test";

    @Test
    void buscarImpresoraInexistenteDevuelveNull() {
        PrintingService service = new PrintingService();
        assertNull(service.getPrintService(IMPRESORA_INEXISTENTE));
    }

    @Test
    void impresoraNoEncontradaNoEnvenenaElCache() {
        PrintingService service = new PrintingService();
        // Primer lookup falla: la impresora no existe en el sistema
        service.getPrintService(IMPRESORA_INEXISTENTE);
        // Segundo lookup: no debe lanzar NPE por un null cacheado en printServiceList,
        // ni impedir reintentos de impresion posteriores
        assertDoesNotThrow(() -> assertNull(service.getPrintService(IMPRESORA_INEXISTENTE)));
    }
}
