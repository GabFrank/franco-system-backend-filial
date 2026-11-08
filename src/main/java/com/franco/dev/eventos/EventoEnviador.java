package com.franco.dev.eventos;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manda las entregas reclamadas. Hasta maxConcurrentes envios del nodo a la vez y hasta
 * maxPorDestino por suscripcion (el tope lo pone el reclamo en la base). Cada intento se firma de
 * nuevo con la hora actual. No se siguen redirecciones.
 */
@Component
public class EventoEnviador {

    private static final Logger log = LoggerFactory.getLogger(EventoEnviador.class);

    /** Respuesta minima del transporte, para poder probar sin red. */
    public static class Respuesta {
        public final int status;
        public final Long retryAfterSeg;

        public Respuesta(int status, Long retryAfterSeg) {
            this.status = status;
            this.retryAfterSeg = retryAfterSeg;
        }
    }

    public interface Transporte {
        Respuesta post(String url, Map<String, String> cabeceras, String cuerpo) throws Exception;
    }

    private final EventosRepositorio repo;
    private final EventosProperties props;
    private final ObjectMapper mapper;
    private final ExecutorService pool;
    private final AtomicInteger enVuelo = new AtomicInteger();
    private Transporte transporte;
    private volatile FirmaWebhook firma;
    private volatile String semillaCargada;

    public EventoEnviador(EventosRepositorio repo, EventosProperties props, ObjectMapper mapper) {
        this.repo = repo;
        this.props = props;
        this.mapper = mapper;
        this.pool = Executors.newFixedThreadPool(Math.max(1, props.getMaxConcurrentes()), r -> {
            Thread t = new Thread(r, "eventos-envio");
            t.setDaemon(true);
            return t;
        });
        this.transporte = transporteNetty(props);
    }

