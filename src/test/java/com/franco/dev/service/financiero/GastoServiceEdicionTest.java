package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.repository.financiero.GastoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Alta y edicion de un gasto guardan solo el gasto: movimiento_caja ya no se escribe (issue #80). Con el codigo
 * anterior el alta y la edicion escribian o reescribian sus filas, y el servicio dependia de MonedaService.
 */
class GastoServiceEdicionTest {

    @Mock private GastoRepository repository;
    @InjectMocks private GastoService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        when(repository.save(any(Gasto.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void elAltaGuardaSoloElGasto() {
        Gasto g = new Gasto();
        g.setSucursalId(24L);
        g.setRetiroGs(10_000.0);
        g.setFinalizado(true);

        assertSame(g, service.saveAndSend(g, false));
        verify(repository).save(g);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void laEdicionGuardaSoloElGasto() {
        Gasto g = new Gasto();
        g.setId(5L);
        g.setSucursalId(24L);
        g.setRetiroDs(10.0);
        g.setVueltoDs(3.0);

        assertSame(g, service.saveAndSend(g, false));
        verify(repository).save(g);
        verifyNoMoreInteractions(repository);
    }
}
