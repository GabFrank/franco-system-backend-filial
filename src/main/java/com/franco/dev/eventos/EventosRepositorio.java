package com.franco.dev.eventos;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** SQL de la cola de eventos. Todo por JdbcTemplate: estas tablas no tienen entidades JPA. */
@Component
public class EventosRepositorio {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public EventosRepositorio(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    // ---------------------------------------------------------------- suscripciones (espejo)

    public List<Suscripcion> suscripcionesActivas() {
        return jdbc.query("SELECT * FROM configuraciones.webhook_suscripcion WHERE activo IS TRUE", (rs, i) -> mapSuscripcion(rs));
    }

    public Suscripcion suscripcion(Long id) {
        List<Suscripcion> l = jdbc.query("SELECT * FROM configuraciones.webhook_suscripcion WHERE id = ?", (rs, i) -> mapSuscripcion(rs), id);
        return l.isEmpty() ? null : l.get(0);
    }

    private Suscripcion mapSuscripcion(ResultSet rs) throws SQLException {
        Suscripcion s = new Suscripcion();
        s.setId(rs.getLong("id"));
        s.setNombre(rs.getString("nombre"));
        s.setUrl(rs.getString("url"));
        s.setEventos(textos(rs.getArray("eventos")));
        s.setEventosPausados(textos(rs.getArray("eventos_pausados")));
        Array suc = rs.getArray("sucursal_ids");
        s.setSucursalIds(suc == null ? null : Arrays.stream((Object[]) suc.getArray())
                .map(o -> ((Number) o).longValue()).collect(Collectors.toList()));
        String umbrales = rs.getString("umbrales");
        if (umbrales != null) {
            try {
                s.setUmbrales(mapper.readValue(umbrales, new TypeReference<Map<String, Map<String, Number>>>() {}));
            } catch (Exception e) {
                // Umbral ilegible: la suscripcion no recibe nada con umbral antes que recibir de mas
                s.setUmbrales(Collections.singletonMap("*", Collections.singletonMap("_ilegible", Double.MAX_VALUE)));
            }
        }
        s.setEntrega(rs.getString("entrega"));
        s.setDatosPersonales(Boolean.TRUE.equals(rs.getObject("datos_personales")));
        s.setCabeceraNombre(rs.getString("cabecera_nombre"));
        s.setSecretoRef(rs.getString("secreto_ref"));
        s.setLatido(Boolean.TRUE.equals(rs.getObject("latido")));
        s.setActivo(Boolean.TRUE.equals(rs.getObject("activo")));
        long cv = rs.getLong("config_version");
        s.setConfigVersion(rs.wasNull() ? null : cv);
        return s;
    }

    private static List<String> textos(Array a) throws SQLException {
        if (a == null) return Collections.emptyList();
        return Arrays.stream((Object[]) a.getArray()).map(String::valueOf).collect(Collectors.toList());
    }

    // ---------------------------------------------------------------- despacho

    public static class EventoPendiente {
        public long id;
        public String tipo;
        public Long sucursalId;
        public Map<String, Object> datos;
    }

    /** Toma eventos sin despachar con lock (SKIP LOCKED): dos JVM no despachan el mismo. Llamar dentro de una TX. */
    public List<EventoPendiente> tomarSinDespachar(int limite) {
        return jdbc.query("SELECT id, tipo, sucursal_id, datos FROM eventos.outbox_evento WHERE repartido_en IS NULL "
                + "ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED", (rs, i) -> {
            EventoPendiente e = new EventoPendiente();
            e.id = rs.getLong("id");
            e.tipo = rs.getString("tipo");
            long suc = rs.getLong("sucursal_id");
            e.sucursalId = rs.wasNull() ? null : suc;
            try {
                String d = rs.getString("datos");
                e.datos = d == null ? Collections.emptyMap() : mapper.readValue(d, new TypeReference<Map<String, Object>>() {});
            } catch (Exception ex) {
                e.datos = Collections.emptyMap();
            }
            return e;
        }, limite);
    }

    /**
     * Crea la entrega con la siguiente secuencia de la suscripcion. El UPDATE sobre destino_estado toma
     * el lock de la fila: las secuencias quedan correlativas y sin saltos aunque despachen dos JVM.
     */
    public void crearEntrega(long outboxId, Suscripcion s, Map<String, Number> umbral) {
        jdbc.update("INSERT INTO eventos.destino_estado (suscripcion_id) VALUES (?) ON CONFLICT DO NOTHING", s.getId());
        Long sec = jdbc.queryForObject("UPDATE eventos.destino_estado SET ultima_secuencia = ultima_secuencia + 1, "
                + "actualizado_en = now() WHERE suscripcion_id = ? RETURNING ultima_secuencia", Long.class, s.getId());
        jdbc.update("INSERT INTO eventos.webhook_entrega (outbox_id, suscripcion_id, secuencia, config_version, umbral_aplicado) "
                + "VALUES (?, ?, ?, ?, CAST(? AS jsonb))", outboxId, s.getId(), sec, s.getConfigVersion(), json(umbral));
    }

    /** Aviso de prueba directo a una suscripcion activa, sin pasar por el reparto. */
    @Transactional
    public boolean crearPrueba(Long suscripcionId, String eventoId, Long sucursalId) {
        Suscripcion s = suscripcion(suscripcionId);
        if (s == null || !s.isActivo()) return false;
        Long outboxId = jdbc.queryForObject("INSERT INTO eventos.outbox_evento (evento_id, tipo, gravedad, sucursal_id, sujeto, datos, repartido_en)"
                + " VALUES (?, 'webhook.prueba', 'baja', ?, ?, CAST(? AS jsonb), now()) RETURNING id", Long.class,
                eventoId, sucursalId, "suscripcion/" + suscripcionId, "{\"mensaje\":\"Aviso de prueba de FRC\"}");
        crearEntrega(outboxId, s, null);
        return true;
    }

    public void marcarDespachado(long outboxId) {
        jdbc.update("UPDATE eventos.outbox_evento SET repartido_en = now() WHERE id = ?", outboxId);
    }

    // ---------------------------------------------------------------- envio

    public static class EntregaReclamada {
        public long id;
        public long suscripcionId;
        public long secuencia;
        public int intentos;
        public Timestamp leaseHasta;
        public String eventoId;
        public String tipo;
        public OffsetDateTime ocurridoEn;
        public Long sucursalId;
        public String sujeto;
        public String gravedad;
        public String datos;
        public String link;
    }

    /**
     * Reclama con lease las entregas que tocan. Por suscripcion toma como mucho maxPorDestino menos las
     * que ya estan EN_CURSO con lease vigente, y salta las pausadas (circuit breaker o 410). La TX del
     * reclamo termina antes de mandar el HTTP.
     */
    public List<EntregaReclamada> reclamar(boolean lote, int limite, int maxPorDestino, int leaseSeg) {
        // El tope por destino cuenta las EN_CURSO: dos reclamos a la vez (dos hilos o dos JVM) verian
        // las mismas y tomarian el doble. Se serializan con pg_advisory_xact_lock dentro de una TX corta
        // sobre la misma conexion: el lock se suelta solo con el commit o el rollback, aunque algo falle,
        // y el UPDATE toma su snapshot despues del lock, asi ve lo que reclamo el otro.
        return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<List<EntregaReclamada>>) con -> {
            boolean autoCommit = con.getAutoCommit();
            con.setAutoCommit(false);
            try {
                try (java.sql.Statement st = con.createStatement()) {
                    st.execute("SELECT pg_advisory_xact_lock(" + LOCK_RECLAMO + ")");
                }
                JdbcTemplate j = new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(con, true));
                // Lo que lleva mas de 72 h sin salir se descarta antes de volver a intentarlo
                j.update("UPDATE eventos.webhook_entrega SET estado = 'DESCARTADA', lease_hasta = NULL,"
                        + " ultimo_error = left('Vencida a las 72 h: ' || COALESCE(ultimo_error, ''), 300)"
                        + " WHERE estado IN ('REINTENTO', 'EN_CURSO') AND primer_intento_en < now() - make_interval(hours => ?)"
                        + " AND (estado = 'REINTENTO' OR lease_hasta < now())", ReglasEntrega.VENCIMIENTO_HORAS);
                List<EntregaReclamada> l = reclamarConLock(j, lote, limite, maxPorDestino, leaseSeg);
                con.commit();
                return l;
            } catch (RuntimeException | java.sql.SQLException ex) {
                con.rollback();
                throw ex;
            } finally {
                con.setAutoCommit(autoCommit);
            }
        });
    }

    static final long LOCK_RECLAMO = 246_111_003L;

    private List<EntregaReclamada> reclamarConLock(JdbcTemplate jdbc, boolean lote, int limite, int maxPorDestino, int leaseSeg) {
        // Por lote: un solo POST en curso por suscripcion, con hasta `limite` eventos.
        // Individual: hasta maxPorDestino envios en curso por suscripcion.
        return jdbc.query(
                "WITH en_curso AS ("
                        + "  SELECT suscripcion_id, count(*) AS n FROM eventos.webhook_entrega"
                        + "  WHERE estado = 'EN_CURSO' AND lease_hasta >= now() GROUP BY suscripcion_id),"
                        + " candidatas AS ("
                        + "  SELECT e.id, e.suscripcion_id, row_number() OVER (PARTITION BY e.suscripcion_id ORDER BY e.secuencia) AS rn"
                        + "  FROM eventos.webhook_entrega e"
                        + "  JOIN configuraciones.webhook_suscripcion s ON s.id = e.suscripcion_id"
                        + "  LEFT JOIN eventos.destino_estado d ON d.suscripcion_id = e.suscripcion_id"
                        + "  WHERE (lower(COALESCE(s.entrega, 'individual')) = 'lote') = ?"
                        + "    AND ((e.estado IN ('PENDIENTE','REINTENTO') AND e.proximo_intento_en <= now())"
                        + "         OR (e.estado = 'EN_CURSO' AND e.lease_hasta < now()))"
                        + "    AND (d.pausado_hasta IS NULL OR d.pausado_hasta <= now())"
                        + "    AND COALESCE(d.pausado_por_410, false) = false),"
                        + " elegidas AS ("
                        + "  SELECT c.id FROM candidatas c LEFT JOIN en_curso k ON k.suscripcion_id = c.suscripcion_id"
                        + "  WHERE c.rn <= (CASE WHEN ? THEN (CASE WHEN COALESCE(k.n, 0) > 0 THEN 0 ELSE ? END) ELSE ? - COALESCE(k.n, 0) END)"
                        + "  ORDER BY c.id LIMIT ?),"
                        + " bloqueadas AS ("
                        + "  SELECT e.id FROM eventos.webhook_entrega e JOIN elegidas USING (id) FOR UPDATE OF e SKIP LOCKED)"
                        + " UPDATE eventos.webhook_entrega w SET estado = 'EN_CURSO', intentos = w.intentos + 1,"
                        + "   lease_hasta = date_trunc('milliseconds', now() + make_interval(secs => ?))"
                        + " FROM bloqueadas b, eventos.outbox_evento o"
                        + " WHERE w.id = b.id AND o.id = w.outbox_id"
                        + " RETURNING w.id, w.suscripcion_id, w.secuencia, w.intentos, w.lease_hasta, o.evento_id, o.tipo,"
                        + "   o.ocurrido_en, o.sucursal_id, o.sujeto, o.gravedad, o.datos::text AS datos, o.link",
                (rs, i) -> {
                    EntregaReclamada e = new EntregaReclamada();
                    e.id = rs.getLong("id");
                    e.suscripcionId = rs.getLong("suscripcion_id");
                    e.secuencia = rs.getLong("secuencia");
                    e.intentos = rs.getInt("intentos");
                    e.leaseHasta = rs.getTimestamp("lease_hasta");
                    e.eventoId = rs.getString("evento_id");
                    e.tipo = rs.getString("tipo");
                    e.ocurridoEn = rs.getObject("ocurrido_en", OffsetDateTime.class);
                    long suc = rs.getLong("sucursal_id");
                    e.sucursalId = rs.wasNull() ? null : suc;
                    e.sujeto = rs.getString("sujeto");
                    e.gravedad = rs.getString("gravedad");
                    e.datos = rs.getString("datos");
                    e.link = rs.getString("link");
                    return e;
                }, lote, lote, limite, maxPorDestino, limite, leaseSeg);
    }

    /** Guarda el resultado solo si la entrega sigue siendo de este envio (cerco por lease). */
    public boolean resolver(EntregaReclamada e, String estado, Integer status, String error, long esperaSeg) {
        // Un REINTENTO de una entrega cuyo primer intento fue hace mas de 72 h pasa a DESCARTADA.
        int n = jdbc.update("UPDATE eventos.webhook_entrega SET"
                        + " estado = CASE WHEN ? = 'REINTENTO' AND COALESCE(primer_intento_en, now()) < now() - make_interval(hours => ?)"
                        + "   THEN 'DESCARTADA' ELSE ? END,"
                        + " ultimo_status_http = ?,"
                        + " ultimo_error = CASE WHEN ? = 'REINTENTO' AND COALESCE(primer_intento_en, now()) < now() - make_interval(hours => ?)"
                        + "   THEN left('Vencida a las 72 h: ' || COALESCE(?, ''), 300) ELSE ? END,"
                        + " primer_intento_en = COALESCE(primer_intento_en, now()),"
                        + " proximo_intento_en = now() + make_interval(secs => ?), lease_hasta = NULL,"
                        + " enviado_en = CASE WHEN ? = 'ENVIADA' THEN now() ELSE enviado_en END"
                        + " WHERE id = ? AND estado = 'EN_CURSO' AND lease_hasta = ?",
                estado, ReglasEntrega.VENCIMIENTO_HORAS, estado, status,
                estado, ReglasEntrega.VENCIMIENTO_HORAS, corto(error), corto(error),
                (double) esperaSeg, estado, e.id, e.leaseHasta);
        return n == 1;
    }

    /** Fallo de nuestro lado (sin firma, sin secreto): vuelve a la cola sin gastar el intento. */
    public void devolverSinGastar(EntregaReclamada e, String error, long esperaSeg) {
        jdbc.update("UPDATE eventos.webhook_entrega SET estado = 'REINTENTO', intentos = GREATEST(intentos - 1, 0),"
                        + " ultimo_error = ?, proximo_intento_en = now() + make_interval(secs => ?), lease_hasta = NULL"
                        + " WHERE id = ? AND estado = 'EN_CURSO' AND lease_hasta = ?",
                corto(error), (double) esperaSeg, e.id, e.leaseHasta);
    }

    public void registrarExito(long suscripcionId) {
        jdbc.update("UPDATE eventos.destino_estado SET fallos_seguidos = 0, pausado_hasta = NULL, actualizado_en = now()"
                + " WHERE suscripcion_id = ?", suscripcionId);
    }

    /** Suma un fallo; a los 10 seguidos el destino queda en pausa 15 min sin frenar a los demas. */
    public void registrarFallo(long suscripcionId, String error) {
        jdbc.update("UPDATE eventos.destino_estado SET fallos_seguidos = fallos_seguidos + 1, ultimo_error = ?,"
                + " pausado_hasta = CASE WHEN fallos_seguidos + 1 >= 10 THEN now() + interval '15 minutes' ELSE pausado_hasta END,"
                + " actualizado_en = now() WHERE suscripcion_id = ?", corto(error), suscripcionId);
    }

    public void pausarPor410(long suscripcionId) {
        jdbc.update("UPDATE eventos.destino_estado d SET pausado_por_410 = true, ultimo_error = 'HTTP 410', actualizado_en = now(),"
                + " pausado_config_version = (SELECT s.config_version FROM configuraciones.webhook_suscripcion s WHERE s.id = d.suscripcion_id)"
                + " WHERE suscripcion_id = ?", suscripcionId);
    }

    /** Un 410 pausa el destino hasta que el central cambie la suscripcion (reactivarla, otra URL): ahi se despausa. */
    public int despausar410ConConfigNueva() {
        return jdbc.update("UPDATE eventos.destino_estado d SET pausado_por_410 = false, pausado_config_version = NULL, actualizado_en = now()"
                + " FROM configuraciones.webhook_suscripcion s WHERE s.id = d.suscripcion_id AND d.pausado_por_410"
                + " AND s.activo IS TRUE AND s.config_version IS DISTINCT FROM d.pausado_config_version");
    }

    /** Si la suscripcion ya no esta activa, sus entregas vivas quedan CANCELADA. */
    public int cancelarDeInactivas() {
        return jdbc.update("UPDATE eventos.webhook_entrega e SET estado = 'CANCELADA', lease_hasta = NULL"
                + " WHERE e.estado IN ('PENDIENTE','REINTENTO') AND NOT EXISTS ("
                + "   SELECT 1 FROM configuraciones.webhook_suscripcion s WHERE s.id = e.suscripcion_id AND s.activo IS TRUE)");
    }

    // ---------------------------------------------------------------- latido y limpieza

    public Map<String, Object> resumenEstado() {
        return jdbc.queryForMap("SELECT"
                + " (SELECT count(*) FROM eventos.webhook_entrega WHERE estado IN ('PENDIENTE','REINTENTO','EN_CURSO')) AS pendientes,"
                + " (SELECT min(creado_en) FROM eventos.webhook_entrega WHERE estado IN ('PENDIENTE','REINTENTO','EN_CURSO')) AS pendiente_mas_viejo,"
                + " (SELECT count(*) FROM eventos.webhook_entrega WHERE estado = 'DESCARTADA' AND creado_en > now() - interval '24 hours') AS descartadas_24h,"
                + " (SELECT max(config_version) FROM configuraciones.webhook_suscripcion) AS config_version,"
                + " (SELECT count(*) FROM eventos.destino_estado WHERE pausado_por_410 OR pausado_hasta > now()) AS destinos_pausados,"
                + " now() AS hora_nodo");
    }

    public void guardarNodoEstado(long sucursalId, boolean emisor, boolean firma, Map<String, Object> r, long fallosEmisor) {
        jdbc.update("INSERT INTO eventos.nodo_estado (sucursal_id, emisor_encendido, firma_configurada, config_version, pendientes,"
                        + " pendiente_mas_viejo, descartadas_24h, fallos_emisor, hora_nodo, actualizado_en)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), now())"
                        + " ON CONFLICT (sucursal_id) DO UPDATE SET emisor_encendido = EXCLUDED.emisor_encendido,"
                        + " firma_configurada = EXCLUDED.firma_configurada, config_version = EXCLUDED.config_version,"
                        + " pendientes = EXCLUDED.pendientes, pendiente_mas_viejo = EXCLUDED.pendiente_mas_viejo,"
                        + " descartadas_24h = EXCLUDED.descartadas_24h, fallos_emisor = EXCLUDED.fallos_emisor,"
                        + " hora_nodo = now(), actualizado_en = now()",
                sucursalId, emisor, firma, r.get("config_version"), ((Number) r.get("pendientes")).intValue(),
                r.get("pendiente_mas_viejo"), ((Number) r.get("descartadas_24h")).intValue(), fallosEmisor);
    }

    /** Limpieza nocturna en lotes de 5000. Lo vivo no se toca nunca, por viejo que sea. */
    public int limpiar() {
        int total = 0;
        int n;
        do {
            n = jdbc.update("DELETE FROM eventos.webhook_entrega WHERE id IN (SELECT id FROM eventos.webhook_entrega"
                    + " WHERE (estado IN ('ENVIADA','CANCELADA') AND creado_en < now() - interval '7 days')"
                    + "    OR (estado = 'DESCARTADA' AND creado_en < now() - interval '30 days') LIMIT 5000)");
            total += n;
        } while (n == 5000);
        do {
            n = jdbc.update("DELETE FROM eventos.outbox_evento o WHERE o.id IN (SELECT o2.id FROM eventos.outbox_evento o2"
                    + " WHERE o2.repartido_en < now() - interval '1 day' AND NOT EXISTS ("
                    + "   SELECT 1 FROM eventos.webhook_entrega e WHERE e.outbox_id = o2.id) LIMIT 5000)");
            total += n;
        } while (n == 5000);
        return total;
    }

    private String json(Object o) {
        if (o == null) return null;
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }

    private static String corto(String s) {
        if (s == null) return null;
        return s.length() > 300 ? s.substring(0, 300) : s;
    }

    public static List<Long> ids(List<EntregaReclamada> l) {
        List<Long> out = new ArrayList<>();
        for (EntregaReclamada e : l) out.add(e.id);
        return out;
    }
}
