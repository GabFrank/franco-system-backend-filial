package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.VentaTarjeta;
import com.franco.dev.repository.financiero.VentaTarjetaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VentaTarjetaServiceTest {

    private VentaTarjetaRepository repository;

    private VentaTarjetaService service;

    @BeforeEach
    void setUp() {
        repository = mock(VentaTarjetaRepository.class);
        service = new VentaTarjetaService(repository);
    }

    @Test
    void marcarNoCompletadas_pasaPendientesANoCompletadoYDevuelveCantidad() {
        VentaTarjeta vt1 = new VentaTarjeta();
        vt1.setEstado("PENDIENTE");
        VentaTarjeta vt2 = new VentaTarjeta();
        vt2.setEstado("PENDIENTE");
        when(repository.findByCajaIdAndSucursalIdAndEstado(10L, 1L, "PENDIENTE"))
                .thenReturn(Arrays.asList(vt1, vt2));

        int cantidad = service.marcarNoCompletadas(10L, 1L);

        assertEquals(2, cantidad);
        assertEquals("NO_COMPLETADO", vt1.getEstado());
        assertEquals("NO_COMPLETADO", vt2.getEstado());
        verify(repository, times(2)).save(any(VentaTarjeta.class));
    }

    @Test
    void marcarNoCompletadas_sinPendientesDevuelveCeroYNoGuarda() {
        when(repository.findByCajaIdAndSucursalIdAndEstado(10L, 1L, "PENDIENTE"))
                .thenReturn(Collections.emptyList());

        assertEquals(0, service.marcarNoCompletadas(10L, 1L));
        verify(repository, never()).save(any());
    }
}
