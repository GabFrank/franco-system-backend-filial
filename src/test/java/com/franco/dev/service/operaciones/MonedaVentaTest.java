package com.franco.dev.service.operaciones;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import com.franco.dev.graphql.operaciones.input.CobroInput;
import com.franco.dev.graphql.operaciones.input.VentaInput;
import com.franco.dev.graphql.operaciones.input.VentaItemInput;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fija la regla del equivalente de la venta en la moneda del PDV (V111.5) y que el ModelMapper de los
 * resolvers lleve las columnas nuevas sin confundirlas con las relaciones de siempre.
 */
class MonedaVentaTest {

    @Test
    void equivalente_unSoloRedondeoALosDecimalesDeLaEmpresa() {
        assertEquals(new BigDecimal("35685"), MonedaVenta.equivalente(new BigDecimal("5.85"), new BigDecimal("6100"), 0));
        assertEquals(new BigDecimal("35820"), MonedaVenta.equivalente(new BigDecimal("5.85"), new BigDecimal("6123"), 0));
        assertNull(MonedaVenta.equivalente(new BigDecimal("5.85"), BigDecimal.ZERO, 0));
        assertNull(MonedaVenta.equivalente(null, new BigDecimal("6100"), 0));
    }

    @Test
    void coincide_conUnaUnidadPorItemDeTolerancia() {
        BigDecimal calculado = new BigDecimal("35685");
        assertTrue(MonedaVenta.coincide(35686.0, calculado, 0, 1));
        assertFalse(MonedaVenta.coincide(35687.0, calculado, 0, 1));
        assertTrue(MonedaVenta.coincide(35688.0, calculado, 0, 3));
        assertTrue(MonedaVenta.coincide(null, calculado, 0, 1));
    }

    @Test
    void esOtraMoneda_soloConLasDosConocidasYDistintas() {
        Moneda gs = moneda(1L, 0), usd = moneda(3L, 2);
        assertTrue(MonedaVenta.esOtraMoneda(usd, gs));
        assertFalse(MonedaVenta.esOtraMoneda(gs, gs));
        assertFalse(MonedaVenta.esOtraMoneda(null, gs));
        assertFalse(MonedaVenta.esOtraMoneda(usd, null));
        assertEquals(2, MonedaVenta.decimales(usd));
        assertEquals(0, MonedaVenta.decimales(null));
    }

    @Test
    void modelMapper_llevaLasColumnasNuevas() {
        ModelMapper m = new ModelMapper();
        VentaInput vi = new VentaInput();
        vi.setTotalGs(35685.0);
        vi.setMonedaId(3L);
        vi.setCotizacion(6100.0);
        vi.setTotalMoneda(5.85);
        Venta v = m.map(vi, Venta.class);
        assertEquals(0, new BigDecimal("6100").compareTo(v.getCotizacion()));
        assertEquals(0, new BigDecimal("5.85").compareTo(v.getTotalMoneda()));

        VentaItemInput ii = new VentaItemInput();
        ii.setPrecio(35685.0);
        ii.setPrecioEnMoneda(5.85);
        ii.setPrecioMonedaId(3L);
        VentaItem item = new ModelMapper().map(ii, VentaItem.class);
        assertEquals(35685.0, item.getPrecio());

        CobroInput ci = new CobroInput();
        ci.setTotalGs(35685.0);
        ci.setTotalMoneda(5.85);
        Cobro c = new ModelMapper().map(ci, Cobro.class);
        assertEquals(0, new BigDecimal("5.85").compareTo(c.getTotalMoneda()));

        CobroDetalleInput di = new CobroDetalleInput();
        di.setMonedaId(3L);
        di.setCambio(6100.0);
        di.setCotizacion(6100.25);
        di.setValor(5.85);
        CobroDetalle d = new ModelMapper().map(di, CobroDetalle.class);
        assertEquals(0, new BigDecimal("6100.25").compareTo(d.getCotizacion()));
        assertEquals(6100.0, d.getCambio());
    }

    private static Moneda moneda(Long id, int decimales) {
        Moneda m = new Moneda();
        m.setId(id);
        m.setDecimales(decimales);
        return m;
    }
}
