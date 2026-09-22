package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.ConfiguracionFacturacion;
import com.franco.dev.repository.financiero.ConfiguracionFacturacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Fija la resolucion de la politica: sucursal propia → global → property, y que ninguna fila rara
 * ni ninguna falla de lectura endurezca o frene la facturacion.
 */
public class ConfiguracionFacturacionLectorTest {

    private static final LocalDateTime AYER = LocalDateTime.of(2026, 9, 21, 10, 0);
    private static final LocalDateTime HOY = LocalDateTime.of(2026, 9, 22, 10, 0);

    private ConfiguracionFacturacionRepository repository;
    private Environment env;
    private ConfiguracionFacturacionLector lector;

    @BeforeEach
    public void setUp() {
        repository = mock(ConfiguracionFacturacionRepository.class);
        env = mock(Environment.class);
        when(env.getProperty("sucursalId")).thenReturn("7");
        when(env.getProperty("facturaCountDown")).thenReturn("3");
        lector = new ConfiguracionFacturacionLector(repository, env);
    }

    private static ConfiguracionFacturacion fila(Long id, Long sucursalId, String modo, Integer n, Boolean respeta,
                                                 LocalDateTime modificado) {
        ConfiguracionFacturacion c = new ConfiguracionFacturacion();
        c.setId(id);
        c.setSucursalId(sucursalId);
        c.setModo(modo);
        c.setVentasSinFactura(n);
        c.setVentaTicketRespetaPolitica(respeta);
        c.setModificadoEn(modificado);
        return c;
    }

    private void conFilas(ConfiguracionFacturacion... filas) {
        when(repository.findAllByOrderByModificadoEnDescIdDesc()).thenReturn(Arrays.asList(filas));
    }

    @Test
    public void sinFilasUsaLaProperty() {
        when(repository.findAllByOrderByModificadoEnDescIdDesc()).thenReturn(Collections.emptyList());
        PoliticaFacturacion p = lector.resolver();
        assertEquals(PoliticaFacturacion.Origen.PROPERTY, p.getOrigen());
        assertEquals(ConfiguracionFacturacion.MODO_INTERVALO, p.getModo());
        assertEquals(3, p.getVentasSinFactura());
        assertFalse(p.isVentaTicketRespetaPolitica());
    }

    @Test
    public void laFilaDeLaSucursalLeGanaALaGlobal() {
        conFilas(fila(1L, null, "TODAS", 0, true, HOY),
                fila(2L, 7L, "A_PEDIDO", 0, false, AYER),
                fila(3L, 9L, "INTERVALO", 5, true, HOY));
        PoliticaFacturacion p = lector.resolver();
        assertEquals(PoliticaFacturacion.Origen.SUCURSAL, p.getOrigen());
        assertEquals(ConfiguracionFacturacion.MODO_A_PEDIDO, p.getModo());
    }

    @Test
    public void sinFilaPropiaUsaLaGlobalYNoLaDeOtraSucursal() {
        conFilas(fila(1L, null, "TODAS", 0, true, AYER), fila(3L, 9L, "A_PEDIDO", 0, false, HOY));
        PoliticaFacturacion p = lector.resolver();
        assertEquals(PoliticaFacturacion.Origen.GLOBAL, p.getOrigen());
        assertEquals(ConfiguracionFacturacion.MODO_TODAS, p.getModo());
        assertTrue(p.isVentaTicketRespetaPolitica());
    }

    @Test
    public void conFilasDuplicadasGanaLaModificadaMasRecientemente() {
        conFilas(fila(5L, 7L, "A_PEDIDO", 0, false, AYER),
                fila(4L, 7L, "TODAS", 0, false, HOY),
                fila(6L, 7L, "INTERVALO", 1, false, null));
        assertEquals(ConfiguracionFacturacion.MODO_TODAS, lector.resolver().getModo());
    }

    @Test
    public void unModoDesconocidoONuloSeIgnoraYSigueConLaSiguiente() {
        conFilas(fila(1L, 7L, "LO_QUE_SEA", 0, true, HOY),
                fila(2L, 7L, null, 0, true, HOY),
                fila(3L, null, "a_pedido", 0, true, AYER));
        PoliticaFacturacion p = lector.resolver();
        assertEquals(PoliticaFacturacion.Origen.GLOBAL, p.getOrigen());
        assertEquals(ConfiguracionFacturacion.MODO_A_PEDIDO, p.getModo());
    }

    @Test
    public void intervaloNuloONegativoCaeALaPropertyYRespetaNuloEsFalse() {
        conFilas(fila(1L, 7L, "INTERVALO", null, null, HOY));
        PoliticaFacturacion p = lector.resolver();
        assertEquals(3, p.getVentasSinFactura());
        assertFalse(p.isVentaTicketRespetaPolitica());

        conFilas(fila(1L, 7L, "INTERVALO", -4, true, HOY));
        assertEquals(3, lector.resolver().getVentasSinFactura());
    }

    @Test
    public void siLaLecturaFallaUsaLaPropertyYNoPropagaLaExcepcion() {
        when(repository.findAllByOrderByModificadoEnDescIdDesc()).thenThrow(new RuntimeException("relation does not exist"));
        PoliticaFacturacion p = lector.resolver();
        assertEquals(PoliticaFacturacion.Origen.PROPERTY, p.getOrigen());
        assertEquals(3, p.getVentasSinFactura());
    }

    @Test
    public void unaPropertyNegativaSigueApagandoLaFacturacionSilenciosa() {
        // Negativo era la forma historica de apagarla: llevarlo a 0 facturaria CADA venta.
        when(env.getProperty("facturaCountDown")).thenReturn("-1");
        when(repository.findAllByOrderByModificadoEnDescIdDesc()).thenReturn(Collections.emptyList());
        PoliticaFacturacion p = lector.resolver();
        assertEquals(ConfiguracionFacturacion.MODO_A_PEDIDO, p.getModo());
        assertFalse(p.isVentaTicketRespetaPolitica());
    }

    @Test
    public void unaPropertyRotaOAusenteNoFacturaSolaNiFrenaLaCaja() {
        when(repository.findAllByOrderByModificadoEnDescIdDesc()).thenReturn(Collections.emptyList());
        when(env.getProperty("facturaCountDown")).thenReturn("abc");
        assertEquals(ConfiguracionFacturacion.MODO_A_PEDIDO, lector.resolver().getModo());
        when(env.getProperty("facturaCountDown")).thenReturn(null);
        assertEquals(ConfiguracionFacturacion.MODO_A_PEDIDO, lector.resolver().getModo());
    }

    @Test
    public void unaFilaIntervaloSinIntervaloUsableYSinPropertySeIgnora() {
        when(env.getProperty("facturaCountDown")).thenReturn("-1");
        conFilas(fila(1L, 7L, "INTERVALO", null, true, HOY), fila(2L, null, "TODAS", null, false, AYER));
        PoliticaFacturacion p = lector.resolver();
        assertEquals(PoliticaFacturacion.Origen.GLOBAL, p.getOrigen());
        assertEquals(ConfiguracionFacturacion.MODO_TODAS, p.getModo());
    }
}
