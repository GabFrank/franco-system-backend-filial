package com.franco.dev.eventos;

import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.domain.financiero.Moneda;
import com.franco.dev.domain.financiero.PdvCaja;
import com.franco.dev.domain.financiero.TerminalPos;
import com.franco.dev.domain.financiero.VentaTarjeta;
import com.franco.dev.domain.operaciones.Cobro;
import com.franco.dev.domain.operaciones.CobroDetalle;
import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.domain.operaciones.VentaItem;
import com.franco.dev.domain.operaciones.enums.VentaEstado;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.graphql.financiero.input.PdvCajaBalanceDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contrato de las proyecciones de la fase 2: todo lo que arma cada builder esta en el catalogo (un
 * campo nuevo sin clasificar rompe este test) y ningun valor tiene forma de numero de tarjeta.
 */
class ProyeccionesContratoTest {

    private static final Pattern PAN = Pattern.compile("\\d{13,19}");

    private static boolean luhn(String n) {
        int s = 0;
        boolean dbl = false;
        for (int i = n.length() - 1; i >= 0; i--) {
            int d = n.charAt(i) - '0';
            if (dbl) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            s += d;
            dbl = !dbl;
        }
        return s % 10 == 0;
    }

    private static void sinPan(Object o) {
        Matcher m = PAN.matcher(String.valueOf(o).replaceAll("[ -]", ""));
        while (m.find()) assertFalse(luhn(m.group()), "valor con forma de PAN: " + m.group());
    }

    private static Venta venta() {
        Usuario u = new Usuario();
        u.setId(3L);
        u.setNickname("ana");
        PdvCaja caja = new PdvCaja();
        caja.setId(15L);
        caja.setUsuario(u);
        Cobro cobro = new Cobro();
        cobro.setId(20L);
        Venta v = new Venta();
        v.setId(10L);
        v.setSucursalId(7L);
        v.setEstado(VentaEstado.CONCLUIDA);
        v.setTotalGs(50000.0);
        v.setUsuario(u);
        v.setCaja(caja);
        v.setCobro(cobro);
        return v;
    }

    private static List<CobroDetalle> detalles() {
        Moneda gs = new Moneda();
        gs.setDenominacion("GUARANI");
        FormaPago tj = new FormaPago();
        tj.setDescripcion("Tarjeta");
        CobroDetalle cd = new CobroDetalle();
        cd.setId(1L);
        cd.setValor(50000.0);
        cd.setMoneda(gs);
        cd.setFormaPago(tj);
        cd.setIdentificadorTransaccion("ABC123");
        CobroDetalle vuelto = new CobroDetalle();
        vuelto.setVuelto(true);
        FormaPago ef = new FormaPago();
        ef.setDescripcion("Efectivo");
        vuelto.setFormaPago(ef);
        return Arrays.asList(cd, vuelto);
    }

    @Test
    void ventaConcluidaCumpleElCatalogo() {
        VentaItem vi = new VentaItem();
        vi.setId(5L);
        vi.setCantidad(2.0);
        vi.setPrecio(25000.0);
        Map<String, Object> d = EventosVenta.venta(venta(), Collections.singletonList(vi), detalles());
        assertTrue(Proyeccion.rutasFueraDeCatalogo("venta.concluida", d).isEmpty(), Proyeccion.rutasFueraDeCatalogo("venta.concluida", d).toString());
        assertEquals(50000.0, d.get("valorTotal"));
        sinPan(d);
        Map<String, Object> a = EventosVenta.atributosVenta(venta(), detalles());
        assertEquals(Collections.singletonList("TARJETA"), a.get("formas_pago"), "los vueltos no cuentan como forma de pago");
        assertEquals(15L, a.get("caja_id"));
        assertTrue(CatalogoEventos.tipo("venta.concluida").atributos.containsAll(a.keySet()));
    }

    @Test
    void soloSaleAlPasarAConcluida() {
        Venta v = venta();
        assertTrue(EventosVenta.concluyeAhora(null, v));
        assertTrue(EventosVenta.concluyeAhora(VentaEstado.values()[0] == VentaEstado.CONCLUIDA ? null : VentaEstado.values()[0], v));
        assertFalse(EventosVenta.concluyeAhora(VentaEstado.CONCLUIDA, v));
        v.setEstado(null);
        assertFalse(EventosVenta.concluyeAhora(null, v));
    }

    @Test
    void cajaCumpleElCatalogo() {
        PdvCaja c = venta().getCaja();
        Map<String, Object> ab = EventosCaja.abierta(c, 100000.0, 0.0, 0.0);
        assertTrue(Proyeccion.rutasFueraDeCatalogo("caja.abierta", ab).isEmpty(), Proyeccion.rutasFueraDeCatalogo("caja.abierta", ab).toString());
        PdvCajaBalanceDto b = new PdvCajaBalanceDto();
        b.setTotalGsCierre(90000.0);
        b.setDiferenciaGs(-10000.0);
        Map<String, Object> ce = EventosCaja.cerrada(c, b);
        assertTrue(Proyeccion.rutasFueraDeCatalogo("caja.cerrada", ce).isEmpty(), Proyeccion.rutasFueraDeCatalogo("caja.cerrada", ce).toString());
        Map<String, Object> a = EventosCaja.atributosCierre(c, b);
        assertEquals(true, a.get("con_diferencia"));
        assertTrue(CatalogoEventos.tipo("caja.cerrada").atributos.containsAll(a.keySet()));
    }

    @Test
    void tarjetaCumpleElCatalogoYNoSacaLaImagen() {
        VentaTarjeta vt = new VentaTarjeta();
        vt.setId(1L);
        vt.setVentaId(10L);
        vt.setCajaId(15L);
        vt.setSucursalId(7L);
        vt.setEstado("COMPLETADO");
        vt.setMonto(new BigDecimal("50000"));
        vt.setCodigoAutorizacion("123456");
        vt.setQrCrudo("QR|123456|****1234");
        vt.setImagenUrl("https://x/cupon.jpg");
        TerminalPos t = new TerminalPos();
        t.setId(4L);
        t.setSerie("SN1");
        vt.setTerminalPos(t);
        Map<String, Object> d = EventosTarjeta.tarjeta(vt);
        assertTrue(Proyeccion.rutasFueraDeCatalogo("venta_tarjeta.completada", d).isEmpty(), Proyeccion.rutasFueraDeCatalogo("venta_tarjeta.completada", d).toString());
        Map<String, Object> sinPermiso = Proyeccion.limpiar("venta_tarjeta.completada", d, true, false);
        assertFalse(sinPermiso.containsKey("codigoAutorizacion"));
        assertFalse(sinPermiso.containsKey("qrCrudo"));
        assertFalse(sinPermiso.containsKey("imagenUrl"));
        sinPan(Proyeccion.limpiar("venta_tarjeta.completada", d, true, true));
        assertTrue(CatalogoEventos.tipo("venta_tarjeta.completada").atributos.containsAll(EventosTarjeta.atributos(vt).keySet()));
    }
}
