package com.franco.dev.service.sorteo;

import com.franco.dev.domain.operaciones.Venta;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import com.franco.dev.graphql.operaciones.input.SorteoClienteInput;
import com.franco.dev.service.impresion.dto.SorteoTicketDto;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Registra en {@code operaciones.sorteo_participacion} los sorteos en los que participa una venta y
 * arma los tickets para imprimir.
 * <p>
 * <b>Nada del sorteo puede impedir ni revertir una venta.</b> Por eso todo lo que toca la base corre
 * por JDBC dentro de transacciones propias ({@code REQUIRES_NEW}), nunca en la de {@code saveVenta}:
 * en Postgres una sentencia que falla deja abortada la transaccion entera, y si fuera la de la
 * venta, el commit de la venta fallaria. Cualquier error se loguea y se devuelve vacio.
 * <p>
 * Consecuencia conocida: la participacion se confirma antes que la venta. Si la venta despues no
 * llegara a confirmarse, queda una participacion sin venta; el central la deriva como ANULADA porque
 * no encuentra la venta activa.
 */
@Service
public class SorteoParticipacionService {

    private static final Logger log = LoggerFactory.getLogger(SorteoParticipacionService.class);

    static final String INSERT =
            "INSERT INTO operaciones.sorteo_participacion " +
            "(sorteo_id, venta_id, sucursal_id, cantidad, cliente_id, documento, nombre, codigo, usuario_id) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT (sorteo_id, venta_id) DO NOTHING";

    static final String NOMBRE_SUCURSAL = "select nombre from empresarial.sucursal where id = ?";

    static final String NOMBRE_CLIENTE =
            "select p.nombre from personas.cliente c join personas.persona p on p.id = c.persona_id where c.id = ?";

    static final String PARTICIPACIONES_DE_VENTA =
            "select sp.sorteo_id, sp.venta_id, sp.cantidad, sp.codigo, sp.nombre, sp.documento, " +
            "s.nombre as sorteo_nombre, s.descripcion, s.fecha_desde, s.fecha_hasta, s.fecha_sorteo, " +
            "su.nombre as sucursal_nombre, v.creado_en as venta_creado_en, v.total_gs " +
            "from operaciones.sorteo_participacion sp " +
            "left join operaciones.sorteo s on s.id = sp.sorteo_id " +
            "left join empresarial.sucursal su on su.id = sp.sucursal_id " +
            "left join operaciones.venta v on v.id = sp.venta_id " +
            "where sp.venta_id = ? order by sp.sorteo_id";

    static final String PREMIOS_DE_SORTEO =
            "select (coalesce(p.cantidad, 1) || ' x ' || coalesce(nullif(trim(p.descripcion), ''), pr.descripcion)) " +
            "from operaciones.sorteo_premio p " +
            "left join productos.producto pr on pr.id = p.producto_id " +
            "where p.sorteo_id = ? and coalesce(nullif(trim(p.descripcion), ''), pr.descripcion) is not null " +
            "order by p.orden, p.id";

    private final SorteoLector lector;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate escritura;
    private final TransactionTemplate lectura;
    private final Environment env;

    public SorteoParticipacionService(SorteoLector lector, JdbcTemplate jdbc,
                                      PlatformTransactionManager transactionManager, Environment env) {
        this.lector = lector;
        this.jdbc = jdbc;
        this.env = env;
        this.escritura = new TransactionTemplate(transactionManager);
        this.escritura.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.lectura = new TransactionTemplate(transactionManager);
        this.lectura.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.lectura.setReadOnly(true);
    }

