package com.franco.dev.service.operaciones;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TicketMonedaVentaTest {

    @Test
    void sinMonedaNoAplica_elTicketSaleComoSiempre() {
        assertFalse(TicketMonedaVenta.aplica(new Venta()));
        assertFalse(TicketMonedaVenta.aplica(null));
    }

    @Test
    void itemEnLaMonedaDeLaVenta_netoDelDescuentoQueViajaEnLaDeLaEmpresa() {
        Venta v = ventaUsd();
        VentaItem vi = new VentaItem();
        vi.setPrecio(35685.0);
        vi.setPrecioEnMoneda(new BigDecimal("5.85"));
        vi.setValorDescuento(610.0);
        vi.setCantidad(3.0);
        assertEquals(new BigDecimal("5.75"), TicketMonedaVenta.unitario(vi, v));
        assertEquals(new BigDecimal("17.25"), TicketMonedaVenta.totalItem(vi, v));
        assertEquals("5,75", TicketMonedaVenta.formato(TicketMonedaVenta.unitario(vi, v), 2));
    }

    @Test
    void itemSinPrecioEnMoneda_seConvierteElDeSiempre() {
        VentaItem vi = new VentaItem();
        vi.setPrecio(12200.0);
        vi.setValorDescuento(0.0);
        vi.setCantidad(1.0);
        assertEquals(new BigDecimal("2.00"), TicketMonedaVenta.unitario(vi, ventaUsd()));
    }

    @Test
    void totalFinal_elDelCobroSiLoTrae() {
        Venta v = ventaUsd();
        Cobro c = new Cobro();
        c.setTotalMoneda(new BigDecimal("17.25"));
        assertEquals(new BigDecimal("17.25"), TicketMonedaVenta.totalFinal(v, c, 999999));
        assertEquals(new BigDecimal("5.85"), TicketMonedaVenta.totalFinal(v, new Cobro(), 35685));
        assertEquals("Moneda: DOLAR  Cotiz.: 6.100", TicketMonedaVenta.lineaMoneda(v));
    }

    private static Venta ventaUsd() {
        Moneda usd = new Moneda();
        usd.setId(3L);
        usd.setDenominacion("DOLAR");
        usd.setDecimales(2);
        Venta v = new Venta();
        v.setMoneda(usd);
        v.setCotizacion(new BigDecimal("6100"));
        return v;
    }
}
