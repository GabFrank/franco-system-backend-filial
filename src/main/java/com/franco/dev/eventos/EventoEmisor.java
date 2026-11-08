package com.franco.dev.eventos;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.PreparedStatement;
import java.sql.Savepoint;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Anota un hecho en eventos.outbox_evento dentro de la transaccion de la operacion: si la operacion
 * se confirma, el aviso tambien; si se revierte, no queda nada.
 *
 * Emitir nunca rompe la operacion. El INSERT va por JDBC sobre la misma conexion de la TX (la que
 * JpaTransactionManager expone al DataSource) y dentro de un savepoint: si falla, se vuelve al
 * savepoint, se cuenta en fallosEmisor y la operacion sigue. No se usa el EntityManager: una
 * excepcion de Hibernate dejaria toda la TX marcada para rollback.
 */
@Component
public class EventoEmisor {

    private static final Logger log = LoggerFactory.getLogger(EventoEmisor.class);

    static final String INSERT = "INSERT INTO eventos.outbox_evento "
            + "(evento_id, tipo, version_sobre, gravedad, sucursal_id, usuario_id, sujeto, datos, link) "
            + "VALUES (?, ?, 1, ?, ?, ?, ?, CAST(? AS jsonb), ?)";

    private final JdbcTemplate jdbc;
    private final EventosProperties props;
    private final ObjectMapper mapper;
    private final TransactionTemplate txPropia;
    private final Long sucursalId;
    private final AtomicLong fallosEmisor = new AtomicLong();
    private volatile boolean esquemaPresente = false;

    public EventoEmisor(JdbcTemplate jdbc, EventosProperties props, ObjectMapper mapper,
                        PlatformTransactionManager txManager,
                        @Value("${sucursalId:#{null}}") Long sucursalId) {
        this.jdbc = jdbc;
        this.props = props;
        this.mapper = mapper;
        this.sucursalId = sucursalId;
        this.txPropia = new TransactionTemplate(txManager);
        this.txPropia.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verificarEsquema() {
        try {
            Integer n = jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema='eventos' AND table_name='outbox_evento'",
                    Integer.class);
            esquemaPresente = n != null && n > 0;
        } catch (Exception e) {
            esquemaPresente = false;
        }
        if (props.isEnabled() && !esquemaPresente) {
            log.warn("[eventos] frc.webhooks.enabled=true pero falta el esquema eventos (V111.3): el emisor queda apagado");
        }
    }

    public boolean encendido() {
        return props.isEnabled() && esquemaPresente;
    }

    public String eventoIdNuevo() {
        return "evt_" + (sucursalId != null ? sucursalId : 0) + "_" + Ulid.nuevo();
    }

    public Long getSucursalId() {
        return sucursalId;
    }

    public long getFallosEmisor() {
        return fallosEmisor.get();
    }

    /**
     * @param tipo     tipo corto, p. ej. "venta.item_eliminado"
     * @param gravedad "alta", "media" o "baja"
     * @param sujeto   entidad, p. ej. "venta/123"
     * @param datos    valores crudos del hecho (solo campos de la lista blanca del tipo)
     */
    public void emitir(String tipo, String gravedad, Long usuarioId, String sujeto, Map<String, Object> datos) {
        if (!encendido()) return;
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            if (props.isEstricto()) {
                throw new IllegalStateException("EventoEmisor.emitir sin transaccion activa: " + tipo);
            }
            log.warn("[eventos] {} emitido sin transaccion activa: se anota en una transaccion propia", tipo);
            txPropia.executeWithoutResult(s -> insertarConSavepoint(tipo, gravedad, usuarioId, sujeto, datos));
            return;
        }
        insertarConSavepoint(tipo, gravedad, usuarioId, sujeto, datos);
    }

    /** Para hechos del propio nodo que no vienen de una operacion (el latido). */
    public void emitirEnTxPropia(String tipo, String gravedad, Long usuarioId, String sujeto, Map<String, Object> datos) {
        if (!encendido()) return;
        txPropia.executeWithoutResult(s -> insertarConSavepoint(tipo, gravedad, usuarioId, sujeto, datos));
    }

    void insertarConSavepoint(String tipo, String gravedad, Long usuarioId, String sujeto, Map<String, Object> datos) {
        final String json;
        try {
            json = mapper.writeValueAsString(ListaBlanca.filtrar(tipo, datos));
        } catch (Exception e) {
            fallosEmisor.incrementAndGet();
            log.warn("[eventos] no se pudo serializar {}: {}", tipo, e.toString());
            return;
        }
        String eventoId = eventoIdNuevo();
        String link = props.getLinkBase() != null && sujeto != null ? props.getLinkBase() + "/" + sujeto : null;
        try {
            jdbc.execute((ConnectionCallback<Void>) con -> {
                Savepoint sp = con.setSavepoint();
                try (PreparedStatement ps = con.prepareStatement(INSERT)) {
                    ps.setString(1, eventoId);
                    ps.setString(2, tipo);
                    ps.setString(3, gravedad);
                    ps.setObject(4, sucursalId);
                    ps.setObject(5, usuarioId);
                    ps.setString(6, sujeto);
                    ps.setString(7, json);
                    ps.setString(8, link);
                    ps.executeUpdate();
                    con.releaseSavepoint(sp);
                } catch (Exception e) {
                    con.rollback(sp);
                    throw e;
                }
                return null;
            });
        } catch (Exception e) {
            fallosEmisor.incrementAndGet();
            log.warn("[eventos] no se pudo anotar {} (la operacion sigue): {}", tipo, e.toString());
        }
    }
}
