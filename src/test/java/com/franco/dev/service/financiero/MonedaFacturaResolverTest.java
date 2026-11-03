package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.operaciones.Venta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MonedaFacturaResolverTest {

    private MonedaModuloLector lector;
    private MonedaService monedaService;
    private CambioService cambioService;
    private MonedaFacturaResolver resolver;
    private Moneda gs, usd, brl;

    @BeforeEach
    void setUp() {
        lector = mock(MonedaModuloLector.class);
        monedaService = mock(MonedaService.class);
        cambioService = mock(CambioService.class);
        resolver = new MonedaFacturaResolver(lector, monedaService, cambioService);
        gs = moneda(1L, "GUARANI", null);
        usd = moneda(3L, "DOLAR", null);
        brl = moneda(2L, "REAL", "BRL");
        when(monedaService.principal()).thenReturn(gs);
        when(lector.configurada(MonedaModuloLector.FACTURACION)).thenReturn(Optional.empty());
    }

    @Test
    void ventaEnLaMonedaDeLaEmpresa_sinConfiguracion_comoSiempre() {
        assertFalse(resolver.para(new Venta()).isPresent());
        verifyNoInteractions(cambioService);
    }

    @Test
    void sinConfiguracion_laFacturaSaleEnLaMonedaDeLaVentaConSuCotizacion() {
        MonedaFacturaResolver.MonedaFactura mf = resolver.para(ventaUsd()).get();
        assertEquals("USD", mf.getCodigoIso());
        assertEquals(6100.0, mf.getTipoCambio());
        verifyNoInteractions(cambioService);
    }

    @Test
    void facturacionConfiguradaEnLaDeLaEmpresa_ganaAunqueLaVentaSeaEnDolares() {
        when(lector.configurada(MonedaModuloLector.FACTURACION)).thenReturn(Optional.of(gs));
        assertFalse(resolver.para(ventaUsd()).isPresent());
    }

    @Test
    void facturacionEnOtraMonedaQueLaVenta_usaLaCotizacionDelComercio() {
        when(lector.configurada(MonedaModuloLector.FACTURACION)).thenReturn(Optional.of(brl));
        when(cambioService.findLastValorEnGsByMonedaIdOrDefault(eq(2L), any())).thenReturn(1250.0);
        MonedaFacturaResolver.MonedaFactura mf = resolver.para(ventaUsd()).get();
        assertEquals("BRL", mf.getCodigoIso());
        assertEquals(1250.0, mf.getTipoCambio());
    }

    @Test
    void sinCotizacion_saleEnLaMonedaDeLaEmpresa_yUnaFallaNoLanza() {
        when(lector.configurada(MonedaModuloLector.FACTURACION)).thenReturn(Optional.of(brl));
        when(cambioService.findLastValorEnGsByMonedaIdOrDefault(eq(2L), any())).thenReturn(null);
        assertFalse(resolver.para(ventaUsd()).isPresent());
        when(monedaService.principal()).thenThrow(new RuntimeException("db"));
        assertFalse(resolver.para(ventaUsd()).isPresent());
    }

    @Test
    void codigoIso_delCampoODeLaDenominacion() {
        assertEquals("USD", CodigoIsoMoneda.de(usd));
        assertEquals("BRL", CodigoIsoMoneda.de(brl));
        assertEquals("PYG", CodigoIsoMoneda.de(gs));
        assertEquals("USD", CodigoIsoMoneda.deDenominacion("usd"));
        assertEquals("ARS", CodigoIsoMoneda.deDenominacion("PESO ARG"));
        assertNull(CodigoIsoMoneda.deDenominacion("YEN JAPONES"));
    }

    private Venta ventaUsd() {
        Venta v = new Venta();
        v.setId(9L);
        v.setMoneda(usd);
        v.setCotizacion(new BigDecimal("6100"));
        return v;
    }

    private static Moneda moneda(Long id, String denominacion, String iso) {
        Moneda m = new Moneda();
        m.setId(id);
        m.setDenominacion(denominacion);
        m.setCodigoIso(iso);
        return m;
    }
}
