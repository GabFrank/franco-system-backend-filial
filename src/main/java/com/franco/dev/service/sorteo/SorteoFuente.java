package com.franco.dev.service.sorteo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lee operaciones.sorteo, sorteo_sucursal y sorteo_premio por JDBC, fuera de Hibernate.
 * Mismo motivo que PrecioEspecialFuente: una consulta JPA que falla revierte la transaccion y
 * limpia el EntityManager compartido del request (OpenEntityManagerInViewFilter).
 * <p>
 * <b>Ojo:</b> JdbcTemplate NO toma una conexion propia si hay una transaccion abierta: usa la de
 * esa transaccion. En Postgres una sentencia que falla deja abortada la transaccion entera, asi que
 * si se llama desde adentro de saveVenta, un error aca haria fallar el commit de la venta. Quien
 * la llame dentro de una transaccion tiene que envolverla en una propia (REQUIRES_NEW), como hace
 * {@link SorteoParticipacionService}. La query {@code sorteosAplicables} corre sin transaccion.
 */
@Component
public class SorteoFuente {

    private static final String SORTEOS =
            "select s.id, s.nombre, s.descripcion, s.monto_minimo, s.modo_participacion, s.cantidad_fija, " +
            "s.tope_por_venta, s.incluye_tarjeta, s.fecha_desde, s.fecha_hasta, s.fecha_sorteo, " +
            "s.todas_sucursales, s.activo " +
            "from operaciones.sorteo s " +
            "where s.activo = true and (s.todas_sucursales = true or exists " +
            "(select 1 from operaciones.sorteo_sucursal ss where ss.sorteo_id = s.id and ss.sucursal_id = :sucursalId)) " +
            "order by s.id";

    private static final String PREMIOS =
            "select p.sorteo_id, coalesce(nullif(trim(p.descripcion), ''), pr.descripcion) as descripcion, p.cantidad " +
            "from operaciones.sorteo_premio p " +
            "left join productos.producto pr on pr.id = p.producto_id " +
            "where p.sorteo_id in (:ids) order by p.sorteo_id, p.orden, p.id";

    private final NamedParameterJdbcTemplate jdbc;

    public SorteoFuente(JdbcTemplate jdbcTemplate) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    /** Sorteos activos de la sucursal (o de todas), sin filtrar fechas; con sus premios ordenados. */
    public List<Sorteo> vigentesCandidatos(long sucursalId) {
        List<Sorteo> base = jdbc.query(SORTEOS, new MapSqlParameterSource("sucursalId", sucursalId), SorteoFuente::sorteo);
        if (base.isEmpty()) return Collections.emptyList();
        List<Long> ids = new ArrayList<>();
        for (Sorteo s : base) ids.add(s.getId());
        Map<Long, List<Premio>> premios = new LinkedHashMap<>();
        jdbc.query(PREMIOS, new MapSqlParameterSource("ids", ids), (ResultSet rs) -> {
            premios.computeIfAbsent(rs.getLong("sorteo_id"), k -> new ArrayList<>())
                    .add(new Premio(rs.getString("descripcion"), (Integer) rs.getObject("cantidad", Integer.class)));
        });
        List<Sorteo> resultado = new ArrayList<>(base.size());
        for (Sorteo s : base) {
            resultado.add(s.conPremios(premios.getOrDefault(s.getId(), Collections.emptyList())));
        }
        return resultado;
    }

    private static Sorteo sorteo(ResultSet rs, int n) throws SQLException {
        return new Sorteo(rs.getLong("id"), rs.getString("nombre"), rs.getString("descripcion"),
                rs.getObject("monto_minimo") != null ? rs.getDouble("monto_minimo") : null,
                rs.getString("modo_participacion"),
                (Integer) rs.getObject("cantidad_fija", Integer.class), (Integer) rs.getObject("tope_por_venta", Integer.class),
                (Boolean) rs.getObject("incluye_tarjeta", Boolean.class),
                rs.getObject("fecha_desde", LocalDate.class), rs.getObject("fecha_hasta", LocalDate.class),
                rs.getObject("fecha_sorteo", LocalDate.class),
                (Boolean) rs.getObject("todas_sucursales", Boolean.class), (Boolean) rs.getObject("activo", Boolean.class),
                Collections.emptyList());
    }

    public static final class Premio {
        private final String descripcion;
        private final Integer cantidad;

        public Premio(String descripcion, Integer cantidad) {
            this.descripcion = descripcion;
            this.cantidad = cantidad;
        }

        public String getDescripcion() { return descripcion; }
        public Integer getCantidad() { return cantidad; }
    }

    public static final class Sorteo {
        private final Long id;
        private final String nombre;
        private final String descripcion;
        private final Double montoMinimo;
        private final String modoParticipacion;
        private final Integer cantidadFija;
        private final Integer topePorVenta;
        private final Boolean incluyeTarjeta;
        private final LocalDate fechaDesde;
        private final LocalDate fechaHasta;
        private final LocalDate fechaSorteo;
        private final Boolean todasSucursales;
        private final Boolean activo;
        private final List<Premio> premios;

        public Sorteo(Long id, String nombre, String descripcion, Double montoMinimo, String modoParticipacion,
                      Integer cantidadFija, Integer topePorVenta, Boolean incluyeTarjeta, LocalDate fechaDesde,
                      LocalDate fechaHasta, LocalDate fechaSorteo, Boolean todasSucursales, Boolean activo,
                      List<Premio> premios) {
            this.id = id;
            this.nombre = nombre;
            this.descripcion = descripcion;
            this.montoMinimo = montoMinimo;
            this.modoParticipacion = modoParticipacion;
            this.cantidadFija = cantidadFija;
            this.topePorVenta = topePorVenta;
            this.incluyeTarjeta = incluyeTarjeta;
            this.fechaDesde = fechaDesde;
            this.fechaHasta = fechaHasta;
            this.fechaSorteo = fechaSorteo;
            this.todasSucursales = todasSucursales;
            this.activo = activo;
            this.premios = premios != null ? Collections.unmodifiableList(new ArrayList<>(premios)) : Collections.emptyList();
        }

        Sorteo conPremios(List<Premio> nuevos) {
            return new Sorteo(id, nombre, descripcion, montoMinimo, modoParticipacion, cantidadFija, topePorVenta,
                    incluyeTarjeta, fechaDesde, fechaHasta, fechaSorteo, todasSucursales, activo, nuevos);
        }

        public Long getId() { return id; }
        public String getNombre() { return nombre; }
        public String getDescripcion() { return descripcion; }
        public Double getMontoMinimo() { return montoMinimo; }
        public String getModoParticipacion() { return modoParticipacion; }
        public Integer getCantidadFija() { return cantidadFija; }
        public Integer getTopePorVenta() { return topePorVenta; }
        public Boolean getIncluyeTarjeta() { return incluyeTarjeta; }
        public LocalDate getFechaDesde() { return fechaDesde; }
        public LocalDate getFechaHasta() { return fechaHasta; }
        public LocalDate getFechaSorteo() { return fechaSorteo; }
        public Boolean getTodasSucursales() { return todasSucursales; }
        public Boolean getActivo() { return activo; }
        public List<Premio> getPremios() { return premios; }
    }
}
