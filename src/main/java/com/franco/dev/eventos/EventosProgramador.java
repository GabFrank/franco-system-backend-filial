package com.franco.dev.eventos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Corre el reparto, el envio, el latido y la limpieza en dos schedulers propios de 1 hilo cada uno. No es un
 * bean TaskScheduler: si lo fuera, Spring dejaria de crear el pool de 4 que usan las demas tareas.
 */
@Component
public class EventosProgramador {

    private static final Logger log = LoggerFactory.getLogger(EventosProgramador.class);

    private final EventoEmisor emisor;
    private final EventoDespachador despachador;
    private final EventoEnviador enviador;
    private final EventosRepositorio repo;
    private final EventosProperties props;
    private final Long sucursalId;
    private ThreadPoolTaskScheduler scheduler;
    private ThreadPoolTaskScheduler mantenimiento;

    public EventosProgramador(EventoEmisor emisor, EventoDespachador despachador, EventoEnviador enviador,
                              EventosRepositorio repo, EventosProperties props,
                              @Value("${sucursalId:#{null}}") Long sucursalId) {
        this.emisor = emisor;
        this.despachador = despachador;
        this.enviador = enviador;
        this.repo = repo;
        this.props = props;
        this.sucursalId = sucursalId;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void arrancar() {
        if (!emisor.encendido()) {
            log.info("[eventos] webhooks apagados en este nodo (frc.webhooks.enabled=false o falta V111.3)");
            return;
        }
        // Dos hilos: uno para reparto y envio, otro para latido y limpieza. Una limpieza larga no frena el envio.
        scheduler = hilo("eventos-envio-");
        mantenimiento = hilo("eventos-mant-");
        scheduler.scheduleWithFixedDelay(() -> seguro("reparto", despachador::ciclo), Duration.ofSeconds(2));
        scheduler.scheduleWithFixedDelay(() -> seguro("envio", () -> enviador.ciclo(false)), Duration.ofSeconds(1));
        scheduler.scheduleWithFixedDelay(() -> seguro("cancelar", repo::cancelarDeInactivas), Duration.ofMinutes(1));
        mantenimiento.scheduleWithFixedDelay(() -> seguro("latido", this::latido), Duration.ofMinutes(10));
        mantenimiento.schedule(() -> seguro("limpieza", repo::limpiar), new CronTrigger("0 30 3 * * *"));
        log.info("[eventos] webhooks encendidos en la sucursal {}", sucursalId);
    }

    private static ThreadPoolTaskScheduler hilo(String prefijo) {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(1);
        s.setThreadNamePrefix(prefijo);
        s.setDaemon(true);
        s.initialize();
        return s;
    }

    @PreDestroy
    public void parar() {
        if (scheduler != null) scheduler.shutdown();
        if (mantenimiento != null) mantenimiento.shutdown();
    }

    /**
     * Actualiza eventos.nodo_estado (sube al central) y anota un nodo.latido en el outbox, que sale
     * por las suscripciones que lo piden.
     */
    public void latido() {
        Map<String, Object> r = repo.resumenEstado();
        long suc = sucursalId != null ? sucursalId : 0;
        repo.guardarNodoEstado(suc, emisor.encendido(), enviador.firmaConfigurada(), r, emisor.getFallosEmisor());
        Map<String, Object> datos = new HashMap<>();
        r.forEach((k, v) -> datos.put(k, v instanceof java.util.Date ? v.toString() : v));
        datos.put("emisor_encendido", emisor.encendido());
        datos.put("firma_configurada", enviador.firmaConfigurada());
        datos.put("fallos_emisor", emisor.getFallosEmisor());
        emisor.emitirEnNuevaTx("nodo.latido", "baja", null, "nodo/" + suc, datos);
    }

    private void seguro(String nombre, Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            log.warn("[eventos] {}: {}", nombre, e.toString());
        }
    }
}
