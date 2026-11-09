package com.franco.dev.eventos;

import com.franco.dev.domain.financiero.Maletin;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.TerminalPos;
import com.franco.dev.domain.financiero.VentaTarjeta;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.graphql.financiero.input.PdvCajaBalanceDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Contratos de la fase 2b (#246): la forma que pidio el cliente y las rutas dentro del catalogo. */
class ProyeccionesFase2bTest {

    private static PdvCaja caja(long id) {
        PdvCaja c = new PdvCaja();
        c.setId(id);
        c.setSucursalId(7L);
        c.setFechaApertura(LocalDateTime.of(2026, 11, 9, 8, 0));
        Maletin m = new Maletin();
        m.setId(3L);
        m.setDescripcion("MALETIN 3");
        c.setMaletin(m);
        return c;
    }

    private static void enCatalogo(String tipo, Map<String, Object> d) {
        assertTrue(Proyeccion.rutasFueraDeCatalogo(tipo, d).isEmpty(), tipo + ": " + Proyeccion.rutasFueraDeCatalogo(tipo, d));
    }

    @Test
    void aperturaConCajaAnteriorYMaletinDistinto() {
        PdvCaja anterior = caja(10);
        anterior.setFechaCierre(LocalDateTime.of(2026, 11, 8, 22, 0));
        EventosCaja.Anterior ant = new EventosCaja.Anterior(anterior, 500000.0, 100.0, null);
        Map<String, Object> d = EventosCaja.abierta(caja(11), 450000.0, 100.0, 0.0, ant);
        enCatalogo("caja.abierta", d);
        assertEquals(10L, d.get("cajaAnteriorId"));
        assertEquals("CON_DIFERENCIA", d.get("estadoDiferenciaConsecutiva"));
        assertEquals(500000.0, ((Map<?, ?>) ((Map<?, ?>) d.get("cajaAnterior")).get("balance")).get("totalCierreGs"));
        assertEquals(450000.0, ((Map<?, ?>) d.get("balance")).get("totalAperGs"));
        assertTrue(EventosCaja.hayDiferencia(450000.0, 100.0, 0.0, ant));
        Map<String, Object> m = EventosCaja.maletinDistinto(caja(11), 450000.0, 100.0, 0.0, ant);
        enCatalogo("caja.maletin_distinto", m);
        assertEquals(-50000.0, ((Map<?, ?>) m.get("diferencia")).get("gs"));
        assertEquals(0.0, ((Map<?, ?>) m.get("diferencia")).get("rs"));
    }

    @Test
    void aperturaSinCajaAnteriorEsSinDatosYNoHayMaletinDistinto() {
        Map<String, Object> d = EventosCaja.abierta(caja(11), 1.0, 0.0, 0.0, null);
        assertEquals("SIN_DATOS", d.get("estadoDiferenciaConsecutiva"));
        assertNull(d.get("cajaAnterior"));
        assertFalse(EventosCaja.hayDiferencia(1.0, 0.0, 0.0, null));
        EventosCaja.Anterior igual = new EventosCaja.Anterior(caja(10), 1.0, 0.0, 0.0);
        assertEquals("SIN_DIFERENCIA", EventosCaja.estadoConsecutivo(1.0, null, 0.0, igual));
    }

    @Test
    void cierreConBalanceCompletoYDiferenciaTotalEnGs() {
        PdvCajaBalanceDto b = new PdvCajaBalanceDto();
        b.setTotalGsCierre(90000.0);
        b.setDiferenciaGs(-10000.0);
        b.setDiferenciaRs(-5.0);
        b.setTotalTarjeta(30000.0);
        Map<String, Object> d = EventosCaja.cerrada(caja(11), b, 10L, 1400.0, 7300.0);
        enCatalogo("caja.cerrada", d);
        Map<?, ?> bal = (Map<?, ?>) d.get("balance");
        assertEquals(30000.0, bal.get("totalTarjeta"));
        assertEquals(-5.0, bal.get("diferenciaRs"));
        assertEquals(17000.0, EventosCaja.diferenciaTotalGs(b, 1400.0, 7300.0), 0.001);
        // Diferencia solo en reales y sin cotizacion: el atributo queda ausente.
        assertNull(EventosCaja.diferenciaTotalGs(b, null, 7300.0));
        assertFalse(EventosCaja.atributosCierre(caja(11), b, null, 7300.0).containsKey("diferencia_total_gs"));
        assertTrue(CatalogoEventos.tipo("caja.cerrada").atributos.containsAll(EventosCaja.atributosCierre(caja(11), b, 1400.0, 7300.0).keySet()));
    }

    @Test
    void conteoEditadoConBalanceRecalculado() {
        PdvCajaBalanceDto b = new PdvCajaBalanceDto();
        b.setDiferenciaGs(0.0);
        Map<String, Object> d = EventosCaja.conteoEditado(caja(11), false, null, Collections.emptyMap(), 1.0, 0.0, 0.0, b);
        enCatalogo("caja.conteo_editado", d);
        assertEquals("cierre", d.get("tipo"));
        assertEquals(7L, ((Map<?, ?>) ((Map<?, ?>) d.get("caja")).get("sucursal")).get("id"));
    }

    private static CobroDetalle linea(long formaId, String forma) {
        CobroDetalle cd = new CobroDetalle();
        cd.setId(1L);
        cd.setValor(50000.0);
        cd.setIdentificadorTransaccion("E2E123");
        FormaPago f = new FormaPago();
        f.setId(formaId);
        f.setDescripcion(forma);
        cd.setFormaPago(f);
        Moneda m = new Moneda();
        m.setId(1L);
        m.setDenominacion("GUARANI");
        cd.setMoneda(m);
        return cd;
    }

    @Test
    void cobroEditadoYTarjetaConLaFormaDelCliente() {
        Venta v = new Venta();
        v.setId(99L);
        v.setSucursalId(7L);
        v.setCaja(caja(11));
        List<CobroDetalle> l = Collections.singletonList(linea(2L, "TARJETA"));
        Map<String, Object> ce = EventosVenta.cobroEditado(v, l);
        enCatalogo("venta.cobro_editado", ce);
        Map<?, ?> fp = (Map<?, ?>) ((Map<?, ?>) ((List<?>) ce.get("cobroDetalleList")).get(0)).get("formaPago");
        assertEquals(2L, fp.get("id"));
        assertEquals(Collections.singletonList(2L), EventosVenta.atributosVenta(v, l).get("formas_pago_id"));

        VentaTarjeta vt = new VentaTarjeta();
        vt.setId(1L);
        vt.setVentaId(99L);
        vt.setCajaId(11L);
        vt.setSucursalId(7L);
        vt.setMonto(new BigDecimal("50000"));
        TerminalPos t = new TerminalPos();
        t.setId(4L);
        t.setCodigo("T4");
        vt.setTerminalPos(t);
        Map<String, Object> d = EventosTarjeta.tarjeta(vt, v, l);
        enCatalogo("venta_tarjeta.completada", d);
        assertEquals(11L, ((Map<?, ?>) d.get("caja")).get("id"));
        assertEquals("T4", ((Map<?, ?>) d.get("terminalPos")).get("codigo"));
        // Sin sensibles no sale el identificador de la linea de cobro de la venta.
        Map<String, Object> limpio = Proyeccion.limpiar("venta_tarjeta.completada", d, true, false);
        Map<?, ?> lin = (Map<?, ?>) ((List<?>) ((Map<?, ?>) ((Map<?, ?>) limpio.get("venta")).get("cobro")).get("cobroDetalleList")).get(0);
        assertFalse(lin.containsKey("identificadorTransaccion"));
    }

    @Test
    void tarjetaEditadaSoloConCambiosRelevantes() {
        VentaTarjeta a = new VentaTarjeta();
        a.setMonto(new BigDecimal("50000.00"));
        VentaTarjeta b = new VentaTarjeta();
        b.setMonto(new BigDecimal("50000"));
        assertTrue(EventosTarjeta.cambios(EventosTarjeta.foto(a), EventosTarjeta.foto(b)).isEmpty());
        b.setMonto(new BigDecimal("45000"));
        Map<String, Object> c = EventosTarjeta.cambios(EventosTarjeta.foto(a), EventosTarjeta.foto(b));
        assertEquals(1, c.size());
        Map<String, Object> d = EventosTarjeta.tarjeta(b);
        d.put("cambios", c);
        enCatalogo("venta_tarjeta.editada", d);
    }
}
