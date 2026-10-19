package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.ConfiguracionCobro;
import com.franco.dev.repository.financiero.ConfiguracionCobroRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * El cobro lee esta configuracion de la filial: sin fila, o con un dato faltante, tiene que quedar
 * con tope del 20% (lo que hacia el boton antes de la tabla), nunca sin tope.
 */
public class ConfiguracionCobroServiceTest {

    private ConfiguracionCobroRepository repository;
    private ConfiguracionCobroService service;

    @BeforeEach
    public void setUp() {
        repository = mock(ConfiguracionCobroRepository.class);
        service = new ConfiguracionCobroService(repository);
    }

    @Test
    public void sinFilaDevuelveTopeDel20SinGuardar() {
        when(repository.findAllByOrderByIdAsc()).thenReturn(Collections.emptyList());

        ConfiguracionCobro c = service.findOrDefault();

        assertTrue(c.getAumentoConTope());
        assertEquals(0, new BigDecimal("20").compareTo(c.getAumentoTopePorcentaje()));
        verify(repository, never()).save(any());
    }

    @Test
    public void conFilaDevuelveLaDeMenorId() {
        ConfiguracionCobro primera = fila(1L, false, new BigDecimal("35"));
        ConfiguracionCobro segunda = fila(2L, true, new BigDecimal("10"));
        when(repository.findAllByOrderByIdAsc()).thenReturn(Arrays.asList(primera, segunda));

        ConfiguracionCobro c = service.findOrDefault();

        assertEquals(Long.valueOf(1L), c.getId());
        assertFalse(c.getAumentoConTope());
        assertEquals(0, new BigDecimal("35").compareTo(c.getAumentoTopePorcentaje()));
    }

    @Test
    public void filaReplicadaConNullSeLeeComoTopeDel20() {
        when(repository.findAllByOrderByIdAsc()).thenReturn(Collections.singletonList(fila(1L, null, null)));

        ConfiguracionCobro c = service.findOrDefault();

        assertTrue(c.getAumentoConTope());
        assertEquals(0, new BigDecimal("20").compareTo(c.getAumentoTopePorcentaje()));
    }

    @Test
    public void porcentajeNoPositivoCaeAlDefaultYNoASinTope() {
        assertEquals(0, new BigDecimal("20").compareTo(fila(1L, true, BigDecimal.ZERO).getAumentoTopePorcentaje()));
        assertEquals(0, new BigDecimal("20").compareTo(fila(1L, true, new BigDecimal("-5")).getAumentoTopePorcentaje()));
    }

    private static ConfiguracionCobro fila(Long id, Boolean conTope, BigDecimal pct) {
        ConfiguracionCobro c = new ConfiguracionCobro();
        c.setId(id);
        c.setAumentoConTope(conTope);
        c.setAumentoTopePorcentaje(pct);
        return c;
    }
}
