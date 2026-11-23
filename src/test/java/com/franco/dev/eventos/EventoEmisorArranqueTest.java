package com.franco.dev.eventos;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Arranque (#587): antes de ApplicationReadyEvent el emisor ya tiene que estar encendido si hay esquema. */
class EventoEmisorArranqueTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AtomicLong ahora = new AtomicLong(1_000_000);

    private EventoEmisor emisor(boolean enabled) {
        EventosProperties props = new EventosProperties();
        props.setEnabled(enabled);
        EventoEmisor e = new EventoEmisor(jdbc, props, new ObjectMapper(), mock(PlatformTransactionManager.class), 1L);
        e.reloj = ahora::get;
        return e;
    }

    @SuppressWarnings("unchecked")
    private void esquema(Integer... respuestas) {
        when(jdbc.execute(any(ConnectionCallback.class))).thenReturn(respuestas[0],
                java.util.Arrays.copyOfRange(respuestas, 1, respuestas.length));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sinListenerDeArranqueYConEsquemaQuedaEncendido() {
        esquema(1);
        EventoEmisor e = emisor(true);
        assertTrue(e.encendido());
        assertTrue(e.encendido());
        verify(jdbc, times(1)).execute(any(ConnectionCallback.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void apagadoNuncaConsulta() {
        EventoEmisor e = emisor(false);
        assertFalse(e.encendido());
        verify(jdbc, never()).execute(any(ConnectionCallback.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sinEsquemaConsultaUnaVezCada30sYSePrendeCuandoAparece() {
        esquema(0, 1);
        EventoEmisor e = emisor(true);
        assertFalse(e.encendido());
        ahora.addAndGet(29_000);
        assertFalse(e.encendido());
        verify(jdbc, times(1)).execute(any(ConnectionCallback.class));
        ahora.addAndGet(2_000);
        assertTrue(e.encendido());
        verify(jdbc, times(2)).execute(any(ConnectionCallback.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void errorTransitorioNoRompeNiPausa() {
        when(jdbc.execute(any(ConnectionCallback.class)))
                .thenThrow(new org.springframework.dao.QueryTimeoutException("lenta"))
                .thenReturn(1);
        EventoEmisor e = emisor(true);
        assertFalse(e.encendido());
        assertTrue(e.encendido());
        verify(jdbc, times(2)).execute(any(ConnectionCallback.class));
    }
}
