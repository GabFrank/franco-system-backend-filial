package com.franco.dev.graphql.financiero;

import com.franco.dev.domain.financiero.Gasto;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.Retiro;
import com.franco.dev.domain.financiero.TipoGasto;
import com.franco.dev.eventos.EventoEmisor;
import com.franco.dev.eventos.Proyeccion;
import com.franco.dev.graphql.financiero.input.RetiroInput;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** retiro.* y gasto.* (fase 2, #246): lo que arman cumple el catalogo. */
class RetiroGastoEventosTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object>[] capturar(EventoEmisor e, String tipo) {
        ArgumentCaptor<Supplier<Map<String, Object>>> datos = ArgumentCaptor.forClass(Supplier.class);
        ArgumentCaptor<Supplier<Map<String, Object>>> atr = ArgumentCaptor.forClass(Supplier.class);
        verify(e).emitirProyeccion(eq(tipo), anyString(), any(), anyString(), datos.capture(), atr.capture());
        return new Map[]{datos.getValue().get(), atr.getValue().get()};
    }

    @Test
    void retiroEliminadoCumpleElCatalogo() {
        RetiroGraphQL g = new RetiroGraphQL();
        EventoEmisor e = mock(EventoEmisor.class);
        ReflectionTestUtils.setField(g, "eventoEmisor", e);
        PdvCaja c = new PdvCaja();
        c.setId(15L);
        Retiro r = new Retiro();
        r.setId(4L);
        r.setSucursalId(7L);
        r.setCajaSalida(c);
        g.emitirRetiro("retiro.eliminado", r, null);
        Map<String, Object>[] m = capturar(e, "retiro.eliminado");
        assertTrue(Proyeccion.rutasFueraDeCatalogo("retiro.eliminado", m[0]).isEmpty());
        assertEquals(15L, m[1].get("caja_id"));

        RetiroInput in = new RetiroInput();
        in.setRetiroGs(10000.0);
        g.emitirRetiro("retiro.creado", r, in);
        Map<String, Object>[] n = capturar(e, "retiro.creado");
        assertEquals(10000.0, n[1].get("monto_gs"));
    }

    @Test
    void gastoCumpleElCatalogo() {
        GastoGraphQL g = new GastoGraphQL();
        EventoEmisor e = mock(EventoEmisor.class);
        ReflectionTestUtils.setField(g, "eventoEmisor", e);
        PdvCaja c = new PdvCaja();
        c.setId(15L);
        TipoGasto t = new TipoGasto();
        t.setId(2L);
        t.setDescripcion("LIMPIEZA");
        Gasto gs = new Gasto();
        gs.setId(9L);
        gs.setSucursalId(7L);
        gs.setCaja(c);
        gs.setTipoGasto(t);
        gs.setRetiroGs(30000.0);
        gs.setObservacion("ESCOBA");
        g.emitirGasto("gasto.creado", gs);
        Map<String, Object>[] m = capturar(e, "gasto.creado");
        assertTrue(Proyeccion.rutasFueraDeCatalogo("gasto.creado", m[0]).isEmpty(),
                Proyeccion.rutasFueraDeCatalogo("gasto.creado", m[0]).toString());
        assertEquals(2L, m[1].get("tipo_gasto_id"));
        assertEquals(30000.0, m[1].get("monto_gs"));
    }
}
