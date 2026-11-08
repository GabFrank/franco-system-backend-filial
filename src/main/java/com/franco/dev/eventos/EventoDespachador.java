package com.franco.dev.eventos;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

/**
 * Reparte cada evento del outbox entre las suscripciones que lo piden, fuera de la TX de la venta y
 * con la configuracion vigente en ese momento. Crea una entrega por suscripcion, con su secuencia.
 */
@Component
public class EventoDespachador {

    private final EventosRepositorio repo;
    private final TransactionTemplate tx;

    public EventoDespachador(EventosRepositorio repo, PlatformTransactionManager txManager) {
        this.repo = repo;
        this.tx = new TransactionTemplate(txManager);
    }

    /** Devuelve cuantos eventos despacho. */
    public int ciclo() {
        Integer n = tx.execute(st -> {
            List<EventosRepositorio.EventoPendiente> eventos = repo.tomarSinDespachar(200);
            if (eventos.isEmpty()) return 0;
            List<Suscripcion> suscripciones = repo.suscripcionesActivas();
            for (EventosRepositorio.EventoPendiente e : eventos) {
                for (Suscripcion s : suscripciones) {
                    if (ReglasEntrega.recibe(s, e.tipo, e.sucursalId, e.datos)) {
                        Map<String, Number> umbral = s.getUmbrales() == null ? null : s.getUmbrales().get(e.tipo);
                        repo.crearEntrega(e.id, s, umbral);
                    }
                }
                repo.marcarDespachado(e.id);
            }
            return eventos.size();
        });
        return n == null ? 0 : n;
    }
}
