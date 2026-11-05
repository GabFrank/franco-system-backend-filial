package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MovimientoCaja;
import com.franco.dev.domain.financiero.enums.PdvCajaTipoMovimiento;
import com.franco.dev.repository.financiero.GastoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Editar un gasto recalcula sus movimientos de caja con el vuelto de la misma moneda, y nulo cuenta 0. */
class GastoServiceEdicionTest {

    @Mock private GastoRepository repository;
    @Mock private MonedaService monedaService;
    @Mock private MovimientoCajaService movimientoCajaService;
    @Mock private CambioService cambioService;
    @InjectMocks private GastoService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        when(repository.save(any(Gasto.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static MovimientoCaja movimientoEn(String denominacion) {
        Moneda m = new Moneda();
        m.setDenominacion(denominacion);
        MovimientoCaja mc = new MovimientoCaja();
        mc.setMoneda(m);
        return mc;
    }

    @Test
    void enDolaresUsaElVueltoEnDolaresYLosNulosCuentanCero() {
        Gasto g = new Gasto();
        g.setId(5L);
        g.setSucursalId(24L);
        g.setRetiroDs(10.0);
        g.setVueltoDs(3.0);
        g.setVueltoRs(99.0); // antes se sumaba este a la moneda US$
        MovimientoCaja dolar = movimientoEn("DOLAR");
        MovimientoCaja guarani = movimientoEn("GUARANI"); // retiro y vuelto en Gs nulos
        when(movimientoCajaService.findByTipoMovimientoAndReferencia(PdvCajaTipoMovimiento.GASTO, 5L))
                .thenReturn(Arrays.asList(dolar, guarani));

        service.saveAndSend(g, false);

        assertEquals(13.0, dolar.getCantidad());
        assertEquals(0.0, guarani.getCantidad());
    }
}
