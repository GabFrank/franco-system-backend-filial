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
            + "(evento_id, tipo, version_evento, gravedad, sucursal_id, usuario_id, sujeto, datos, link, atributos) "
            + "VALUES (?, ?, 1, ?, ?, ?, ?, CAST(? AS jsonb), ?, CAST(? AS jsonb))";

    /** Cada cuanto se relee que tipos tienen suscriptores (ms). Una suscripcion nueva vale desde ahi. */
    static final long REFRESCO_SUSCRIPTORES_MS = 10_000;

    private final JdbcTemplate jdbc;
    private final EventosProperties props;
    private final ObjectMapper mapper;
    private final TransactionTemplate txPropia;
    private final Long sucursalId;
    private final AtomicLong fallosEmisor = new AtomicLong();
    private volatile boolean esquemaPresente = false;
    private volatile Suscriptores suscriptores = null;
    private volatile long refrescoMs = REFRESCO_SUSCRIPTORES_MS;

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

    /** Para los tests: cada cuanto se relee la lista de suscriptores (0 = en cada emision). */
    void setRefrescoMs(long ms) {
        this.refrescoMs = ms;
        this.suscriptores = null;
    }

    /** Lo que piden las suscripciones activas, por tipo y sucursal: quien recibe y si quiere lo personal o sensible. */
    static final class Suscriptores {
        final long leidoEn;
        final java.util.List<Object[]> filas; // {String[] eventos, Long[] sucursales, Boolean personales, Boolean sensibles}

        Suscriptores(long leidoEn, java.util.List<Object[]> filas) {
            this.leidoEn = leidoEn;
            this.filas = filas;
        }
    }

    /** {hay suscriptor, alguno quiere lo personal, alguno quiere lo sensible} para el tipo en esta sucursal. */
    boolean[] pedidoPara(String tipo) {
        Suscriptores sc = suscriptores;
        long ahora = System.currentTimeMillis();
        if (sc == null || ahora - sc.leidoEn >= refrescoMs) {
            try {
                java.util.List<Object[]> filas = jdbc.query(
                        "SELECT eventos, eventos_pausados, sucursal_ids, datos_personales, "
                                + (columnaSensibles() ? "datos_sensibles" : "NULL::boolean AS datos_sensibles")
                                + " FROM configuraciones.webhook_suscripcion WHERE activo IS TRUE AND url IS NOT NULL",
                        (rs, i) -> new Object[]{arr(rs.getArray(1)), arr(rs.getArray(2)), arr(rs.getArray(3)),
                                rs.getObject(4), rs.getObject(5)});
                sc = new Suscriptores(ahora, filas);
            } catch (Exception e) {
                // Sin poder leer: se emite completo, como en la 1a (mejor anotar de mas que perder el hecho)
                return new boolean[]{true, true, true};
            }
            suscriptores = sc;
        }
        boolean hay = false, per = false, sen = false;
        for (Object[] f : sc.filas) {
            Object[] eventos = (Object[]) f[0];
            Object[] pausados = (Object[]) f[1];
            Object[] sucs = (Object[]) f[2];
            if (!algunoCoincide(eventos, tipo) || algunoCoincide(pausados, tipo)) continue;
            if (sucs != null && sucs.length > 0 && !contiene(sucs, sucursalId)) continue;
            hay = true;
            per |= Boolean.TRUE.equals(f[3]);
            sen |= Boolean.TRUE.equals(f[4]);
        }
        return new boolean[]{hay, per, sen};
    }

    private volatile Boolean tieneColumnaSensibles = null;

    private boolean columnaSensibles() {
        Boolean b = tieneColumnaSensibles;
        if (b == null) {
            Integer n = jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema='configuraciones'"
                    + " AND table_name='webhook_suscripcion' AND column_name='datos_sensibles'", Integer.class);
            b = n != null && n > 0;
            tieneColumnaSensibles = b;
        }
        return b;
    }

    private static Object[] arr(java.sql.Array a) throws java.sql.SQLException {
        return a == null ? null : (Object[]) a.getArray();
    }

    private static boolean algunoCoincide(Object[] patrones, String tipo) {
        if (patrones == null) return false;
        for (Object p : patrones) if (p != null && ReglasEntrega.coincideTipo(String.valueOf(p), tipo)) return true;
        return false;
    }

    private static boolean contiene(Object[] sucs, Long id) {
        if (id == null) return false;
        for (Object o : sucs) if (o instanceof Number && ((Number) o).longValue() == id) return true;
        return false;
    }

    /**
     * Emision de los tipos con proyeccion (fase 2). Si el emisor esta apagado o nadie esta suscripto a
     * ese tipo en esta sucursal, no se arma nada: los suppliers no se llaman. Lo personal y lo sensible
     * se guardan solo si alguna suscripcion los pide. Nunca lanza.
     */
    public void emitirProyeccion(String tipo, String gravedad, Long usuarioId, String sujeto,
                                 java.util.function.Supplier<Map<String, Object>> datos,
                                 java.util.function.Supplier<Map<String, Object>> atributos) {
        if (!encendido()) return;
        try {
            boolean[] p = pedidoPara(tipo);
            if (!p[0]) return;
            Map<String, Object> d = Proyeccion.limpiar(tipo, datos.get(), p[1], p[2]);
            Map<String, Object> a = atributos == null ? null : atributos.get();
            emitirConAtributos(tipo, gravedad, usuarioId, sujeto, d, a);
        } catch (Exception e) {
            fallosEmisor.incrementAndGet();
            log.warn("[eventos] no se pudo armar {} (la operacion sigue): {}", tipo, e.toString());
        }
    }

    private void emitirConAtributos(String tipo, String gravedad, Long usuarioId, String sujeto,
                                    Map<String, Object> datos, Map<String, Object> atributos) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            if (props.isEstricto()) {
                throw new IllegalStateException("EventoEmisor.emitir sin transaccion activa: " + tipo);
            }
            log.warn("[eventos] {} emitido sin transaccion activa: se anota en una transaccion propia", tipo);
            txPropia.executeWithoutResult(s -> insertarConSavepoint(tipo, gravedad, usuarioId, sujeto, datos, atributos));
            return;
        }
        insertarConSavepoint(tipo, gravedad, usuarioId, sujeto, datos, atributos);
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

    /**
     * En una TX propia (REQUIRES_NEW), para hechos que ocurren fuera de una TX (deleteVentaItem) o del
     * propio nodo (latido). Nunca lanza: un fallo se cuenta en fallosEmisor.
     */
    public void emitirEnNuevaTx(String tipo, String gravedad, Long usuarioId, String sujeto, Map<String, Object> datos) {
        if (!encendido()) return;
        try {
            txPropia.executeWithoutResult(s -> insertarConSavepoint(tipo, gravedad, usuarioId, sujeto, datos));
        } catch (Exception e) {
            fallosEmisor.incrementAndGet();
            log.warn("[eventos] no se pudo anotar {} en TX propia: {}", tipo, e.toString());
        }
    }

    void insertarConSavepoint(String tipo, String gravedad, Long usuarioId, String sujeto, Map<String, Object> datos) {
        insertarConSavepoint(tipo, gravedad, usuarioId, sujeto, datos, null);
    }

    void insertarConSavepoint(String tipo, String gravedad, Long usuarioId, String sujeto, Map<String, Object> datos,
                              Map<String, Object> atributos) {
        final String json;
        final String jsonAtributos;
        try {
            json = mapper.writeValueAsString(ListaBlanca.filtrar(tipo, datos));
            jsonAtributos = atributos == null ? null : mapper.writeValueAsString(atributos);
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
                    ps.setString(9, jsonAtributos);
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
