package com.franco.dev.eventos;

import com.franco.dev.domain.financiero.ConteoMoneda;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.MonedaBilletes;
import com.franco.dev.domain.financiero.MovimientoCaja;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.personas.Usuario;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contrato de los builders que se sumaron en el segundo tramo de la fase 2 (#246). */
class ProyeccionesFase2bContratoTest {

    private static Moneda moneda(String d) {
        Moneda m = new Moneda();
        m.setDenominacion(d);
        return m;
    }

    private static void enCatalogo(String tipo, Map<String, Object> d) {
        assertTrue(Proyeccion.rutasFueraDeCatalogo(tipo, d).isEmpty(), tipo + ": " + Proyeccion.rutasFueraDeCatalogo(tipo, d));
        ProyeccionesContratoTest.sinPan(d);
    }

    @Test
    void conteoEditadoCumpleElCatalogoYSumaBilletesYRespaldo() {
        MonedaBilletes b = new MonedaBilletes();
        b.setValor(100000.0);
        b.setMoneda(moneda("GUARANI"));
        ConteoMoneda cm = new ConteoMoneda();
        cm.setMonedaBilletes(b);
        cm.setCantidad(3.0);
        MovimientoCaja real = new MovimientoCaja();
        real.setMoneda(moneda("REAL"));
        real.setCantidad(50.0);
        real.setActivo(true);
        MovimientoCaja inactivo = new MovimientoCaja();
        inactivo.setMoneda(moneda("REAL"));
        inactivo.setCantidad(999.0);
        inactivo.setActivo(false);
        Map<String, Double> t = EventosCaja.totalesDeConteo(Collections.singletonList(cm), Arrays.asList(real, inactivo));
        assertEquals(300000.0, t.get("GUARANI"));
        assertEquals(50.0, t.get("REAL"), "un respaldo inactivo no suma");

        PdvCaja c = new PdvCaja();
        c.setId(15L);
        c.setSucursalId(7L);
        Usuario u = new Usuario();
        u.setId(3L);
        u.setNickname("ana");
        Map<String, Object> d = EventosCaja.conteoEditado(c, false, u, t, 250000.0, 50.0, 0.0);
        enCatalogo("caja.conteo_editado", d);
        assertEquals(false, d.get("apertura"));
    }

    @Test
    void itemsCanceladosCumpleElCatalogo() {
        Venta v = new Venta();
        v.setId(10L);
        PdvCaja c = new PdvCaja();
        c.setId(15L);
        v.setCaja(c);
        VentaItem vi = new VentaItem();
        vi.setId(5L);
        vi.setCantidad(2.0);
        vi.setPrecio(1000.0);
        Map<String, Object> d = EventosVenta.itemsCancelados(10L, v, Collections.singletonList(vi));
        enCatalogo("venta.items_cancelados", d);
        assertEquals(2000.0, ((List<Map<String, Object>>) d.get("items")).get(0).get("valorTotal"));
    }

    @Test
    void cobroEditadoUsaLaVentaSinItems() {
        Venta v = new Venta();
        v.setId(10L);
        v.setSucursalId(7L);
        Cobro co = new Cobro();
        co.setId(20L);
        v.setCobro(co);
        enCatalogo("venta.cobro_editado", EventosVenta.venta(v, null, Collections.emptyList()));
    }
}
