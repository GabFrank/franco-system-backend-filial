package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.operaciones.enums.TipoMovimiento;
import com.franco.dev.eventos.EventoEmisor;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import javax.persistence.EntityManager;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VentaItemOperacionServiceTest {

    private final VentaItemService items = mock(VentaItemService.class);
    private final MovimientoStockService movs = mock(MovimientoStockService.class);
    private final EventoEmisor emisor = mock(EventoEmisor.class);
    private final EntityManager em = mock(EntityManager.class);
    private final VentaItemOperacionService svc = new VentaItemOperacionService(items, movs, emisor);

    VentaItemOperacionServiceTest() {
        svc.setEntityManager(em);
    }

    private VentaItem item() {
        Venta v = new Venta();
        v.setId(123L);
        VentaItem vi = new VentaItem();
        vi.setId(9L);
        vi.setVenta(v);
        vi.setCantidad(2.0);
        vi.setPrecio(15000.0);
        return vi;
    }

    @Test
    @SuppressWarnings("unchecked")
    void avisaRecienDespuesDeConfirmarElBorrado() {
        VentaItem vi = item();
        MovimientoStock ms = new MovimientoStock();
        ms.setTipoMovimiento(TipoMovimiento.VENTA);
        ms.setEstado(true);
        when(items.findById(9L)).thenReturn(Optional.of(vi));
        when(movs.findByReferencia(9L)).thenReturn(Collections.singletonList(ms));
        when(items.deleteById(9L)).thenReturn(true);
        AtomicInteger recalculos = new AtomicInteger();

        assertTrue(svc.eliminar(9L, v -> recalculos.incrementAndGet()));

        assertFalse(ms.getEstado());
        assertEquals(1, recalculos.get());
        InOrder orden = inOrder(movs, items, em, emisor);
        orden.verify(movs).saveAndSend(ms, false);
        orden.verify(items).deleteById(9L);
        orden.verify(em).flush();
        orden.verify(emisor).emitir(eq("venta.item_eliminado"), eq("media"), isNull(), eq("venta/123"), argThat((Map<String, Object> d) ->
                Long.valueOf(123L).equals(d.get("venta_id")) && Double.valueOf(30000.0).equals(d.get("valor_total"))));
    }

    @Test
    void siElBorradoFallaNoAvisaYRevierte() {
        when(items.findById(9L)).thenReturn(Optional.of(item()));
        when(movs.findByReferencia(9L)).thenReturn(Collections.emptyList());
        when(items.deleteById(9L)).thenReturn(false);
        assertThrows(VentaItemOperacionService.BorradoFallido.class, () -> svc.eliminar(9L, v -> {}));
        verifyNoInteractions(emisor);
        verify(em, never()).flush();
    }

    @Test
    void siElFlushFallaNoAvisa() {
        when(items.findById(9L)).thenReturn(Optional.of(item()));
        when(movs.findByReferencia(9L)).thenReturn(Collections.emptyList());
        when(items.deleteById(9L)).thenReturn(true);
        doThrow(new javax.persistence.PersistenceException("fk")).when(em).flush();
        assertThrows(javax.persistence.PersistenceException.class, () -> svc.eliminar(9L, v -> {}));
        verifyNoInteractions(emisor);
    }

    @Test
    void itemInexistente() {
        when(items.findById(9L)).thenReturn(Optional.empty());
        assertFalse(svc.eliminar(9L, v -> {}));
        verify(items, never()).deleteById(anyLong());
        verifyNoInteractions(emisor);
    }

    @Test
    void elResolverDevuelveFalseSiLaOperacionFalla() {
        com.franco.dev.graphql.operaciones.VentaItemGraphQL g = new com.franco.dev.graphql.operaciones.VentaItemGraphQL();
        VentaItemOperacionService op = mock(VentaItemOperacionService.class);
        when(op.eliminar(eq(9L), any())).thenThrow(new VentaItemOperacionService.BorradoFallido(9L));
        org.springframework.test.util.ReflectionTestUtils.setField(g, "ventaItemOperacionService", op);
        assertFalse(g.deleteVentaItem(9L, null));
        Mockito.reset(op);
        when(op.eliminar(eq(9L), any())).thenReturn(true);
        assertTrue(g.deleteVentaItem(9L, null));
    }
}
