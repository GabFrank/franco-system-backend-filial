package com.franco.dev.service.productos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Lee productos.precio_especial_sucursal por JDBC, fuera de Hibernate.
 * <p>
 * Con OpenEntityManagerInViewFilter, una consulta JPA que falla revierte su transaccion y limpia
 * el EntityManager compartido del request: los PrecioPorSucursal ya leidos quedan con sus LAZY sin
 * inicializar y la venta revienta aunque el error se ataje. JdbcTemplate toma su propia conexion
 * del pool (no hay transaccion activa en el resolver) y una falla aca no toca ese contexto.
 */
@Component
public class PrecioEspecialFuente {

    private static final String SELECT =
            "select e.id, e.precio_id, pps.presentacion_id, e.precio, e.fecha_desde, e.fecha_hasta, e.activo " +
            "from productos.precio_especial_sucursal e " +
            "join productos.precio_por_sucursal pps on pps.id = e.precio_id " +
            "where e.sucursal_id = :sucursalId and e.activo = true and ";

    private final NamedParameterJdbcTemplate jdbc;

    public PrecioEspecialFuente(JdbcTemplate jdbcTemplate) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    public List<Fila> porPrecios(long sucursalId, Collection<Long> precioIds) {
        if (precioIds == null || precioIds.isEmpty()) return Collections.emptyList();
        return jdbc.query(SELECT + "e.precio_id in (:ids)",
                new MapSqlParameterSource("sucursalId", sucursalId).addValue("ids", precioIds), PrecioEspecialFuente::fila);
    }

    public List<Fila> porPresentaciones(long sucursalId, Collection<Long> presentacionIds) {
        if (presentacionIds == null || presentacionIds.isEmpty()) return Collections.emptyList();
        return jdbc.query(SELECT + "pps.presentacion_id in (:ids)",
                new MapSqlParameterSource("sucursalId", sucursalId).addValue("ids", presentacionIds), PrecioEspecialFuente::fila);
    }

    private static Fila fila(ResultSet rs, int n) throws SQLException {
        return new Fila(rs.getLong("id"), rs.getLong("precio_id"), (Long) rs.getObject("presentacion_id", Long.class),
                rs.getObject("precio") != null ? rs.getDouble("precio") : null,
                rs.getObject("fecha_desde", LocalDate.class), rs.getObject("fecha_hasta", LocalDate.class),
                (Boolean) rs.getObject("activo", Boolean.class));
    }

    public static final class Fila {
        private final Long id;
        private final Long precioId;
        private final Long presentacionId;
        private final Double precio;
        private final LocalDate fechaDesde;
        private final LocalDate fechaHasta;
        private final Boolean activo;

        public Fila(Long id, Long precioId, Long presentacionId, Double precio, LocalDate fechaDesde,
                    LocalDate fechaHasta, Boolean activo) {
            this.id = id;
            this.precioId = precioId;
            this.presentacionId = presentacionId;
            this.precio = precio;
            this.fechaDesde = fechaDesde;
            this.fechaHasta = fechaHasta;
            this.activo = activo;
        }

        public Long getId() { return id; }
        public Long getPrecioId() { return precioId; }
        public Long getPresentacionId() { return presentacionId; }
        public Double getPrecio() { return precio; }
        public LocalDate getFechaDesde() { return fechaDesde; }
        public LocalDate getFechaHasta() { return fechaHasta; }
        public Boolean getActivo() { return activo; }
    }
}
