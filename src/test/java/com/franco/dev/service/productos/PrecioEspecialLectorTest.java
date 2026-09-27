package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.service.productos.PrecioEspecialFuente.Fila;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.time.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PrecioEspecialLectorTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 10);
    private PrecioEspecialFuente fuente;
    private Environment env;
    private PrecioEspecialLector lector;

    /** Reloj fijo a una hora dada de HOY en zona Paraguay. */
    private PrecioEspecialLector lectorA(int hora, int minuto) {
        Instant instante = HOY.atTime(hora, minuto).atOffset(ZoneOffset.ofHours(-3)).toInstant();
        return new PrecioEspecialLector(fuente, env, Clock.fixed(instante, ZoneOffset.UTC));
    }

    @BeforeEach
    public void setUp() {
        fuente = mock(PrecioEspecialFuente.class);
        env = mock(Environment.class);
        when(env.getProperty("sucursalId")).thenReturn("7");
        when(env.getProperty("precio.especial.habilitado", "true")).thenReturn("true");
        lector = lectorA(12, 0);
    }

    private static PrecioPorSucursal precio(Long id, double valor, boolean activo) {
        PrecioPorSucursal p = new PrecioPorSucursal();
        p.setId(id);
        p.setPrecio(valor);
        p.setActivo(activo);
        p.setPrincipal(true);
        return p;
    }

    private static Fila fila(Long id, Long precioId, Long presentacionId, double valor, LocalDate desde, LocalDate hasta, Boolean activo) {
        return new Fila(id, precioId, presentacionId, valor, desde, hasta, activo);
    }

    private void porPrecios(Fila... filas) {
        when(fuente.porPrecios(eq(7L), anyCollection())).thenReturn(Arrays.asList(filas));
    }

    @Test
    public void sinEspecialesDevuelveLosMismosObjetos() {
        PrecioPorSucursal p = precio(10L, 6000, true);
        porPrecios();
        assertSame(p, lector.aplicar(Collections.singletonList(p)).get(0));
    }

    @Test
    public void especialVigenteDevuelveCopiaConPrecioYActivoSinTocarElOriginal() {
        PrecioPorSucursal p = precio(10L, 6000, false);
        porPrecios(fila(1L, 10L, 50L, 5000, null, null, true));
        PrecioPorSucursal r = lector.aplicar(Collections.singletonList(p)).get(0);
        assertNotSame(p, r);
        assertEquals(10L, r.getId().longValue());
        assertEquals(5000.0, r.getPrecio().doubleValue());
        assertTrue(r.getActivo());
        assertTrue(r.getPrincipal());
        assertEquals(6000.0, p.getPrecio().doubleValue(), "el original no se toca: OSIV lo flushearia");
        assertFalse(p.getActivo());
    }

    @Test
    public void losExtremosDeLaVigenciaSonInclusivos() {
        assertTrue(PrecioEspecialLector.esVigente(fila(1L, 10L, 50L, 5000, HOY, HOY, true), HOY));
        assertFalse(PrecioEspecialLector.esVigente(fila(1L, 10L, 50L, 5000, HOY.plusDays(1), null, true), HOY));
        assertFalse(PrecioEspecialLector.esVigente(fila(1L, 10L, 50L, 5000, null, HOY.minusDays(1), true), HOY));
    }

    @Test
    public void elDiaSeCortaALasCeroHorasDeParaguayNoDeLaJvm() {
        porPrecios(fila(1L, 10L, 50L, 5000, null, HOY, true));
        assertEquals(5000.0, lectorA(23, 59).aplicar(Collections.singletonList(precio(10L, 6000, true))).get(0).getPrecio().doubleValue());
        // 00:30 del dia siguiente en Paraguay (03:30 UTC): ya no aplica
        Instant manana = HOY.plusDays(1).atTime(0, 30).atOffset(ZoneOffset.ofHours(-3)).toInstant();
        PrecioEspecialLector l = new PrecioEspecialLector(fuente, env, Clock.fixed(manana, ZoneOffset.UTC));
        assertEquals(6000.0, l.aplicar(Collections.singletonList(precio(10L, 6000, true))).get(0).getPrecio().doubleValue());
    }

    @Test
    public void cortadoActivoNuloOPrecioNoPositivoNoAplica() {
        assertFalse(PrecioEspecialLector.esVigente(fila(1L, 10L, 50L, 5000, null, null, false), HOY));
        assertFalse(PrecioEspecialLector.esVigente(fila(1L, 10L, 50L, 5000, null, null, null), HOY));
        assertFalse(PrecioEspecialLector.esVigente(fila(1L, 10L, 50L, 0, null, null, true), HOY));
        assertFalse(PrecioEspecialLector.esVigente(new Fila(1L, 10L, 50L, null, null, null, true), HOY));
    }

    @Test
    public void conDosVigentesGanaElDeMenorId() {
        porPrecios(fila(9L, 10L, 50L, 4000, null, null, true), fila(3L, 10L, 50L, 5000, null, null, true));
        assertEquals(5000.0, lector.aplicar(Collections.singletonList(precio(10L, 6000, true))).get(0).getPrecio().doubleValue());
    }

    @Test
    public void consultaSoloLaSucursalPropia() {
        porPrecios();
        lector.aplicar(Collections.singletonList(precio(10L, 6000, true)));
        verify(fuente).porPrecios(eq(7L), eq(Collections.singletonList(10L)));
    }

    @Test
    public void sinSucursalIdOConElInterruptorApagadoNoConsulta() {
        PrecioPorSucursal p = precio(10L, 6000, true);
        when(env.getProperty("sucursalId")).thenReturn(null);
        assertSame(p, lector.aplicar(Collections.singletonList(p)).get(0));
        when(env.getProperty("sucursalId")).thenReturn("7");
        when(env.getProperty("precio.especial.habilitado", "true")).thenReturn("false");
        assertSame(p, lector.aplicar(Collections.singletonList(p)).get(0));
        verifyZeroInteractions(fuente);   // Boot 2.1 trae Mockito 2: no hay verifyNoInteractions
    }

    @Test
    public void siLaLecturaFallaDevuelveLosPreciosOriginales() {
        when(fuente.porPrecios(anyLong(), anyCollection())).thenThrow(new RuntimeException("relation does not exist"));
        PrecioPorSucursal p = precio(10L, 6000, true);
        assertSame(p, lector.aplicar(Collections.singletonList(p)).get(0));
    }

    @Test
    public void presentacionInactivaConEspecialVigenteSaleActivaEnCopiaYLasDemasNoSeTocan() {
        Presentacion dosPorUno = new Presentacion();
        dosPorUno.setId(50L);
        dosPorUno.setActivo(false);
        dosPorUno.setCantidad(2.0);
        Presentacion otraInactiva = new Presentacion();
        otraInactiva.setId(51L);
        otraInactiva.setActivo(false);
        Presentacion activa = new Presentacion();
        activa.setId(52L);
        activa.setActivo(true);
        when(fuente.porPresentaciones(eq(7L), anyCollection()))
                .thenReturn(Collections.singletonList(fila(1L, 20L, 50L, 6000, null, null, true)));
        List<Presentacion> r = lector.habilitarPresentaciones(Arrays.asList(dosPorUno, otraInactiva, activa));
        assertNotSame(dosPorUno, r.get(0));
        assertTrue(r.get(0).getActivo());
        assertEquals(2.0, r.get(0).getCantidad().doubleValue());
        assertFalse(dosPorUno.getActivo());
        assertSame(otraInactiva, r.get(1));
        assertSame(activa, r.get(2));
        // una sola consulta, solo con las inactivas
        verify(fuente).porPresentaciones(eq(7L), eq(Arrays.asList(50L, 51L)));
    }
}