    /**
     * Vuelve a evaluar los sorteos con el total real de la venta y registra una participacion por
     * cada sorteo que califica. Devuelve vacio si no hay cliente (no participa o desktop viejo), si
     * es un delivery, si la venta no tiene id o ante cualquier error.
     */
    public List<Participacion> registrar(Venta venta, List<CobroDetalleInput> detalles, SorteoClienteInput cliente,
                                         Long usuarioId) {
        if (cliente == null || venta == null || venta.getId() == null || venta.getDelivery() != null) {
            return Collections.emptyList();
        }
        try {
            Long sucursalId = venta.getSucursalId() != null ? venta.getSucursalId() : sucursalIdPropia();
            if (sucursalId == null) {
                log.warn("Sorteo: la venta {} no tiene sucursal; no se registra la participacion", venta.getId());
                return Collections.emptyList();
            }
            double total = venta.getTotalGs() != null ? venta.getTotalGs() : 0d;
            String nombreInput = textoONull(cliente.getNombre());
            // Una sola transaccion de lectura (una conexion) para evaluar y para los datos del ticket.
            Lecturas leido = enTransaccionPropia(lectura, () -> {
                Lecturas l = new Lecturas();
                l.aplicables = lector.aplicables(total, detalles);
                if (l.aplicables == null || l.aplicables.isEmpty()) return l;
                l.sucursal = primero(NOMBRE_SUCURSAL, sucursalId);
                if (nombreInput == null && cliente.getClienteId() != null) {
                    l.nombre = primero(NOMBRE_CLIENTE, cliente.getClienteId());
                }
                return l;
            });
            List<SorteoAplicable> aplicables = leido != null ? leido.aplicables : null;
            if (aplicables == null || aplicables.isEmpty()) return Collections.emptyList();
            String nombre = nombreInput != null ? nombreInput : leido.nombre;
            String documento = textoONull(cliente.getDocumento());
            String sucursal = leido.sucursal;

            List<Participacion> participaciones = new ArrayList<>();
            for (SorteoAplicable a : aplicables) {
                if (a == null || a.getSorteoId() == null || a.getCantidadTickets() == null
                        || a.getCantidadTickets() <= 0) continue;
                Participacion p = new Participacion();
                p.setSorteoId(a.getSorteoId());
                p.setNombreSorteo(a.getNombre());
                p.setDescripcion(a.getDescripcion());
                p.setPremios(a.getPremios());
                p.setFechaDesde(a.getFechaDesde());
                p.setFechaHasta(a.getFechaHasta());
                p.setFechaSorteo(a.getFechaSorteo());
                p.setCantidad(a.getCantidadTickets());
                p.setCodigo(codigo(a.getSorteoId(), sucursalId, venta.getId()));
                p.setVentaId(venta.getId());
                p.setSucursal(sucursal);
                p.setFecha(venta.getCreadoEn());
                p.setTotalGs(venta.getTotalGs());
                p.setClienteNombre(nombre);
                p.setClienteDocumento(documento);
                participaciones.add(p);
            }
            if (participaciones.isEmpty()) return Collections.emptyList();

            final Long clienteId = cliente.getClienteId();
            final String nombreFinal = nombre;
            escritura.executeWithoutResult(status -> {
                for (Participacion p : participaciones) {
                    jdbc.update(INSERT, p.getSorteoId(), p.getVentaId(), sucursalId, p.getCantidad(), clienteId,
                            documento, nombreFinal, p.getCodigo(), usuarioId);
                }
            });
            return participaciones;
        } catch (Exception ex) {
            log.error("No se pudo registrar la participacion en sorteos de la venta {}; la venta sigue sin sorteo: {}",
                    venta.getId(), ex.getMessage(), ex);
            return Collections.emptyList();
        }
    }

    /**
     * Tickets de las participaciones ya registradas de una venta (reimpresion). Lee en una
     * transaccion propia: la venta tiene que estar confirmada. Nunca lanza.
     */
    public List<SorteoTicketDto> ticketsDeVenta(Long ventaId, boolean reimpresion) {
        if (ventaId == null) return Collections.emptyList();
        try {
            List<Participacion> participaciones = enTransaccionPropia(lectura, () -> {
                List<Participacion> ps = jdbc.query(PARTICIPACIONES_DE_VENTA, SorteoParticipacionService::participacion, ventaId);
                for (Participacion p : ps) {
                    p.setPremios(jdbc.queryForList(PREMIOS_DE_SORTEO, String.class, p.getSorteoId()));
                }
                return ps;
            });
            return tickets(participaciones, reimpresion);
        } catch (Exception ex) {
            log.error("No se pudieron leer los tickets de sorteo de la venta {}: {}", ventaId, ex.getMessage(), ex);
            return Collections.emptyList();
        }
    }

    /**
     * Si la venta tiene alguna participacion registrada. Una sola consulta indexada por venta_id;
     * devuelve false (y loguea) ante cualquier error, porque corre por fila en listados.
     */
    public boolean tieneParticipaciones(Long ventaId) {
        if (ventaId == null) return false;
        try {
            Boolean r = jdbc.queryForObject(
                    "select exists (select 1 from operaciones.sorteo_participacion where venta_id = ?)",
                    Boolean.class, ventaId);
            return Boolean.TRUE.equals(r);
        } catch (Exception ex) {
            log.warn("No se pudo consultar si la venta {} tiene sorteo: {}", ventaId, ex.getMessage());
            return false;
        }
    }

