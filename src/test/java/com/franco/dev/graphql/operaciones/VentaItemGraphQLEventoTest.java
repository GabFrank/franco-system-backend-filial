package com.franco.dev.graphql.operaciones;

import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.eventos.EventoEmisor;
import com.franco.dev.service.financiero.CambioService;
import com.franco.dev.service.operaciones.MovimientoStockService;
import com.franco.dev.service.operaciones.VentaItemService;
import com.franco.dev.service.operaciones.VentaService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** deleteVentaItem sigue igual que en develop (sin TX de afuera, mismo resultado) y solo avisa si borro. */
class VentaItemGraphQLEventoTest {

    private final VentaItemService service = mock(VentaItemService.class);
    private final MovimientoStockService movs = mock(MovimientoStockService.class);
    private final VentaService ventaService = mock(VentaService.class);
    private final CambioService cambio = mock(CambioService.class);
    private final EventoEmisor emisor = mock(EventoEmisor.class);
    private final VentaItemGraphQL g = new VentaItemGraphQL();

    VentaItemGraphQLEventoTest() {
        ReflectionTestUtils.setField(g, "service", service);
        ReflectionTestUtils.setField(g, "movimientoStockService", movs);
        ReflectionTestUtils.setField(g, "ventaService", ventaService);
        ReflectionTestUtils.setField(g, "cambioService", cambio);
        ReflectionTestUtils.setField(g, "eventoEmisor", emisor);
        when(cambio.findLastValorEnGsByMonedaIdOrDefault(anyLong(), anyDouble())).thenReturn(1.0);
        when(movs.findByReferencia(anyLong())).thenReturn(Collections.emptyList());
        when(service.findByVentaId(anyLong())).thenReturn(Collections.emptyList());
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
    void laTransaccionNoCambia() throws Exception {
        assertNull(VentaItemGraphQL.class.getMethod("deleteVentaItem", Long.class, Long.class).getAnnotation(Transactional.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void avisaDespuesDelBorradoYDevuelveLoMismo() {
        when(service.findById(9L)).thenReturn(Optional.of(item()));
        when(service.deleteById(9L)).thenReturn(true);
        assertTrue(g.deleteVentaItem(9L, null));
        InOrder o = inOrder(service, emisor);
        o.verify(service).deleteById(9L);
        o.verify(emisor).emitirEnNuevaTx(eq("venta.item_eliminado"), eq("media"), isNull(), eq("venta/123"),
                argThat((Map<String, Object> d) -> Long.valueOf(123L).equals(d.get("venta_id")) && Double.valueOf(30000.0).equals(d.get("valor_total"))));
    }

    @Test
    void siElBorradoDevuelveFalseNoAvisaYDevuelveFalse() {
        when(service.findById(9L)).thenReturn(Optional.of(item()));
        when(service.deleteById(9L)).thenReturn(false);
        assertFalse(g.deleteVentaItem(9L, null));
        verifyNoInteractions(emisor);
    }

    @Test
    void itemInexistenteNoAvisa() {
        when(service.findById(9L)).thenReturn(Optional.empty());
        when(service.deleteById(9L)).thenReturn(false);
        assertFalse(g.deleteVentaItem(9L, null));
        verifyNoInteractions(emisor);
    }
}
