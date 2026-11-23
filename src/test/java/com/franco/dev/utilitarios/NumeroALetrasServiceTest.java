package com.franco.dev.utilitarios;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class NumeroALetrasServiceTest {

    private final NumeroALetrasService s = new NumeroALetrasService();

    @Test
    void hastaNueveDigitos_elTextoDeSiempre() {
        assertEquals("CERO ", s.converter("0", true));
        assertEquals("CIENTO VEINTE Y TRES ", s.converter("123", true));
        assertEquals("TREINTA Y CINCO MIL SEISCIENTOS OCHENTA Y CINCO ", s.converter("35685", true));
        assertEquals("UN MILLON ", s.converter("1000000", true));
        assertEquals("CIENTO VEINTE Y TRES MILLONES CUATROCIENTOS CINCUENTA Y SEIS MIL SETECIENTOS OCHENTA Y NUEVE ",
                s.converter("123456789", true));
    }

    @Test
    void deDosANueveMillones_enPlural() {
        assertEquals("DOS MILLONES QUINIENTOS MIL ", s.converter("2500000", true));
    }

    @Test
    void desdeMilMillones_yaNoDevuelveNull() {
        assertEquals("MIL MILLONES ", s.converter("1000000000", true));
        assertEquals("DOS MIL TRECIENTOS MILLONES ", s.converter("2300000000", true));
        assertEquals("MIL UN MILLONES ", s.converter("1001000000", true));
    }

    @Test
    void entradaInvalida_null() {
        assertNull(s.converter("-5", true));
        assertNull(s.converter("5.85", true));
        assertNull(s.converter(null, true));
    }

    @Test
    void enLetras_conLosDecimalesDeLaMoneda() {
        assertEquals("CINCO CON 85/100", s.enLetras(new BigDecimal("5.85"), 2, true));
        assertEquals("CIENTO VEINTE Y TRES CON 05/100", s.enLetras(new BigDecimal("123.049"), 2, true));
        assertEquals("TREINTA Y CINCO MIL SEISCIENTOS OCHENTA Y CINCO ", s.enLetras(new BigDecimal("35685"), 0, true));
        assertEquals("CERO CON 50/100", s.enLetras(new BigDecimal("0.5"), 2, true));
    }
}
