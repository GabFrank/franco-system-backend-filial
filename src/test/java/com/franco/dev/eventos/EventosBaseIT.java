package com.franco.dev.eventos;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas contra un PostgreSQL real con la migracion V111.3. Se corren con
 * -Dit.eventos=true -Dit.eventos.url=jdbc:postgresql://localhost:5432/eventos_it (usuario franco).
 */
@EnabledIfSystemProperty(named = "it.eventos", matches = "true")
class EventosBaseIT {

    private DriverManagerDataSource ds;
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager txm;
    private TransactionTemplate tx;
    private EventosProperties props;
    private ObjectMapper mapper = new ObjectMapper();
    private EventosRepositorio repo;
    private EventoEmisor emisor;
    private EventoDespachador despachador;
    private EventoEnviador enviador;
    private String publica;

    @BeforeEach
    void preparar() throws Exception {
        ds = new DriverManagerDataSource(System.getProperty("it.eventos.url", "jdbc:postgresql://localhost:5432/eventos_it"), "franco", "franco");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("DROP SCHEMA IF EXISTS eventos CASCADE; DROP TABLE IF EXISTS configuraciones.webhook_suscripcion; DROP TABLE IF EXISTS public.venta_prueba");
        jdbc.execute(new String(Files.readAllBytes(Paths.get("src/main/resources/db/migration/V111.3__eventos_webhook.sql")), "UTF-8"));
        // Idempotente: se puede correr dos veces a mano en una filial
        jdbc.execute(new String(Files.readAllBytes(Paths.get("src/main/resources/db/migration/V111.3__eventos_webhook.sql")), "UTF-8"));
        jdbc.execute("CREATE TABLE public.venta_prueba (id int)");
        txm = new DataSourceTransactionManager(ds);
        tx = new TransactionTemplate(txm);
        props = new EventosProperties();
        props.setEnabled(true);
        props.setEstricto(true);
        byte[] semilla = new byte[32];
        semilla[0] = 7;
        props.setClavePrivada(Base64.getEncoder().encodeToString(semilla));
        publica = new FirmaWebhook(props.getClavePrivada()).clavePublicaBase64();
        repo = new EventosRepositorio(jdbc, mapper);
        emisor = new EventoEmisor(jdbc, props, mapper, txm, 7L);
        emisor.verificarEsquema();
        despachador = new EventoDespachador(repo, txm);
        enviador = new EventoEnviador(repo, props, mapper);
    }

    private long suscripcion(String eventos, String entrega) {
        return jdbc.queryForObject("INSERT INTO configuraciones.webhook_suscripcion (nombre, url, eventos, entrega, activo, latido, config_version)"
                + " VALUES ('prueba', 'https://93.184.215.14/hook', string_to_array(?, ','), ?, true, false, 1) RETURNING id", Long.class, eventos, entrega);
    }

    private void emitirEnTx(int n) {
        tx.executeWithoutResult(s -> {
            for (int i = 0; i < n; i++) {
                emisor.emitir("venta.item_eliminado", "media", 5L, "venta/" + i, Collections.singletonMap("venta_id", i));
            }
        });
    }

