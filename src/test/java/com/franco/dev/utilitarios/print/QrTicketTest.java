package com.franco.dev.utilitarios.print;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QrTicketTest {

    @Test
    void retiroLlevaSucursalRetiroYCaja() {
        assertEquals("frc-24-RETIRO-1151-24-IngresarRetiroCajaMayor-662-null", QrTicket.retiro(24L, 1151L, 662L));
    }

    @Test
    void retiroSinCajaIgualSeImprime() {
        // El lector de caja mayor cae a retiro.cajaSalida cuando la posicion 6 viene "null".
        assertEquals("frc-24-RETIRO-1151-24-IngresarRetiroCajaMayor-null-null", QrTicket.retiro(24L, 1151L, null));
    }

    @Test
    void cajaLlevaSucursalYCaja() {
        assertEquals("frc-24-PDVCAJA-662-24-IngresarRetiroCajaMayor-null-null", QrTicket.caja(24L, 662L));
    }

    @Test
    void sinSucursalOSinIdNoHayCodigo() {
        assertNull(QrTicket.retiro(null, 1151L, 662L));
        assertNull(QrTicket.retiro(24L, null, 662L));
        assertNull(QrTicket.caja(null, 662L));
        assertNull(QrTicket.caja(24L, null));
    }
}
