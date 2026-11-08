package com.franco.dev.eventos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EventosReglasTest {

    private static Suscripcion sus(String... eventos) {
        Suscripcion s = new Suscripcion();
        s.setId(1L);
        s.setUrl("https://93.184.215.14/hook");
        s.setActivo(true);
        s.setEventos(Arrays.asList(eventos));
        return s;
    }

    @Test
    void comodinYTipoExacto() {
        assertTrue(ReglasEntrega.coincideTipo("venta.*", "venta.item_eliminado"));
        assertTrue(ReglasEntrega.coincideTipo("*", "caja.reabierta"));
        assertTrue(ReglasEntrega.coincideTipo("venta.item_eliminado", "venta.item_eliminado"));
        assertFalse(ReglasEntrega.coincideTipo("venta.*", "ventas.x"));
        assertFalse(ReglasEntrega.coincideTipo("caja.*", "venta.item_eliminado"));
    }

    @Test
    void filtraPorSucursalPausadosInactivaYUmbral() {
        Suscripcion s = sus("venta.*");
        assertTrue(ReglasEntrega.recibe(s, "venta.item_eliminado", 7L, null));
        s.setSucursalIds(Collections.singletonList(3L));
        assertFalse(ReglasEntrega.recibe(s, "venta.item_eliminado", 7L, null));
        s.setSucursalIds(null);
        s.setEventosPausados(Collections.singletonList("venta.item_eliminado"));
        assertFalse(ReglasEntrega.recibe(s, "venta.item_eliminado", 7L, null));
        s.setEventosPausados(null);
        Map<String, Map<String, Number>> u = new HashMap<>();
        u.put("venta.item_eliminado", Collections.singletonMap("valor_total", 50000));
        s.setUmbrales(u);
        assertFalse(ReglasEntrega.recibe(s, "venta.item_eliminado", 7L, Collections.singletonMap("valor_total", 10000)));
        assertTrue(ReglasEntrega.recibe(s, "venta.item_eliminado", 7L, Collections.singletonMap("valor_total", -60000)));
        assertFalse(ReglasEntrega.recibe(s, "venta.item_eliminado", 7L, Collections.emptyMap()), "sin el campo del umbral no sale");
        s.setActivo(false);
        assertFalse(ReglasEntrega.recibe(s, "venta.item_eliminado", 7L, Collections.singletonMap("valor_total", 90000)));
    }

    @Test
    void latidoSoloAQuienLoPidio() {
        Suscripcion s = sus("*");
        assertFalse(ReglasEntrega.recibe(s, "nodo.latido", 7L, null));
        s.setLatido(true);
        s.setEventos(Collections.emptyList());
        assertTrue(ReglasEntrega.recibe(s, "nodo.latido", 7L, null));
    }

    @Test
    void clasificaCodigosHttp() {
        assertEquals(ReglasEntrega.Resultado.ENVIADA, ReglasEntrega.clasificar(200));
        assertEquals(ReglasEntrega.Resultado.ENVIADA, ReglasEntrega.clasificar(204));
        for (int c : new int[]{301, 302, 401, 403, 404, 408, 409, 425, 429, 500, 502, 503, 418}) {
            assertEquals(ReglasEntrega.Resultado.REINTENTO, ReglasEntrega.clasificar(c), "" + c);
        }
        for (int c : new int[]{400, 413, 415, 422}) assertEquals(ReglasEntrega.Resultado.DESCARTADA, ReglasEntrega.clasificar(c), "" + c);
        assertEquals(ReglasEntrega.Resultado.PAUSAR_410, ReglasEntrega.clasificar(410));
    }

    @Test
    void esperasYRetryAfter() {
        assertEquals(10, ReglasEntrega.esperaSegundos(1, null));
        assertEquals(60, ReglasEntrega.esperaSegundos(2, null));
        assertEquals(300, ReglasEntrega.esperaSegundos(3, null));
        assertEquals(1800, ReglasEntrega.esperaSegundos(4, null));
        assertEquals(3600, ReglasEntrega.esperaSegundos(50, null), "despues sigue cada hora hasta las 72 h");
        assertEquals(120, ReglasEntrega.esperaSegundos(1, 120L));
        assertEquals(10, ReglasEntrega.esperaSegundos(1, 2L));
    }

    @Test
    void destinoSeguro() throws Exception {
        assertNull(DestinoSeguro.validar("https://93.184.215.14/hook", null));
        assertNotNull(DestinoSeguro.validar("http://93.184.215.14/hook", null), "solo https");
        for (String ip : new String[]{"127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.1", "169.254.169.254", "100.64.0.5", "100.127.255.1", "0.0.0.0"}) {
            assertNotNull(DestinoSeguro.validar("https://" + ip + "/x", null), ip);
        }
        assertTrue(DestinoSeguro.bloqueada(InetAddress.getByName("::1")));
        assertTrue(DestinoSeguro.bloqueada(InetAddress.getByName("fd00::1")));
        assertTrue(DestinoSeguro.bloqueada(InetAddress.getByAddress(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff, 10, 0, 0, 1})));
        assertNotNull(DestinoSeguro.validar("https://93.184.215.14/hook", Collections.singletonList("loom.example.com")), "fuera de la lista");
    }

    @Test
    void elTransporteRealBloqueaIpsInternasAlConectar() {
        EventosProperties p = new EventosProperties();
        p.setTimeoutMs(2000);
        EventoEnviador.Transporte t = EventoEnviador.transporteNetty(p);
        for (String url : new String[]{"https://localhost:9/x", "https://127.0.0.1:9/x", "https://10.0.0.1:9/x"}) {
            Exception e = assertThrows(Exception.class, () -> t.post(url, Collections.emptyMap(), "{}"), url);
            assertTrue(String.valueOf(causaRaiz(e).getMessage()).contains("Destino bloqueado"), url + ": " + causaRaiz(e));
        }
    }

    private static Throwable causaRaiz(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t;
    }

    @Test
    void firmaStandardWebhooksEd25519() {
        byte[] semilla = new byte[32];
        for (int i = 0; i < 32; i++) semilla[i] = (byte) i;
        FirmaWebhook f = new FirmaWebhook(Base64.getEncoder().encodeToString(semilla));
        String cuerpo = "{\"id\":\"evt_7_X\"}";
        String cab = f.firmar("evt_7_X", 1731000000L, cuerpo);
        assertTrue(cab.startsWith("v1a,"));
        // Vector calculado aparte con el Ed25519 nativo de Node 22 (crypto.sign): misma semilla, mismo contenido.
        assertEquals("v1a,CaGwqZ0xfaIabQS5grvjUyPRJT5/un4yFxZ8nrg9Gq0jzRgNVz6VoDFX7XO/TZJSAgamBA1TNtskFXBEEDtBBg==", cab);
        assertEquals("A6EHv/POEL4dcN0Y50vAmWfk1jCbpQ1fHdyGZBJVMbg=", f.clavePublicaBase64());
        assertTrue(FirmaWebhook.verificar(f.clavePublicaBase64(), "evt_7_X", 1731000000L, cuerpo, cab));
        assertFalse(FirmaWebhook.verificar(f.clavePublicaBase64(), "evt_7_X", 1731000001L, cuerpo, cab), "otra hora");
        assertFalse(FirmaWebhook.verificar(f.clavePublicaBase64(), "evt_7_X", 1731000000L, cuerpo + " ", cab), "otro cuerpo");
        assertThrows(IllegalArgumentException.class, () -> new FirmaWebhook(Base64.getEncoder().encodeToString(new byte[16])));
    }

    @Test
    void ulidOrdenadoYUnico() {
        String a = Ulid.nuevo(1_700_000_000_000L);
        String b = Ulid.nuevo(1_700_000_000_001L);
        assertEquals(26, a.length());
        assertTrue(a.compareTo(b) < 0);
        Set<String> s = new HashSet<>();
        for (int i = 0; i < 10000; i++) s.add(Ulid.nuevo());
        assertEquals(10000, s.size());
    }

    @Test
    void listaBlancaSacaLoQueNoEsta() {
        Map<String, Object> d = new HashMap<>();
        d.put("venta_id", 1);
        d.put("usuario_nickname", "juan");
        d.put("documento", "1234567");
        Map<String, Object> f = ListaBlanca.filtrar("venta.item_eliminado", d);
        assertEquals(Collections.singletonMap("venta_id", 1), f);
        assertTrue(ListaBlanca.filtrar("tipo.sin.lista", d).isEmpty());
    }

    @Test
    void sobreCloudEvents() throws Exception {
        ObjectMapper m = new ObjectMapper();
        EventosRepositorio.EntregaReclamada e = new EventosRepositorio.EntregaReclamada();
        e.eventoId = "evt_7_01JC";
        e.tipo = "venta.item_eliminado";
        e.sucursalId = 7L;
        e.secuencia = 42;
        e.sujeto = "venta/123";
        e.gravedad = "media";
        e.ocurridoEn = OffsetDateTime.parse("2026-11-08T12:30:00-03:00");
        e.datos = "{\"venta_id\":123}";
        JsonNode n = m.readTree(SobreCloudEvents.cuerpo(m, "bodega", Collections.singletonList(e), false));
        assertEquals("1.0", n.get("specversion").asText());
        assertEquals("evt_7_01JC", n.get("id").asText());
        assertEquals("frc://bodega/sucursal/7", n.get("source").asText());
        assertEquals("py.frc.venta.item_eliminado.v1", n.get("type").asText());
        assertEquals("2026-11-08T12:30:00-03:00", n.get("time").asText());
        assertEquals(42, n.get("frcsecuencia").asLong());
        assertEquals(123, n.get("data").get("venta_id").asInt());
        JsonNode lote = m.readTree(SobreCloudEvents.cuerpo(m, "bodega", Arrays.asList(e, e), true));
        assertTrue(lote.isArray());
        assertEquals(2, lote.size());
    }
}