    /** Expande cada participacion en sus N tickets, numerados 1..N. */
    public static List<SorteoTicketDto> tickets(List<Participacion> participaciones, boolean reimpresion) {
        List<SorteoTicketDto> tickets = new ArrayList<>();
        if (participaciones == null) return tickets;
        for (Participacion p : participaciones) {
            int n = p.getCantidad() != null ? p.getCantidad() : 0;
            for (int i = 1; i <= n; i++) {
                tickets.add(new SorteoTicketDto(p.getNombreSorteo(), p.getDescripcion(), p.getPremios(),
                        p.getFechaDesde(), p.getFechaHasta(), p.getFechaSorteo(), p.getSucursal(), p.getVentaId(),
                        p.getFecha(), p.getTotalGs(), p.getCodigo(), i, n, p.getClienteNombre(),
                        p.getClienteDocumento(), reimpresion));
            }
        }
        return tickets;
    }

    /** Lo que vuelve en {@code Venta.sorteoTickets} para el aviso del desktop. */
    public static List<SorteoTicketInfo> resumen(List<Participacion> participaciones) {
        List<SorteoTicketInfo> r = new ArrayList<>();
        if (participaciones == null) return r;
        for (Participacion p : participaciones) r.add(new SorteoTicketInfo(p.getNombreSorteo(), p.getCantidad()));
        return r;
    }

    static String codigo(Long sorteoId, Long sucursalId, Long ventaId) {
        return "S" + sorteoId + "-" + sucursalId + "-" + ventaId;
    }

    private static <T> T enTransaccionPropia(TransactionTemplate tx, Supplier<T> trabajo) {
        return tx.execute(status -> trabajo.get());
    }

    /**
     * Primer valor de una consulta de un solo texto; null si falla. Corre dentro de la transaccion
     * de lectura propia de {@link #registrar}, nunca en la de la venta.
     */
    private String primero(String sql, Long id) {
        try {
            List<String> r = jdbc.queryForList(sql, String.class, id);
            return r == null || r.isEmpty() ? null : textoONull(r.get(0));
        } catch (Exception ex) {
            log.warn("Sorteo: no se pudo leer un dato para el ticket ({}): {}", id, ex.getMessage());
            return null;
        }
    }

    private static String textoONull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    private Long sucursalIdPropia() {
        String v = env.getProperty("sucursalId");
        if (v == null) return null;
        try {
            return Long.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Participacion participacion(ResultSet rs, int n) throws SQLException {
        Participacion p = new Participacion();
        p.setSorteoId(rs.getLong("sorteo_id"));
        p.setVentaId(rs.getLong("venta_id"));
        p.setCantidad((Integer) rs.getObject("cantidad", Integer.class));
        p.setCodigo(rs.getString("codigo"));
        p.setClienteNombre(rs.getString("nombre"));
        p.setClienteDocumento(rs.getString("documento"));
        p.setNombreSorteo(rs.getString("sorteo_nombre"));
        p.setDescripcion(rs.getString("descripcion"));
        p.setFechaDesde(rs.getObject("fecha_desde", LocalDate.class));
        p.setFechaHasta(rs.getObject("fecha_hasta", LocalDate.class));
        p.setFechaSorteo(rs.getObject("fecha_sorteo", LocalDate.class));
        p.setSucursal(rs.getString("sucursal_nombre"));
        Timestamp creado = rs.getTimestamp("venta_creado_en");
        p.setFecha(creado != null ? creado.toLocalDateTime() : null);
        p.setTotalGs(rs.getObject("total_gs") != null ? rs.getDouble("total_gs") : null);
        return p;
    }

    /** Lo que se lee en la transaccion de lectura de {@link #registrar}. */
    private static final class Lecturas {
        List<SorteoAplicable> aplicables;
        String sucursal;
        String nombre;
    }

    /** Una participacion registrada (una por venta y sorteo), con lo necesario para imprimirla. */
    @Data
    @NoArgsConstructor
    public static class Participacion {
        private Long sorteoId;
        private String nombreSorteo;
        private String descripcion;
        private List<String> premios;
        private LocalDate fechaDesde;
        private LocalDate fechaHasta;
        private LocalDate fechaSorteo;
        private String codigo;
        private Integer cantidad;
        private Long ventaId;
        private String sucursal;
        private LocalDateTime fecha;
        private Double totalGs;
        private String clienteNombre;
        private String clienteDocumento;
    }
}