    private int contar(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void siElInsertFallaLaVentaSigue() {
        jdbc.execute("ALTER TABLE eventos.outbox_evento ADD CONSTRAINT no_falla CHECK (tipo <> 'falla')");
        tx.executeWithoutResult(s -> {
            jdbc.update("INSERT INTO public.venta_prueba VALUES (1)");
            emisor.emitir("falla", "alta", null, null, null);
            jdbc.update("INSERT INTO public.venta_prueba VALUES (2)");
        });
        assertEquals(2, contar("SELECT count(*) FROM public.venta_prueba"));
        assertEquals(0, contar("SELECT count(*) FROM eventos.outbox_evento"));
        assertEquals(1, emisor.getFallosEmisor());
    }

    @Test
    void siLaVentaSeRevierteNoQuedaAviso() {
        tx.executeWithoutResult(s -> {
            jdbc.update("INSERT INTO public.venta_prueba VALUES (1)");
            emisor.emitir("venta.item_eliminado", "media", null, "venta/1", Collections.singletonMap("venta_id", 1));
            s.setRollbackOnly();
        });
        assertEquals(0, contar("SELECT count(*) FROM eventos.outbox_evento"));
        tx.executeWithoutResult(s -> emisor.emitir("venta.item_eliminado", "media", null, "venta/1", Collections.singletonMap("venta_id", 1)));
        assertEquals(1, contar("SELECT count(*) FROM eventos.outbox_evento"));
    }

    @Test
    void sinTransaccionEnModoEstrictoFalla() {
        assertThrows(IllegalStateException.class, () -> emisor.emitir("venta.item_eliminado", "media", null, null, null));
    }

    @Test
    void secuenciaCorrelativaPorSuscripcionYFiltros() {
        long a = suscripcion("venta.*", null);
        long b = suscripcion("venta.item_eliminado", null);
        long c = suscripcion("caja.*", null);
        jdbc.update("UPDATE configuraciones.webhook_suscripcion SET sucursal_ids = '{3}' WHERE id = ?", b);
        emitirEnTx(5);
        assertEquals(5, despachador.ciclo());
        assertEquals(0, despachador.ciclo());
        List<Long> secA = jdbc.queryForList("SELECT secuencia FROM eventos.webhook_entrega WHERE suscripcion_id = ? ORDER BY secuencia", Long.class, a);
        assertEquals(java.util.Arrays.asList(1L, 2L, 3L, 4L, 5L), secA);
        assertEquals(0, contar("SELECT count(*) FROM eventos.webhook_entrega WHERE suscripcion_id = ?", b), "otra sucursal");
        assertEquals(0, contar("SELECT count(*) FROM eventos.webhook_entrega WHERE suscripcion_id = ?", c), "otro tipo");
    }

    @Test
    void secuenciaSinSaltosConDosDespachadoresALaVez() throws Exception {
        long a = suscripcion("*", null);
        emitirEnTx(300);
        CountDownLatch go = new CountDownLatch(1);
        List<Thread> hilos = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Thread t = new Thread(() -> {
                try {
                    go.await();
                    while (despachador.ciclo() > 0) { }
                } catch (Exception ignored) {
                }
            });
            t.start();
            hilos.add(t);
        }
        go.countDown();
        for (Thread t : hilos) t.join();
        assertEquals(300, contar("SELECT count(*) FROM eventos.webhook_entrega WHERE suscripcion_id = ?", a));
        assertEquals(300, contar("SELECT max(secuencia) FROM eventos.webhook_entrega WHERE suscripcion_id = ?", a));
        assertEquals(300, contar("SELECT count(DISTINCT secuencia) FROM eventos.webhook_entrega WHERE suscripcion_id = ?", a));
    }

