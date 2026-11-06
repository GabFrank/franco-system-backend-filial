package com.franco.dev.service.utils;

import com.franco.dev.utilitarios.print.output.CapturaPrintService;
import org.junit.jupiter.api.Test;

import javax.print.PrintService;
import java.lang.reflect.Field;
import java.util.List;

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

    @Test
    void sinNombreNoHayImpresoraNiNpe() throws Exception {
        PrintingService service = new PrintingService();
        Field cache = PrintingService.class.getDeclaredField("printServiceList");
        cache.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<PrintService> lista = (List<PrintService>) cache.get(service);
        lista.add(new CapturaPrintService());
        assertNull(service.getPrintService(null));
        assertNull(service.getPrintService(" "));
    }
}
