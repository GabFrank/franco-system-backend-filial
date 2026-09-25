package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.enums.PdvCajaEstado;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cuando una caja cuenta como abierta para la captura del cupon por foto.
 *
 * <p>Hasta el 2026-09-24 la captura exigia {@code pdv_caja.estado = EN_PROCESO}, una columna que
 * nadie escribe: estaba vacia en las 5.366 cajas de la filial 1 de farmacia y en las 2.379 de
 * alpha. La primera prueba real (caja 5459, abierta) fallo con "la caja no esta abierta". El
 * criterio correcto es el que ya usa {@code PdvCajaService}: {@code activo = true}.
 */
class CapturaCuponCajaAbiertaTest {

    private static PdvCaja caja(Boolean activo, PdvCajaEstado estado, LocalDateTime cierre) {
        PdvCaja c = new PdvCaja();
        c.setActivo(activo);
        c.setEstado(estado);
        c.setFechaApertura(LocalDateTime.of(2026, 9, 24, 15, 11));
        c.setFechaCierre(cierre);
        return c;
    }

    /** El caso real de farmacia: caja abierta con estado vacio. */
    @Test
    void cajaActivaConEstadoVacioEstaAbierta() {
        assertTrue(CapturaCuponService.cajaAbierta(caja(true, null, null)));
    }

    @Test
    void cajaCerradaNoEstaAbierta() {
        assertFalse(CapturaCuponService.cajaAbierta(
                caja(false, null, LocalDateTime.of(2026, 9, 24, 22, 0))));
    }

    @Test
    void activoNuloNoEstaAbierta() {
        assertFalse(CapturaCuponService.cajaAbierta(caja(null, null, null)));
    }

    @Test
    void sinCajaNoEstaAbierta() {
        assertFalse(CapturaCuponService.cajaAbierta(null));
    }

    /** El estado ya no decide: aunque diga EN_PROCESO, una caja inactiva esta cerrada. */
    @Test
    void estadoEnProcesoNoAlcanzaSiLaCajaNoEstaActiva() {
        assertFalse(CapturaCuponService.cajaAbierta(
                caja(false, PdvCajaEstado.EN_PROCESO, LocalDateTime.of(2026, 9, 24, 22, 0))));
    }
}
