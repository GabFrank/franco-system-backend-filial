package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.CobroDetalle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PdvCajaDescuentoMonedaTest {

    @Test
    void descuentoEnDolares_seLlevaALaMonedaDeLaEmpresa() {
        CobroDetalle cd = linea(moneda("DOLAR", false), 0.5, 6100.0, new BigDecimal("6100.5"));
        assertEquals(3050.25, PdvCajaService.descuentoEnOtraMoneda(cd), 1e-9);
        cd.setCotizacion(null);
        assertEquals(3050.0, PdvCajaService.descuentoEnOtraMoneda(cd), 1e-9);
    }

    @Test
    void descuentoEnLaMonedaDeLaEmpresa_oNoDescuento_sigueComoSiempre() {
        assertNull(PdvCajaService.descuentoEnOtraMoneda(linea(moneda("GUARANI", true), 1000.0, 1.0, null)));
        assertNull(PdvCajaService.descuentoEnOtraMoneda(linea(moneda("GUARANI", null), 1000.0, null, null)));
        CobroDetalle pago = linea(moneda("DOLAR", false), 5.0, 6100.0, null);
        pago.setDescuento(false);
        assertNull(PdvCajaService.descuentoEnOtraMoneda(pago));
    }

    private static CobroDetalle linea(Moneda m, Double valor, Double cambio, BigDecimal cotizacion) {
        CobroDetalle cd = new CobroDetalle();
        cd.setMoneda(m);
        cd.setValor(valor);
        cd.setCambio(cambio);
        cd.setCotizacion(cotizacion);
        cd.setDescuento(true);
        return cd;
    }

    private static Moneda moneda(String denominacion, Boolean principal) {
        Moneda m = new Moneda();
        m.setDenominacion(denominacion);
        m.setPrincipal(principal);
        return m;
    }
}
