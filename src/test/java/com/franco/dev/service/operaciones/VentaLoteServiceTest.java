package com.franco.dev.service.operaciones;

import com.franco.dev.domain.operaciones.MovimientoStock;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.productos.Producto;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VentaLoteServiceTest {

    @Test
    void devuelveLaAsignacionSinTrazarPorLoQueFefoNoCubrio() {
        LoteFefoService.AsignacionLote r = VentaLoteService.faltanteSinTrazar(3.0, 0.0);

        assertNotNull(r);
        assertNull(r.getLoteId());
        assertEquals("SIN LOTE", r.getNumeroLote());
        assertEquals(3.0, (double) r.getCantidad());
    }

    @Test
    void cubrimientoParcialDejaSoloLaDiferencia() {
        LoteFefoService.AsignacionLote r = VentaLoteService.faltanteSinTrazar(6.0, 4.0);
        assertNotNull(r);
        assertEquals(2.0, (double) r.getCantidad());
    }

    @Test
    void noDevuelveNadaCuandoFefoCubrioTodo() {
        assertNull(VentaLoteService.faltanteSinTrazar(3.0, 3.0));
    }

    @Test
    void limpiaElDesglosePrevioAntesDeCalcularLaAsignacionFefo() {
        LoteFefoService loteFefoService = mock(LoteFefoService.class);
        MovimientoStockLoteService movimientoStockLoteService = mock(MovimientoStockLoteService.class);
        VentaLoteService service = new VentaLoteService(loteFefoService, movimientoStockLoteService);

        Producto producto = new Producto();
        producto.setId(1L);
        producto.setLote(true);

        VentaItem item = new VentaItem();
        item.setId(10L);
        item.setProducto(producto);
        item.setCantidad(5.0);

        MovimientoStock movimiento = new MovimientoStock();
        movimiento.setId(100L);
        movimiento.setSucursalId(24L);

        when(loteFefoService.asignarConPreferencia(anyLong(), anyLong(), anyDouble(), any()))
                .thenReturn(new ArrayList<>());
        when(movimientoStockLoteService.reemplazarDesglose(anyLong(), anyLong(), anyList()))
                .thenReturn(new ArrayList<>());

        service.registrarSalidaVenta(item, movimiento, null);

        InOrder orden = inOrder(movimientoStockLoteService, loteFefoService);
        orden.verify(movimientoStockLoteService).limpiarDesglose(movimiento);
        orden.verify(loteFefoService).asignarConPreferencia(anyLong(), anyLong(), anyDouble(), any());
    }
}
