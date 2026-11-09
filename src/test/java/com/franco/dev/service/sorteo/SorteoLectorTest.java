package com.franco.dev.service.sorteo;

import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import com.franco.dev.service.financiero.FormaPagoService;
import com.franco.dev.service.sorteo.SorteoFuente.Premio;
import com.franco.dev.service.sorteo.SorteoFuente.Sorteo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

public class SorteoLectorTest {

    private SorteoFuente fuente;
    private FormaPagoService formaPagoService;
    private Environment env;

    @BeforeEach
    public void setUp() {
        fuente = mock(SorteoFuente.class);
        formaPagoService = mock(FormaPagoService.class);
        env = mock(Environment.class);
        when(env.getProperty("sucursalId")).thenReturn("7");
        when(env.getProperty("sorteo.habilitado", "true")).thenReturn("true");
        FormaPago tarjeta = new FormaPago();
        tarjeta.setId(5L);
        tarjeta.setDescripcion("TARJETA");
        FormaPago efectivo = new FormaPago();
        efectivo.setId(1L);
        efectivo.setDescripcion("EFECTIVO");
        when(formaPagoService.findAll2()).thenReturn(Arrays.asList(tarjeta, efectivo));
    }

    private SorteoLector lectorEn(String instanteUtc) {
        return new SorteoLector(fuente, formaPagoService, env, Clock.fixed(Instant.parse(instanteUtc), ZoneOffset.UTC));
    }

    private static Sorteo sorteo(LocalDate desde, LocalDate hasta, boolean incluyeTarjeta) {
        return new Sorteo(3L, "Moto", "Gran sorteo", 100000.0, "POR_MULTIPLO", null, null, incluyeTarjeta,
                desde, hasta, LocalDate.of(2026, 11, 15), true, true,
                Collections.singletonList(new Premio("Moto 110cc", 1)));
    }

    private static CobroDetalleInput pago(long fp, double valor) {
        CobroDetalleInput d = new CobroDetalleInput();
        d.setPago(true);
        d.setFormaPagoId(fp);
        d.setValor(valor);
        return d;
    }

    @Test
    public void aplicaConTicketsYPremiosEnTexto() {
        when(fuente.vigentesCandidatos(7L)).thenReturn(Collections.singletonList(sorteo(null, null, true)));
        List<SorteoAplicable> r = lectorEn("2026-10-20T15:00:00Z").aplicables(250000, Collections.emptyList());
        assertEquals(1, r.size());
        assertEquals(2, r.get(0).getCantidadTickets().intValue());
        assertEquals(3L, r.get(0).getSorteoId().longValue());
        assertEquals(Collections.singletonList("1 x Moto 110cc"), r.get(0).getPremios());
    }

    @Test
    public void fueraDeVigenciaNoAplica() {
        when(fuente.vigentesCandidatos(7L)).thenReturn(Collections.singletonList(
                sorteo(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10), true)));
        assertTrue(lectorEn("2026-10-20T15:00:00Z").aplicables(250000, Collections.emptyList()).isEmpty());
        when(fuente.vigentesCandidatos(7L)).thenReturn(Collections.singletonList(
                sorteo(LocalDate.of(2026, 10, 25), null, true)));
        assertTrue(lectorEn("2026-10-20T15:00:00Z").aplicables(250000, Collections.emptyList()).isEmpty());
    }

    @Test
    public void ultimoDiaA2330Menos03Aplica() {
        when(fuente.vigentesCandidatos(7L)).thenReturn(Collections.singletonList(
                sorteo(null, LocalDate.of(2026, 10, 31), true)));
        // 23:30 en -03 del 31/10 = 02:30Z del 01/11
        assertEquals(1, lectorEn("2026-11-01T02:30:00Z").aplicables(250000, Collections.emptyList()).size());
        // 00:30 en -03 del 01/11: ya no
        assertTrue(lectorEn("2026-11-01T03:30:00Z").aplicables(250000, Collections.emptyList()).isEmpty());
    }

    @Test
    public void desdeYHastaNullSonAbiertos() {
        when(fuente.vigentesCandidatos(7L)).thenReturn(Collections.singletonList(sorteo(null, null, true)));
        assertEquals(1, lectorEn("2030-01-01T12:00:00Z").aplicables(100000, Collections.emptyList()).size());
    }

    @Test
    public void sinTarjetaDescuentaLoPagadoConTarjeta() {
        when(fuente.vigentesCandidatos(7L)).thenReturn(Collections.singletonList(sorteo(null, null, false)));
        List<CobroDetalleInput> pagos = Arrays.asList(pago(5L, 150000), pago(1L, 50000));
        assertTrue(lectorEn("2026-10-20T15:00:00Z").aplicables(200000, pagos).isEmpty());
    }

    @Test
    public void deshabilitadoDevuelveVacio() {
        when(env.getProperty("sorteo.habilitado", "true")).thenReturn("false");
        assertTrue(lectorEn("2026-10-20T15:00:00Z").aplicables(250000, Collections.emptyList()).isEmpty());
        verifyNoInteractions(fuente);
    }

    @Test
    public void sinSucursalIdDevuelveVacio() {
        when(env.getProperty("sucursalId")).thenReturn(null);
        assertTrue(lectorEn("2026-10-20T15:00:00Z").aplicables(250000, Collections.emptyList()).isEmpty());
        verifyNoInteractions(fuente);
    }

    @Test
    public void excepcionDeFuenteDevuelveVacio() {
        when(fuente.vigentesCandidatos(anyLong())).thenThrow(new RuntimeException("relation does not exist"));
        assertTrue(lectorEn("2026-10-20T15:00:00Z").aplicables(250000, Collections.emptyList()).isEmpty());
    }

    @Test
    public void siFallaLaBusquedaDeFormasDePagoSeTrataComoSinTarjetas() {
        when(formaPagoService.findAll2()).thenThrow(new RuntimeException("boom"));
        when(fuente.vigentesCandidatos(7L)).thenReturn(Collections.singletonList(sorteo(null, null, false)));
        List<CobroDetalleInput> pagos = Arrays.asList(pago(5L, 150000), pago(1L, 50000));
        assertEquals(1, lectorEn("2026-10-20T15:00:00Z").aplicables(200000, pagos).size());
    }
}
