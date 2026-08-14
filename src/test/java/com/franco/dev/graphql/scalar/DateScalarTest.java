package com.franco.dev.graphql.scalar;

import graphql.schema.CoercingSerializeException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * El scalar Date tiene que poder serializar los dos tipos que salen por la API: LocalDateTime
 * (la mayoria de las entidades) y LocalDate (fechas del maestro de lotes: vencimiento y retiro).
 */
class DateScalarTest {

    private final DateScalar scalar = new DateScalar();

    @Test
    void serializaLocalDateTime() {
        Object salida = scalar.getCoercing().serialize(LocalDateTime.of(2026, 11, 13, 16, 25, 22));

        assertEquals("2026-11-13T16:25:22", salida);
    }

    @Test
    void serializaLocalDateAlInicioDelDia() {
        Object salida = scalar.getCoercing().serialize(LocalDate.of(2026, 11, 13));

        assertEquals("2026-11-13T00:00:00", salida);
    }

    @Test
    void rechazaLoQueNoEsFecha() {
        assertThrows(CoercingSerializeException.class, () -> scalar.getCoercing().serialize("cualquier cosa"));
    }
}