    @Test
    void reclamoRespetaTopePorDestinoAunConDosHilos() throws Exception {
        long a = suscripcion("*", null);
        long b = suscripcion("*", null);
        emitirEnTx(6);
        despachador.ciclo();
        CountDownLatch go = new CountDownLatch(1);
        List<EventosRepositorio.EntregaReclamada> todas = new CopyOnWriteArrayList<>();
        List<Thread> hilos = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Thread t = new Thread(() -> {
                try {
                    go.await();
                    todas.addAll(repo.reclamar(false, 10, 2, 60));
                } catch (Exception ignored) {
                }
            });
            t.start();
            hilos.add(t);
        }
        go.countDown();
        for (Thread t : hilos) t.join();
        long deA = todas.stream().filter(e -> e.suscripcionId == a).count();
        long deB = todas.stream().filter(e -> e.suscripcionId == b).count();
        assertTrue(deA <= 2 && deB <= 2, "como mucho 2 por destino: " + deA + "/" + deB);
        assertEquals(todas.size(), todas.stream().map(e -> e.id).distinct().count(), "ninguna entrega reclamada dos veces");
        assertEquals(contar("SELECT count(*) FROM eventos.webhook_entrega WHERE estado = 'EN_CURSO'"), todas.size());
        // Con 2 en curso por destino no se toma ninguna mas
        assertTrue(repo.reclamar(false, 10, 2, 60).stream().noneMatch(e -> (e.suscripcionId == a && deA == 2) || (e.suscripcionId == b && deB == 2)));
    }

    @Test
    void cercoDelLease() {
        suscripcion("*", null);
        emitirEnTx(1);
        despachador.ciclo();
        EventosRepositorio.EntregaReclamada primero = repo.reclamar(false, 10, 2, 60).get(0);
        jdbc.update("UPDATE eventos.webhook_entrega SET lease_hasta = now() - interval '1 second'");
        EventosRepositorio.EntregaReclamada segundo = repo.reclamar(false, 10, 2, 60).get(0);
        assertEquals(primero.id, segundo.id);
        assertFalse(repo.resolver(primero, "ENVIADA", 200, null, 0), "el envio vencido no pisa al nuevo");
        assertTrue(repo.resolver(segundo, "ENVIADA", 200, null, 0));
        assertEquals(2, contar("SELECT intentos FROM eventos.webhook_entrega"));
    }

    @Test
    void conSecretoHmacFirmaV1aYV1() throws Exception {
        long id = suscripcion("*", null);
        String whsec = "whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw";
        props.getSecretosHmac().put(String.valueOf(id), whsec);
        emitirEnTx(1);
        despachador.ciclo();
        transporte(200);
        assertEquals(1, enviador.ciclo(true));
        Map<String, String> r = recibidas.get(0);
        String[] firmas = r.get("webhook-signature").split(" ");
        assertEquals(2, firmas.length);
        assertTrue(firmas[0].startsWith("v1a,"));
        long ts = Long.parseLong(r.get("webhook-timestamp"));
        assertTrue(FirmaWebhook.verificar(publica, r.get("webhook-id"), ts, r.get("_cuerpo"), r.get("webhook-signature")));
        assertEquals(FirmaWebhook.firmarHmac(whsec, r.get("webhook-id"), ts, r.get("_cuerpo")), firmas[1]);
        // Muestra para verificarla afuera con la libreria oficial (tools/swh)
        Map<String, String> muestra = new java.util.LinkedHashMap<>(r);
        muestra.put("_secreto", whsec);
        Files.write(Paths.get("target/swh-muestra.json"), mapper.writeValueAsBytes(muestra));
    }

    @Test
    void secretoHmacInvalidoNoGastaElIntento() {
        long id = suscripcion("*", null);
        props.getSecretosHmac().put(String.valueOf(id), "whsec_");
        emitirEnTx(1);
        despachador.ciclo();
        transporte(200);
        enviador.ciclo(true);
        assertEquals(0, recibidas.size());
        assertEquals(0, contar("SELECT count(*) FROM eventos.webhook_entrega WHERE estado = 'ENVIADA'"));
    }

    private List<Map<String, String>> recibidas = new CopyOnWriteArrayList<>();

    private void transporte(int status) {
        enviador.setTransporte((url, cab, cuerpo) -> {
            Map<String, String> c = new java.util.HashMap<>(cab);
            c.put("_cuerpo", cuerpo);
            recibidas.add(c);
            return new EventoEnviador.Respuesta(status, null);
        });
    }

    @Test
    void enviaFirmadoYMarcaEnviada() throws Exception {
        suscripcion("*", null);
        emitirEnTx(1);
        despachador.ciclo();
        transporte(200);
        assertEquals(1, enviador.ciclo(true));
        assertEquals(1, contar("SELECT count(*) FROM eventos.webhook_entrega WHERE estado = 'ENVIADA' AND enviado_en IS NOT NULL"));
        Map<String, String> r = recibidas.get(0);
        assertEquals(SobreCloudEvents.CT_UNO, r.get("Content-Type"));
        assertTrue(FirmaWebhook.verificar(publica, r.get("webhook-id"), Long.parseLong(r.get("webhook-timestamp")), r.get("_cuerpo"), r.get("webhook-signature")));
        Map<?, ?> ce = mapper.readValue(r.get("_cuerpo"), Map.class);
        assertEquals("py.frc.venta.item_eliminado.v1", ce.get("type"));
        assertEquals("frc://bodega/sucursal/7", ce.get("source"));
        assertEquals(r.get("webhook-id"), ce.get("id"));
        assertEquals(1, ((Number) ce.get("frcsecuencia")).intValue());
    }

    @Test
    void cabeceraFijaYFaltaDeSecretoNoPierdeNada() {
        long a = suscripcion("*", null);
        jdbc.update("UPDATE configuraciones.webhook_suscripcion SET cabecera_nombre = 'X-Loom-Secreto', secreto_ref = 'loom' WHERE id = ?", a);
        emitirEnTx(1);
        despachador.ciclo();
        transporte(200);
        enviador.ciclo(true);
        assertTrue(recibidas.isEmpty());
        assertEquals(1, contar("SELECT count(*) FROM eventos.webhook_entrega WHERE estado = 'REINTENTO' AND intentos = 0"));
        props.getSecretos().put("loom", "s3cr3t");
        jdbc.update("UPDATE eventos.webhook_entrega SET proximo_intento_en = now()");
        enviador.ciclo(true);
        assertEquals("s3cr3t", recibidas.get(0).get("X-Loom-Secreto"));
        assertEquals(1, contar("SELECT count(*) FROM eventos.webhook_entrega WHERE estado = 'ENVIADA'"));
    }

    @Test
    void sinClaveDeFirmaQuedaEnColaSinGastarIntentos() {
        suscripcion("*", null);
        emitirEnTx(1);
        despachador.ciclo();
        props.setClavePrivada(null);
        transporte(200);
        for (int i = 0; i < 3; i++) {
            jdbc.update("UPDATE eventos.webhook_entrega SET proximo_intento_en = now()");
            enviador.ciclo(true);
        }
        assertTrue(recibidas.isEmpty());
        assertEquals(1, contar("SELECT count(*) FROM eventos.webhook_entrega WHERE estado = 'REINTENTO' AND intentos = 0"));
    }

    @Test
    void codigosHttpYCircuitBreaker() {
        long a = suscripcion("*", null);
        emitirEnTx(1);
        despachador.ciclo();
        transporte(503);
        enviador.ciclo(true);
        assertEquals(1, contar("SELECT count(*) FROM eventos.webhook_entrega WHERE estado = 'REINTENTO' AND ultimo_status_http = 503"
                + " AND proximo_intento_en > now() + interval '5 seconds'"));
        for (int i = 0; i < 9; i++) {
            jdbc.update("UPDATE eventos.webhook_entrega SET proximo_intento_en = now()");
            enviador.ciclo(true);
        }
        assertEquals(10, contar("SELECT fallos_seguidos FROM eventos.destino_estado WHERE suscripcion_id = ?", a));
        assertEquals(1, contar("SELECT count(*) FROM eventos.destino_estado WHERE pausado_hasta > now() + interval '14 minutes'"));
        jdbc.update("UPDATE eventos.webhook_entrega SET proximo_intento_en = now()");
        int antes = recibidas.size();
        enviador.ciclo(true);
        assertEquals(antes, recibidas.size(), "en pausa no se envia");
        // Se levanta la pausa y responde 200: se resetea
        jdbc.update("UPDATE eventos.destino_estado SET pausado_hasta = now()");
        transporte(200);
        enviador.ciclo(true);
        assertEquals(0, contar("SELECT fallos_seguidos FROM eventos.destino_estado WHERE suscripcion_id = ?", a));
        assertEquals(1, contar("SELECT count(*) FROM eventos.webhook_entrega WHERE estado = 'ENVIADA'"));
    }

    @Test
    void descarta4xxYPausaCon410() {
        long a = suscripcion("*", null);
        emitirEnTx(2);
        despachador.ciclo();
        transporte(422);
        enviador.ciclo(true);
        assertEquals(2, contar("SELECT count(*) FROM eventos.webhook_entrega WHERE estado = 'DESCARTADA'"));
        emitirEnTx(1);
        despachador.ciclo();
        transporte(410);
        enviador.ciclo(true);
        assertEquals(1, contar("SELECT count(*) FROM eventos.destino_estado WHERE pausado_por_410 AND suscripcion_id = ?", a));
        assertEquals(1, contar("SELECT count(*) FROM eventos.webhook_entrega WHERE estado = 'REINTENTO'"), "no se pierde");
    }

    @Test
    void errorDeRedReintentaHasta72HorasYDespuesDescarta() {
        suscripcion("*", null);
        emitirEnTx(1);
        despachador.ciclo();
        enviador.setTransporte((u, c, b) -> { throw new java.net.ConnectException("sin internet"); });
        for (int i = 0; i < 8; i++) {
            jdbc.update("UPDATE eventos.webhook_entrega SET proximo_intento_en = now(); UPDATE eventos.destino_estado SET pausado_hasta = NULL");
            enviador.ciclo(true);
        }
        assertEquals(1, contar("SELECT count(*) FROM eventos.webhook_entrega WHERE estado = 'REINTENTO' AND intentos = 8 AND primer_intento_en IS NOT NULL"));
        // A las 71 h sigue viva y la limpieza no la toca
        jdbc.update("UPDATE eventos.webhook_entrega SET primer_intento_en = now() - interval '71 hours', creado_en = now() - interval '71 hours'");
        repo.limpiar();
        jdbc.update("UPDATE eventos.webhook_entrega SET proximo_intento_en = now(); UPDATE eventos.destino_estado SET pausado_hasta = NULL");
        enviador.ciclo(true);
        assertEquals(1, contar("SELECT count(*) FROM eventos.webhook_entrega WHERE estado = 'REINTENTO'"));
        // Pasadas las 72 h, el siguiente fallo la descarta
        jdbc.update("UPDATE eventos.webhook_entrega SET primer_intento_en = now() - interval '73 hours', proximo_intento_en = now();"
                + " UPDATE eventos.destino_estado SET pausado_hasta = NULL");
        enviador.ciclo(true);
        assertEquals(1, contar("SELECT count(*) FROM eventos.webhook_entrega WHERE estado = 'DESCARTADA' AND ultimo_error LIKE 'Vencida a las 72 h%'"));
    }

    @Test
    void siVuelveLaRedAntesDe72HorasSale() {
        suscripcion("*", null);
        emitirEnTx(1);
        despachador.ciclo();
        enviador.setTransporte((u, c, b) -> { throw new java.net.ConnectException("sin internet"); });
        enviador.ciclo(true);
        jdbc.update("UPDATE eventos.webhook_entrega SET primer_intento_en = now() - interval '60 hours', proximo_intento_en = now()");
        transporte(200);
        enviador.ciclo(true);
        assertEquals(1, contar("SELECT count(*) FROM eventos.webhook_entrega WHERE estado = 'ENVIADA'"));
    }

    @Test
    void credencialRotadaOUrl404SeReintentan() {
        suscripcion("*", null);
        emitirEnTx(2);
        despachador.ciclo();
        transporte(401);
        enviador.ciclo(true);
        jdbc.update("UPDATE eventos.webhook_entrega SET proximo_intento_en = now()");
        transporte(404);
        enviador.ciclo(true);
        assertEquals(2, contar("SELECT count(*) FROM eventos.webhook_entrega WHERE estado = 'REINTENTO' AND ultimo_status_http = 404"));
        jdbc.update("UPDATE eventos.webhook_entrega SET proximo_intento_en = now()");
        transporte(200);
        enviador.ciclo(true);
        assertEquals(2, contar("SELECT count(*) FROM eventos.webhook_entrega WHERE estado = 'ENVIADA'"));
    }

    @Test
    void repartoSinSuscripcionesMarcaYNoCreaEntregas() {
        emitirEnTx(3);
        assertEquals(3, despachador.ciclo());
        assertEquals(0, contar("SELECT count(*) FROM eventos.webhook_entrega"));
        assertEquals(0, contar("SELECT count(*) FROM eventos.outbox_evento WHERE repartido_en IS NULL"));
    }

    @Test
    void emitirEnNuevaTxSinTransaccionDeAfuera() {
        emisor.emitirEnNuevaTx("venta.item_eliminado", "media", null, "venta/1", Collections.singletonMap("venta_id", 1));
        assertEquals(1, contar("SELECT count(*) FROM eventos.outbox_evento"));
        jdbc.execute("ALTER TABLE eventos.outbox_evento ADD CONSTRAINT no_falla CHECK (tipo <> 'falla')");
        emisor.emitirEnNuevaTx("falla", "media", null, null, null); // no lanza
        assertEquals(1, emisor.getFallosEmisor());
    }

    @Test
    void receptorLentoNoFrenaALosDemas() throws Exception {
        long lento = suscripcion("*", null);
        long rapido = suscripcion("*", null);
        jdbc.update("UPDATE configuraciones.webhook_suscripcion SET url = 'https://93.184.215.15/lento' WHERE id = ?", lento);
        emitirEnTx(10);
        despachador.ciclo();
        CountDownLatch soltar = new CountDownLatch(1);
        AtomicInteger lentosEnVuelo = new AtomicInteger();
        AtomicInteger maxLentos = new AtomicInteger();
        AtomicInteger rapidos = new AtomicInteger();
        enviador.setTransporte((url, cab, cuerpo) -> {
            if (url.contains("lento")) {
                maxLentos.accumulateAndGet(lentosEnVuelo.incrementAndGet(), Math::max);
                soltar.await();
                lentosEnVuelo.decrementAndGet();
            } else {
                rapidos.incrementAndGet();
            }
            return new EventoEnviador.Respuesta(200, null);
        });
        for (int i = 0; i < 8; i++) enviador.ciclo(false);
        long fin = System.currentTimeMillis() + 5000;
        while (rapidos.get() < 10 && System.currentTimeMillis() < fin) {
            enviador.ciclo(false);
            Thread.sleep(20);
        }
        assertEquals(10, rapidos.get(), "el rapido recibio todo mientras el lento colgaba");
        assertEquals(2, maxLentos.get(), "el lento ocupa como mucho 2 envios");
        soltar.countDown();
    }

    @Test
    void entregaPorLote() throws Exception {
        long a = suscripcion("*", "lote");
        emitirEnTx(5);
        despachador.ciclo();
        transporte(200);
        assertEquals(1, enviador.ciclo(true));
        Map<String, String> r = recibidas.get(0);
        assertEquals(SobreCloudEvents.CT_LOTE, r.get("Content-Type"));
        assertEquals(5, mapper.readValue(r.get("_cuerpo"), List.class).size());
        assertTrue(FirmaWebhook.verificar(publica, r.get("webhook-id"), Long.parseLong(r.get("webhook-timestamp")), r.get("_cuerpo"), r.get("webhook-signature")));
        assertEquals(5, contar("SELECT count(*) FROM eventos.webhook_entrega WHERE estado = 'ENVIADA' AND suscripcion_id = ?", a));
    }

    @Test
    void suscripcionDesactivadaCancelaLoPendiente() {
        long a = suscripcion("*", null);
        emitirEnTx(3);
        despachador.ciclo();
        jdbc.update("UPDATE configuraciones.webhook_suscripcion SET activo = false WHERE id = ?", a);
        assertEquals(3, repo.cancelarDeInactivas());
        assertEquals(3, contar("SELECT count(*) FROM eventos.webhook_entrega WHERE estado = 'CANCELADA'"));
    }

    @Test
    void latidoYPrueba() {
        long a = suscripcion("venta.*", null);
        jdbc.update("UPDATE configuraciones.webhook_suscripcion SET latido = true WHERE id = ?", a);
        EventosProgramador p = new EventosProgramador(emisor, despachador, enviador, repo, props, 7L);
        p.latido();
        assertEquals(1, contar("SELECT count(*) FROM eventos.nodo_estado WHERE sucursal_id = 7 AND emisor_encendido AND firma_configurada"));
        despachador.ciclo();
        assertEquals(1, contar("SELECT count(*) FROM eventos.webhook_entrega e JOIN eventos.outbox_evento o ON o.id = e.outbox_id WHERE o.tipo = 'nodo.latido'"));
        assertTrue(repo.crearPrueba(a, emisor.eventoIdNuevo(), 7L));
        assertEquals(2, contar("SELECT max(secuencia) FROM eventos.webhook_entrega WHERE suscripcion_id = ?", a));
    }

    @Test
    void elLockDelReclamoSeSueltaAunqueFalle() throws Exception {
        suscripcion("*", null);
        emitirEnTx(1);
        despachador.ciclo();
        assertThrows(Exception.class, () -> repo.reclamar(false, -1, 2, 60), "LIMIT negativo hace fallar el reclamo");
        assertEquals(0, contar("SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND objid = ?", (int) (EventosRepositorio.LOCK_RECLAMO & 0xffffffffL)));
        // Otro hilo puede reclamar enseguida (si el lock hubiera quedado tomado, se colgaria)
        java.util.concurrent.Future<List<EventosRepositorio.EntregaReclamada>> f =
                java.util.concurrent.Executors.newSingleThreadExecutor().submit(() -> repo.reclamar(false, 10, 2, 60));
        assertEquals(1, f.get(5, java.util.concurrent.TimeUnit.SECONDS).size());
        assertEquals(0, contar("SELECT count(*) FROM pg_locks WHERE locktype = 'advisory'"));
    }

    @Test
    void vencidaA72HorasNoSeVuelveAIntentar() {
        suscripcion("*", null);
        emitirEnTx(1);
        despachador.ciclo();
        jdbc.update("UPDATE eventos.webhook_entrega SET estado = 'REINTENTO', intentos = 20, primer_intento_en = now() - interval '73 hours'");
        transporte(200);
        assertEquals(0, enviador.ciclo(true));
        assertTrue(recibidas.isEmpty(), "pasadas las 72 h no sale aunque el receptor ya ande");
        assertEquals(1, contar("SELECT count(*) FROM eventos.webhook_entrega WHERE estado = 'DESCARTADA' AND ultimo_error LIKE 'Vencida a las 72 h%'"));
    }

    @Test
    void limpiezaConDescartadasYSinFallarPorFk() {
        suscripcion("*", null);
        emitirEnTx(3);
        despachador.ciclo();
        jdbc.update("UPDATE eventos.outbox_evento SET repartido_en = now() - interval '40 days'");
        jdbc.update("UPDATE eventos.webhook_entrega SET estado = 'DESCARTADA', creado_en = now() - interval '10 days' WHERE secuencia = 1");
        jdbc.update("UPDATE eventos.webhook_entrega SET estado = 'DESCARTADA', creado_en = now() - interval '31 days' WHERE secuencia = 2");
        jdbc.update("UPDATE eventos.webhook_entrega SET estado = 'ENVIADA', creado_en = now() - interval '8 days' WHERE secuencia = 3");
        repo.limpiar();
        assertEquals(1, contar("SELECT count(*) FROM eventos.webhook_entrega"), "queda la DESCARTADA de 10 dias");
        assertEquals(1, contar("SELECT count(*) FROM eventos.outbox_evento o JOIN eventos.webhook_entrega e ON e.outbox_id = o.id"),
                "y su outbox, aunque este repartido hace 40 dias");
        assertEquals(1, contar("SELECT count(*) FROM eventos.outbox_evento"));
    }

    @Test
    void limpiezaNoTocaLoVivo() {
        suscripcion("*", null);
        emitirEnTx(2);
        despachador.ciclo();
        jdbc.update("UPDATE eventos.webhook_entrega SET estado = 'ENVIADA', creado_en = now() - interval '8 days' WHERE secuencia = 1");
        jdbc.update("UPDATE eventos.webhook_entrega SET creado_en = now() - interval '40 days' WHERE secuencia = 2");
        jdbc.update("UPDATE eventos.outbox_evento SET repartido_en = now() - interval '2 days'");
        repo.limpiar();
        assertEquals(1, contar("SELECT count(*) FROM eventos.webhook_entrega"));
        assertEquals(1, contar("SELECT count(*) FROM eventos.outbox_evento"));
    }
}
