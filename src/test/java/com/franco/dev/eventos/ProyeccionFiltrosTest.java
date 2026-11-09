package com.franco.dev.eventos;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fase 2: filtros por atributo, lista blanca por rutas, recorte por campos y datos personales o sensibles. */
class ProyeccionFiltrosTest {

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) out.put((String) kv[i], kv[i + 1]);
        return out;
    }

    private static Map<String, Object> venta() {
        return m("id", 10L, "estado", "CONCLUIDA", "totalGs", 50000.0, "ventaObs", "cliente pidio factura",
                "usuario", m("id", 3L, "nickname", "ana", "password", "x"),
                "cliente", m("id", 4L, "codigo", "C1", "nombre", "Juan",
                        "persona", m("nombre", "Juan Perez", "documento", "1234567", "telefono", "0981")),
                "cobro", m("id", 20L, "cobroDetalleList", Arrays.asList(
                        m("valor", 30000.0, "identificadorTransaccion", "TX-1", "formaPago", m("descripcion", "TARJETA")),
                        m("valor", 20000.0, "formaPago", m("descripcion", "EFECTIVO"), "secreto", "no"))),
                "ventaItemList", Collections.singletonList(m("cantidad", 1.0, "producto", m("id", 9L, "costo", 1.0))));
    }

    private static Suscripcion sus(String eventos) {
        Suscripcion s = new Suscripcion();
        s.setId(1L);
        s.setUrl("https://x/hook");
        s.setActivo(true);
        s.setEventos(Arrays.asList(eventos.split(",")));
        return s;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sub(Map<String, Object> d, String k) {
        return (Map<String, Object>) d.get(k);
    }

    // ---------------------------------------------------------------- lista blanca

    @Test
    void sinPermisosSaleSoloLoOperativo() {
        Map<String, Object> d = Proyeccion.limpiar("venta.concluida", venta(), false, false);
        assertEquals(10L, d.get("id"));
        assertNull(d.get("ventaObs"));
        assertEquals(m("id", 3L), d.get("usuario"));
        assertEquals(m("id", 4L, "codigo", "C1"), d.get("cliente"), "persona sin nada operativo no sale como {}");
        List<Map<String, Object>> det = (List<Map<String, Object>>) sub(d, "cobro").get("cobroDetalleList");
        assertNull(det.get(0).get("identificadorTransaccion"));
        assertNull(det.get(1).get("secreto"));
        assertEquals(m("descripcion", "TARJETA"), det.get(0).get("formaPago"));
        assertNull(((Map<String, Object>) ((List<Map<String, Object>>) d.get("ventaItemList")).get(0).get("producto")).get("costo"));
    }

    @Test
    void conPermisosSalenPersonalYSensibleNuncaLoQueNoEstaEnLaLista() {
        Map<String, Object> d = Proyeccion.limpiar("venta.concluida", venta(), true, true);
        assertEquals("cliente pidio factura", d.get("ventaObs"));
        assertEquals("ana", sub(d, "usuario").get("nickname"));
        assertNull(sub(d, "usuario").get("password"));
        Map<String, Object> persona = sub(sub(d, "cliente"), "persona");
        assertEquals("Juan Perez", persona.get("nombre"));
        assertNull(persona.get("documento"));
        assertNull(persona.get("telefono"));
        List<Map<String, Object>> det = (List<Map<String, Object>>) sub(d, "cobro").get("cobroDetalleList");
        assertEquals("TX-1", det.get(0).get("identificadorTransaccion"));
        assertTrue(Proyeccion.rutasFueraDeCatalogo("venta.concluida", d).isEmpty());
    }

    @Test
    void lasRutasFueraDeCatalogoSeDetectan() {
        assertEquals(Arrays.asList("usuario.password", "cliente.persona.documento", "cliente.persona.telefono",
                        "cobro.cobroDetalleList.secreto", "ventaItemList.producto.costo"),
                Proyeccion.rutasFueraDeCatalogo("venta.concluida", venta()));
    }

    @Test
    void cadaRutaDelCatalogoTieneClaseYCadaTipoAtributos() {
        for (CatalogoEventos.Tipo t : CatalogoEventos.todos().values()) {
            assertFalse(t.rutas.isEmpty(), t.nombre);
            assertFalse(t.atributos.isEmpty(), t.nombre);
            t.rutas.forEach((r, c) -> assertTrue(c != null && !r.contains(" "), t.nombre + " " + r));
        }
        // Lo de tarjeta que identifica la operacion es sensible
        Map<String, CatalogoEventos.Clase> tj = CatalogoEventos.tipo("venta_tarjeta.completada").rutas;
        assertEquals(CatalogoEventos.Clase.SENSIBLE, tj.get("codigoAutorizacion"));
        assertEquals(CatalogoEventos.Clase.SENSIBLE, tj.get("qrCrudo"));
        assertEquals(CatalogoEventos.Clase.SENSIBLE, tj.get("numeroBoleta"));
        assertFalse(tj.containsKey("imagenUrl"));
        assertFalse(tj.containsKey("datosExtra"));
    }

    // ---------------------------------------------------------------- campos

    @Test
    void recortaALasRutasPedidas() {
        Map<String, Object> d = Proyeccion.limpiar("venta.concluida", venta(), false, false);
        Map<String, Object> r = Proyeccion.recortar("venta.concluida", d, Arrays.asList("id", "cobro.cobroDetalleList"));
        assertEquals(Arrays.asList("id", "cobro"), Arrays.asList(r.keySet().toArray()));
        assertNull(r.get("ventaItemList"));
        assertEquals(2, ((List<?>) sub(r, "cobro").get("cobroDetalleList")).size());
        assertNull(sub(r, "cobro").get("id"));
    }

    @Test
    void sinCamposVaTodoYConRutasDesconocidasSaleVacio() {
        Map<String, Object> d = Proyeccion.limpiar("venta.concluida", venta(), false, false);
        assertEquals(d, Proyeccion.recortar("venta.concluida", d, null));
        assertEquals(d, Proyeccion.recortar("venta.concluida", d, Collections.emptyList()));
        assertTrue(Proyeccion.recortar("venta.concluida", d, Collections.singletonList("noExiste")).isEmpty());
        // Una desconocida junto a una valida: se ignora la desconocida
        assertEquals(m("id", 10L), Proyeccion.recortar("venta.concluida", d, Arrays.asList("noExiste", "id")));
    }

    // ---------------------------------------------------------------- filtros

    private static Map<String, Map<String, List<Object>>> filtro(String tipo, String atributo, Object... valores) {
        Map<String, List<Object>> a = new HashMap<>();
        a.put(atributo, Arrays.asList(valores));
        Map<String, Map<String, List<Object>>> f = new HashMap<>();
        f.put(tipo, a);
        return f;
    }

    @Test
    void filtroPorListaAlcanzaConUnElemento() {
        Suscripcion s = sus("venta.*");
        s.setFiltros(filtro("venta.concluida", "formas_pago", "tarjeta", "PIX"));
        Map<String, Object> conTarjeta = m("formas_pago", Arrays.asList("EFECTIVO", "TARJETA"));
        Map<String, Object> soloEfectivo = m("formas_pago", Collections.singletonList("EFECTIVO"));
        assertTrue(ReglasEntrega.recibe(s, "venta.concluida", 7L, null, conTarjeta));
        assertFalse(ReglasEntrega.recibe(s, "venta.concluida", 7L, null, soloEfectivo));
        assertFalse(ReglasEntrega.recibe(s, "venta.concluida", 7L, null, m()));
        assertFalse(ReglasEntrega.recibe(s, "venta.concluida", 7L, null, null));
        // El filtro de otro tipo no afecta
        assertTrue(ReglasEntrega.recibe(s, "venta.cobro_editado", 7L, null, soloEfectivo));
    }

    @Test
    void filtroPorBooleanoYNumero() {
        Suscripcion s = sus("caja.cerrada");
        s.setFiltros(filtro("caja.cerrada", "con_diferencia", true));
        assertTrue(ReglasEntrega.recibe(s, "caja.cerrada", 7L, null, m("con_diferencia", true)));
        assertFalse(ReglasEntrega.recibe(s, "caja.cerrada", 7L, null, m("con_diferencia", false)));
        s.setFiltros(filtro("caja.cerrada", "caja_id", 15));
        assertTrue(ReglasEntrega.recibe(s, "caja.cerrada", 7L, null, m("caja_id", 15L)));
        assertFalse(ReglasEntrega.recibe(s, "caja.cerrada", 7L, null, m("caja_id", 16L)));
    }

    @Test
    void atributoDesconocidoNoEntregaYEsInvalido() {
        Suscripcion s = sus("venta.concluida");
        s.setFiltros(filtro("venta.concluida", "forma_pago", "TARJETA"));
        Map<String, Object> a = m("formas_pago", Collections.singletonList("TARJETA"));
        assertFalse(ReglasEntrega.recibe(s, "venta.concluida", 7L, null, a));
        assertEquals(ReglasEntrega.Filtro.INVALIDO, ReglasEntrega.evaluarFiltros(s, "venta.concluida", a));
        // Un tipo de la 1a sin catalogo tampoco acepta filtros
        s.setEventos(Collections.singletonList("venta.item_eliminado"));
        s.setFiltros(filtro("venta.item_eliminado", "caja_id", 1));
        assertEquals(ReglasEntrega.Filtro.INVALIDO, ReglasEntrega.evaluarFiltros(s, "venta.item_eliminado", null));
    }

    @Test
    void elUmbralMiraLosAtributos() {
        Suscripcion s = sus("caja.cierre_con_diferencia");
        Map<String, Number> u = new HashMap<>();
        u.put("diferencia_gs", 10000);
        Map<String, Map<String, Number>> umbrales = new HashMap<>();
        umbrales.put("caja.cierre_con_diferencia", u);
        s.setUmbrales(umbrales);
        assertTrue(ReglasEntrega.recibe(s, "caja.cierre_con_diferencia", 7L, m(), m("diferencia_gs", -15000.0)));
        assertFalse(ReglasEntrega.recibe(s, "caja.cierre_con_diferencia", 7L, m(), m("diferencia_gs", 5000.0)));
    }

    @Test
    void sinFiltrosTodoQuedaComoEnLa1a() {
        Suscripcion s = sus("venta.item_eliminado");
        assertTrue(ReglasEntrega.recibe(s, "venta.item_eliminado", 7L, m("venta_id", 1)));
        assertEquals(ReglasEntrega.Filtro.PASA, ReglasEntrega.evaluarFiltros(s, "venta.item_eliminado", null));
    }

    @Test
    void unObjetoQueQuedaVacioNoSale() {
        Map<String, Object> u = new java.util.LinkedHashMap<>();
        u.put("nickname", "ana");
        Map<String, Object> d = new java.util.LinkedHashMap<>();
        d.put("id", 1L);
        d.put("usuario", u);
        Map<String, Object> l = Proyeccion.limpiar("caja.reabierta", d, false, false);
        assertFalse(l.containsKey("usuario"), l.toString());
    }
}