    /**
     * Reactor Netty (ya viene con webflux) con ResolverSeguro: la IP se valida al conectar, no antes.
     * Sin redirecciones (es el default de Reactor Netty) y con timeout de conexion y de respuesta.
     */
    static Transporte transporteNetty(EventosProperties props) {
        reactor.netty.http.client.HttpClient http = reactor.netty.http.client.HttpClient.create()
                .resolver(new ResolverSeguro())
                .followRedirect(false)
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, props.getTimeoutMs())
                .responseTimeout(Duration.ofMillis(props.getTimeoutMs()));
        return (url, cab, cuerpo) -> {
            Respuesta r = http.headers(h -> cab.forEach(h::set))
                    .post()
                    .uri(url)
                    .send(reactor.netty.ByteBufFlux.fromString(reactor.core.publisher.Mono.just(cuerpo)))
                    .responseSingle((resp, body) -> body.asByteArray().defaultIfEmpty(new byte[0]).map(ignorado -> {
                        String v = resp.responseHeaders().get("Retry-After");
                        Long ra = null;
                        if (v != null) {
                            try {
                                ra = Long.parseLong(v.trim());
                            } catch (NumberFormatException ignored) {
                            }
                        }
                        return new Respuesta(resp.status().code(), ra);
                    }))
                    .block(Duration.ofMillis(props.getTimeoutMs() + 1000L));
            if (r == null) throw new java.io.IOException("Sin respuesta");
            return r;
        };
    }

    void setTransporte(Transporte t) {
        this.transporte = t;
    }

    public boolean firmaConfigurada() {
        return firma() != null;
    }

    private FirmaWebhook firma() {
        String s = props.getClavePrivada();
        if (s == null || s.trim().isEmpty()) return null;
        if (!s.equals(semillaCargada)) {
            try {
                firma = new FirmaWebhook(s);
                semillaCargada = s;
            } catch (Exception e) {
                log.warn("[eventos] frc.webhooks.clave-privada invalida: {}", e.getMessage());
                return null;
            }
        }
        return firma;
    }

    /** Un ciclo: reclama lo que entra en los hilos libres y lo manda. Devuelve cuantos envios lanzo. */
    public int ciclo(boolean esperar) {
        int libres = props.getMaxConcurrentes() - enVuelo.get();
        if (libres <= 0) return 0;
        List<List<EventosRepositorio.EntregaReclamada>> envios = new ArrayList<>();
        for (EventosRepositorio.EntregaReclamada e : repo.reclamar(false, libres, props.getMaxPorDestino(), props.getLeaseSegundos())) {
            List<EventosRepositorio.EntregaReclamada> uno = new ArrayList<>();
            uno.add(e);
            envios.add(uno);
        }
        if (envios.size() < libres) {
            Map<Long, List<EventosRepositorio.EntregaReclamada>> porSuscripcion = new LinkedHashMap<>();
            for (EventosRepositorio.EntregaReclamada e : repo.reclamar(true, props.getTamanoLote(), props.getMaxPorDestino(), props.getLeaseSegundos())) {
                porSuscripcion.computeIfAbsent(e.suscripcionId, k -> new ArrayList<>()).add(e);
            }
            envios.addAll(porSuscripcion.values());
        }
        List<java.util.concurrent.Future<?>> futuros = new ArrayList<>();
        for (List<EventosRepositorio.EntregaReclamada> envio : envios) {
            enVuelo.incrementAndGet();
            futuros.add(pool.submit(() -> {
                try {
                    enviar(envio);
                } catch (Exception ex) {
                    log.warn("[eventos] error enviando: {}", ex.toString());
                } finally {
                    enVuelo.decrementAndGet();
                }
            }));
        }
        if (esperar) {
            for (java.util.concurrent.Future<?> f : futuros) {
                try {
                    f.get();
                } catch (Exception ignored) {
                }
            }
        }
        return envios.size();
    }

    void enviar(List<EventosRepositorio.EntregaReclamada> envio) {
        EventosRepositorio.EntregaReclamada primera = envio.get(0);
        Suscripcion s = repo.suscripcion(primera.suscripcionId);
        if (s == null || !s.isActivo()) {
            for (EventosRepositorio.EntregaReclamada e : envio) repo.resolver(e, "CANCELADA", null, "Suscripcion inactiva", 0);
            return;
        }
        String motivo = DestinoSeguro.validar(s.getUrl(), props.getHostsPermitidos());
        if (motivo != null) {
            for (EventosRepositorio.EntregaReclamada e : envio) repo.resolver(e, "DESCARTADA", null, motivo, 0);
            repo.registrarFallo(s.getId(), motivo);
            return;
        }
        Map<String, String> cab = new LinkedHashMap<>();
        // Cabecera fija (plataformas que autentican asi, como Loom). Sin el secreto cargado no se
        // manda: vuelve a la cola sin gastar el intento, para no perder nada mientras se arregla.
        if (s.getCabeceraNombre() != null && !s.getCabeceraNombre().isEmpty()) {
            String valor = s.getSecretoRef() == null ? null : props.getSecretos().get(s.getSecretoRef());
            if (valor == null) {
                for (EventosRepositorio.EntregaReclamada e : envio) repo.devolverSinGastar(e, "Falta el secreto " + s.getSecretoRef(), 300);
                return;
            }
            cab.put(s.getCabeceraNombre(), valor);
        }
        FirmaWebhook f = firma();
        if (f == null) {
            for (EventosRepositorio.EntregaReclamada e : envio) repo.devolverSinGastar(e, "Falta frc.webhooks.clave-privada", 300);
            return;
        }
        boolean lote = envio.size() > 1 || s.porLote();
        String cuerpo = SobreCloudEvents.cuerpo(mapper, props.getEmpresa(), envio, lote);
        String webhookId = lote ? "lote_" + primera.eventoId + "_" + envio.size() : primera.eventoId;
        long ts = System.currentTimeMillis() / 1000;
        cab.put("Content-Type", lote ? SobreCloudEvents.CT_LOTE : SobreCloudEvents.CT_UNO);
        cab.put("webhook-id", webhookId);
        cab.put("webhook-timestamp", String.valueOf(ts));
        cab.put("webhook-signature", f.firmar(webhookId, ts, cuerpo));
        if (props.getClaveId() != null) cab.put("frc-key-id", props.getClaveId());
        cab.put("frc-intento", String.valueOf(primera.intentos));

        Respuesta r;
        try {
            r = transporte.post(s.getUrl(), cab, cuerpo);
        } catch (Exception ex) {
            String err = ex.getClass().getSimpleName() + (ex.getMessage() != null ? ": " + ex.getMessage() : "");
            for (EventosRepositorio.EntregaReclamada e : envio) {
                repo.resolver(e, "REINTENTO", null, err, ReglasEntrega.esperaSegundos(e.intentos, null));
            }
            repo.registrarFallo(s.getId(), err);
            return;
        }
        ReglasEntrega.Resultado res = ReglasEntrega.clasificar(r.status);
        for (EventosRepositorio.EntregaReclamada e : envio) {
            switch (res) {
                case ENVIADA:
                    repo.resolver(e, "ENVIADA", r.status, null, 0);
                    break;
                case DESCARTADA:
                    repo.resolver(e, "DESCARTADA", r.status, "HTTP " + r.status, 0);
                    break;
                case PAUSAR_410:
                    // Queda viva: sale si se reactiva el destino
                    repo.resolver(e, "REINTENTO", r.status, "HTTP 410", 3600);
                    break;
                default:
                    repo.resolver(e, "REINTENTO", r.status, "HTTP " + r.status, ReglasEntrega.esperaSegundos(e.intentos, r.retryAfterSeg));
            }
        }
        if (res == ReglasEntrega.Resultado.ENVIADA) repo.registrarExito(s.getId());
        else if (res == ReglasEntrega.Resultado.PAUSAR_410) repo.pausarPor410(s.getId());
        else repo.registrarFallo(s.getId(), "HTTP " + r.status);
    }
}
