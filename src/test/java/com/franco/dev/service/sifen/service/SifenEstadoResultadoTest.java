package com.franco.dev.service.sifen.service;

import com.franco.dev.domain.financiero.enums.EstadoDE;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * SIFEN responde el dEstRes con tres valores: "Aprobado", "Aprobado con observación"
 * y "Rechazado". El segundo ES una aprobación — la observación típica es la transmisión
 * fuera de plazo.
 *
 * El mapeo comparaba contra la frase completa, así que "Aprobado con observación" caía
 * en el else de rechazo. El 2026-08-20 venció el certificado de firma de la red bodega
 * y al recuperarse la cola, 5.467 documentos aprobados quedaron marcados RECHAZADO.
 */
class SifenEstadoResultadoTest {

    @Test
    void aprobadoEsAprobado() {
        assertEquals(EstadoDE.APROBADO, SifenService.mapearEstadoResultadoSifen("Aprobado"));
    }

    @Test
    void aprobadoConObservacionTambienEsAprobado() {
        assertEquals(EstadoDE.APROBADO,
            SifenService.mapearEstadoResultadoSifen("Aprobado con observación"));
    }

    @Test
    void aprobadoConObservacionSinTilde() {
        assertEquals(EstadoDE.APROBADO,
            SifenService.mapearEstadoResultadoSifen("Aprobado con observacion"));
    }

    /**
     * El caso que se vio en produccion: extraerValorXML no decodifica entidades y
     * decodeHtmlEntities no cubre las numericas, asi que el texto llega escapado.
     */
    @Test
    void aprobadoConObservacionHtmlEscapado() {
        assertEquals(EstadoDE.APROBADO,
            SifenService.mapearEstadoResultadoSifen("Aprobado con observaci&#243;n"));
    }

    @Test
    void toleraEspaciosYMayusculas() {
        assertEquals(EstadoDE.APROBADO, SifenService.mapearEstadoResultadoSifen("  APROBADO  "));
        assertEquals(EstadoDE.RECHAZADO, SifenService.mapearEstadoResultadoSifen(" rechazado "));
    }

    @Test
    void rechazadoEsRechazado() {
        assertEquals(EstadoDE.RECHAZADO, SifenService.mapearEstadoResultadoSifen("Rechazado"));
    }

    /** Sin dEstRes o con un valor desconocido no se infiere nada: el llamador no toca el estado. */
    @Test
    void desconocidoDevuelveNull() {
        assertNull(SifenService.mapearEstadoResultadoSifen(null));
        assertNull(SifenService.mapearEstadoResultadoSifen(""));
        assertNull(SifenService.mapearEstadoResultadoSifen("En proceso"));
    }
}
