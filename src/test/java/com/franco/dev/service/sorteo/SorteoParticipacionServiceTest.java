package com.franco.dev.service.sorteo;

import com.franco.dev.domain.operaciones.Delivery;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import com.franco.dev.graphql.operaciones.input.SorteoClienteInput;
import com.franco.dev.service.impresion.dto.SorteoTicketDto;
import com.franco.dev.service.sorteo.SorteoParticipacionService.Participacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SorteoParticipacionServiceTest {

    private SorteoLector lector;
    private JdbcTemplate jdbc;
    private PlatformTransactionManager txManager;
    private Environment env;
    private SorteoParticipacionService service;
    private final List<CobroDetalleInput> detalles = Collections.emptyList();

    @BeforeEach
    public void setUp() {
        lector = mock(SorteoLector.class);
        jdbc = mock(JdbcTemplate.class);
        txManager = mock(PlatformTransactionManager.class);
        env = mock(Environment.class);
        when(env.getProperty("sucursalId")).thenReturn("7");
        when(txManager.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
        service = new SorteoParticipacionService(lector, jdbc, txManager, env);
    }

    private static Venta venta(Long id, double total) {
        Venta v = new Venta();
        v.setId(id);
        v.setSucursalId(7L);
        v.setTotalGs(total);
        v.setCreadoEn(LocalDateTime.of(2026, 10, 28, 10, 30));
        return v;
    }

    private static SorteoClienteInput cliente() {
        SorteoClienteInput c = new SorteoClienteInput();
        c.setClienteId(15L);
        c.setDocumento("1234567");
        c.setNombre("JUAN PEREZ");
        return c;
    }

    private static SorteoAplicable aplicable(long id, String nombre, int tickets) {
        return new SorteoAplicable(id, nombre, "desc " + nombre, tickets, LocalDate.of(2026, 12, 24),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 20), Arrays.asList("1 x Moto"));
    }

    @Test
    public void sinClienteNoRegistra() {
        List<Participacion> r = service.registrar(venta(100L, 500000), detalles, null, 3L);
        assertTrue(r.isEmpty());
        verifyNoInteractions(lector, jdbc, txManager);
    }

    @Test
    public void deliveryNoRegistra() {
        Venta v = venta(100L, 500000);
        v.setDelivery(new Delivery());
        List<Participacion> r = service.registrar(v, detalles, cliente(), 3L);
        assertTrue(r.isEmpty());
        verifyNoInteractions(lector, jdbc, txManager);
    }

    @Test
    public void ventaSinIdNoRegistra() {
        List<Participacion> r = service.registrar(venta(null, 500000), detalles, cliente(), 3L);
        assertTrue(r.isEmpty());
        verifyNoInteractions(lector, jdbc, txManager);
    }

    @Test
    public void sinAplicablesNoInserta() {
        when(lector.aplicables(anyDouble(), any())).thenReturn(Collections.emptyList());
        List<Participacion> r = service.registrar(venta(100L, 500000), detalles, cliente(), 3L);
        assertTrue(r.isEmpty());
        verify(jdbc, never()).update(anyString(), (Object[]) any());
    }

    @Test
    public void registraUnaFilaPorSorteoConCantidadYCodigo() {
        when(lector.aplicables(500000d, detalles))
                .thenReturn(Arrays.asList(aplicable(4L, "NAVIDAD", 3), aplicable(9L, "VERANO", 1)));
        when(jdbc.queryForList(contains("empresarial.sucursal"), eq(String.class), eq(7L)))
                .thenReturn(Collections.singletonList("CENTRO"));

        List<Participacion> r = service.registrar(venta(100L, 500000), detalles, cliente(), 3L);

        assertEquals(2, r.size());
        assertEquals(4L, r.get(0).getSorteoId());
        assertEquals("NAVIDAD", r.get(0).getNombreSorteo());
        assertEquals(3, r.get(0).getCantidad());
        assertEquals("S4-7-100", r.get(0).getCodigo());
        assertEquals("CENTRO", r.get(0).getSucursal());
        assertEquals("JUAN PEREZ", r.get(0).getClienteNombre());
        assertEquals("1234567", r.get(0).getClienteDocumento());
        assertEquals(500000d, r.get(0).getTotalGs());
        assertEquals("S9-7-100", r.get(1).getCodigo());
        assertEquals(1, r.get(1).getCantidad());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), eq(4L), eq(100L), eq(7L), eq(3), eq(15L), eq("1234567"),
                eq("JUAN PEREZ"), eq("S4-7-100"), eq(3L));
        verify(jdbc).update(anyString(), eq(9L), eq(100L), eq(7L), eq(1), eq(15L), eq("1234567"),
                eq("JUAN PEREZ"), eq("S9-7-100"), eq(3L));
        assertTrue(sql.getValue().contains("ON CONFLICT (sorteo_id, venta_id) DO NOTHING"));

        // Todo lo del sorteo corre en transacciones propias: nunca en la de la venta.
        ArgumentCaptor<TransactionDefinition> defs = ArgumentCaptor.forClass(TransactionDefinition.class);
        // Dos conexiones extra como mucho: una lectura (evaluacion + datos del ticket) y una escritura.
        verify(txManager, times(2)).getTransaction(defs.capture());
        for (TransactionDefinition d : defs.getAllValues()) {
            assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, d.getPropagationBehavior());
        }
    }

    @Test
    public void elLectorSeEvaluaFueraDeLaTransaccionDeLaVenta() {
        when(txManager.getTransaction(any())).thenAnswer(i -> {
            TransactionDefinition d = i.getArgument(0);
            assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, d.getPropagationBehavior());
            return new SimpleTransactionStatus();
        });
        when(lector.aplicables(anyDouble(), any())).thenAnswer(i -> {
            verify(txManager, atLeastOnce()).getTransaction(any());
            return Collections.emptyList();
        });
        service.registrar(venta(100L, 500000), detalles, cliente(), 3L);
        verify(lector).aplicables(500000d, detalles);
    }

    @Test
    public void sinNombreLoBuscaPorCliente() {
        when(lector.aplicables(anyDouble(), any())).thenReturn(Collections.singletonList(aplicable(4L, "N", 1)));
        when(jdbc.queryForList(contains("personas.persona"), eq(String.class), eq(15L)))
                .thenReturn(Collections.singletonList("MARIA GOMEZ"));
        SorteoClienteInput c = cliente();
        c.setNombre("  ");
        List<Participacion> r = service.registrar(venta(100L, 500000), detalles, c, 3L);
        assertEquals("MARIA GOMEZ", r.get(0).getClienteNombre());
    }

    @Test
    public void falloAlBuscarNombreNoImpideRegistrar() {
        when(lector.aplicables(anyDouble(), any())).thenReturn(Collections.singletonList(aplicable(4L, "N", 1)));
        when(jdbc.queryForList(contains("personas.persona"), eq(String.class), eq(15L)))
                .thenThrow(new DataIntegrityViolationException("boom"));
        SorteoClienteInput c = cliente();
        c.setNombre(null);
        List<Participacion> r = service.registrar(venta(100L, 500000), detalles, c, 3L);
        assertEquals(1, r.size());
        assertNull(r.get(0).getClienteNombre());
    }

    @Test
    public void errorDeSqlDevuelveVacioYNoLanza() {
        when(lector.aplicables(anyDouble(), any())).thenReturn(Collections.singletonList(aplicable(4L, "N", 2)));
        when(jdbc.update(contains("sorteo_participacion"), (Object[]) any()))
                .thenThrow(new DataIntegrityViolationException("relation does not exist"));

        List<Participacion> r = assertDoesNotThrow(
                () -> service.registrar(venta(100L, 500000), detalles, cliente(), 3L));

        assertTrue(r.isEmpty());
        // La transaccion propia del insert se revierte; la de la venta no se entera.
        verify(txManager, atLeastOnce()).rollback(any());
    }

    @Test
    public void ticketsExpandeCantidad() {
        Participacion p = new Participacion();
        p.setSorteoId(4L);
        p.setNombreSorteo("NAVIDAD");
        p.setCodigo("S4-7-100");
        p.setCantidad(3);
        p.setVentaId(100L);
        p.setPremios(Arrays.asList("1 x Moto"));
        List<SorteoTicketDto> t = SorteoParticipacionService.tickets(Collections.singletonList(p), false);
        assertEquals(3, t.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(i + 1, t.get(i).getNumero());
            assertEquals(3, t.get(i).getTotal());
            assertEquals("S4-7-100", t.get(i).getCodigo());
            assertEquals("NAVIDAD", t.get(i).getNombreSorteo());
            assertFalse(t.get(i).isReimpresion());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void ticketsDeVentaExpandeCantidad() {
        Participacion p = new Participacion();
        p.setSorteoId(4L);
        p.setNombreSorteo("NAVIDAD");
        p.setCodigo("S4-7-100");
        p.setCantidad(3);
        p.setVentaId(100L);
        when(jdbc.query(contains("sorteo_participacion"), any(RowMapper.class), eq(100L)))
                .thenReturn(Collections.singletonList(p));
        when(jdbc.queryForList(contains("sorteo_premio"), eq(String.class), eq(4L)))
                .thenReturn(Arrays.asList("1 x Moto"));

        List<SorteoTicketDto> t = service.ticketsDeVenta(100L, true);

        assertEquals(3, t.size());
        assertEquals(1, t.get(0).getNumero());
        assertEquals(3, t.get(2).getNumero());
        assertEquals(3, t.get(1).getTotal());
        assertTrue(t.get(0).isReimpresion());
        assertEquals(Arrays.asList("1 x Moto"), t.get(0).getPremios());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void ticketsDeVentaConErrorDevuelveVacio() {
        when(jdbc.query(anyString(), any(RowMapper.class), any()))
                .thenThrow(new DataIntegrityViolationException("boom"));
        assertTrue(assertDoesNotThrow(() -> service.ticketsDeVenta(100L, false)).isEmpty());
    }

    @Test
    public void resumenParaElDesktop() {
        Participacion p = new Participacion();
        p.setNombreSorteo("NAVIDAD");
        p.setCantidad(3);
        List<SorteoTicketInfo> r = SorteoParticipacionService.resumen(Collections.singletonList(p));
        assertEquals(1, r.size());
        assertEquals("NAVIDAD", r.get(0).getNombreSorteo());
        assertEquals(3, r.get(0).getCantidad());
    }

    @Test
    public void tieneParticipacionesConsultaPorVenta() {
        when(jdbc.queryForObject(contains("sorteo_participacion"), eq(Boolean.class), eq(100L))).thenReturn(true);
        when(jdbc.queryForObject(contains("sorteo_participacion"), eq(Boolean.class), eq(101L))).thenReturn(false);
        assertTrue(service.tieneParticipaciones(100L));
        assertFalse(service.tieneParticipaciones(101L));
        assertFalse(service.tieneParticipaciones(null));
    }

    @Test
    public void tieneParticipacionesConErrorEsFalso() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any()))
                .thenThrow(new DataIntegrityViolationException("boom"));
        assertFalse(assertDoesNotThrow(() -> service.tieneParticipaciones(100L)));
    }
}
