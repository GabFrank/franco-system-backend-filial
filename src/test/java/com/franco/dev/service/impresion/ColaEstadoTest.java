package com.franco.dev.service.impresion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La tarjeta de la app mostraba "Activa" (impresora.activo de la BD) mientras CUPS tenia la
 * cola deshabilitada hace tres dias y los tickets se perdian. Estos tests fijan la lectura
 * del estado real de lpstat -p, que es lo que ahora muestra la UI.
 */
class ColaEstadoTest {

    /** Salida real de frc-servidor (LC_ALL=C). */
    private static final String SALIDA_PROD = String.join("\n",
            "printer adm_ticket disabled since Wed 19 Aug 2026 01:11:49 PM -03 -",
            "\tUnable to connect to CIFS host after (tried 3 times)",
            "printer EPSON_L395_Series is idle.  enabled since Wed 22 Jul 2026 09:50:13 AM -03",
            "printer pronet is idle.  enabled since Wed 31 Dec 1969 08:00:00 PM -04",
            "printer ticket_soporte is idle.  enabled since Sat 22 Aug 2026 11:05:19 AM -03");

    @Test
    void leeTodasLasColas() {
        assertEquals(4, ColaEstado.parsear(SALIDA_PROD).size());
    }

    @Test
    void colaFrenadaQuedaDeshabilitadaConSuRazon() {
        ColaEstado cola = ColaEstado.parsear(SALIDA_PROD).get(0);

        assertEquals("adm_ticket", cola.getNombre());
        assertEquals("DESHABILITADA", cola.getEstado());
        assertFalse(cola.getHabilitada());
        assertEquals("Unable to connect to CIFS host after (tried 3 times)", cola.getRazon());
    }

    @Test
    void colaSanaQuedaInactivaYHabilitada() {
        ColaEstado cola = ColaEstado.parsear(SALIDA_PROD).get(1);

        assertEquals("EPSON_L395_Series", cola.getNombre());
        assertEquals("INACTIVA", cola.getEstado());
        assertTrue(cola.getHabilitada());
    }

    @Test
    void colaTrabajandoQuedaImprimiendo() {
        List<ColaEstado> colas = ColaEstado.parsear(
                "printer ticket_soporte now printing ticket_soporte-42.  enabled since Sat 22 Aug 2026");

        assertEquals("IMPRIMIENDO", colas.get(0).getEstado());
        assertTrue(colas.get(0).getHabilitada());
    }

    @Test
    void deshabilitadaSinRazonNoRompe() {
        List<ColaEstado> colas = ColaEstado.parsear("printer adm_ticket disabled since Wed 19 Aug 2026 -");

        assertEquals("DESHABILITADA", colas.get(0).getEstado());
        assertEquals("", colas.get(0).getRazon());
    }

    @Test
    void ignoraLasLineasQueNoSonUnaCola() {
        List<ColaEstado> colas = ColaEstado.parsear(String.join("\n",
                "scheduler is running",
                "no system default destination",
                "printer adm_ticket is idle.  enabled since Wed 22 Jul 2026"));

        assertEquals(1, colas.size());
        assertEquals("adm_ticket", colas.get(0).getNombre());
    }

    @Test
    void salidaVaciaDevuelveListaVacia() {
        assertTrue(ColaEstado.parsear("").isEmpty());
        assertTrue(ColaEstado.parsear(null).isEmpty());
    }
}
