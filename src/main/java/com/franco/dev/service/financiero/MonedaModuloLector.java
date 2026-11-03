package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.Moneda;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Moneda de un modulo para esta sucursal, leida del espejo de {@code configuracion_moneda_modulo}
 * (V108.5, llega del central). Mismo orden que el {@code MonedaModuloLector} del central: la fila de
 * la sucursal (PDV y FACTURACION) -> la fila de toda la empresa. De cada clave cuenta la fila activa
 * (NULL = activa) mas reciente con {@code vigente_desde <= ahora}.
 * <p>
 * Nunca lanza y corre fuera de la transaccion de quien llama ({@code NOT_SUPPORTED}): JdbcTemplate
 * usa la conexion de la transaccion abierta, y en Postgres una sentencia fallida aborta la
 * transaccion entera; dentro de {@code saveVenta} perderia la venta. Sin cache: un cambio rige en
 * la venta siguiente. Un modulo que el filial no conoce no se lee nunca (la fila se ignora).
 */
@Service
public class MonedaModuloLector {

    private static final Logger log = LoggerFactory.getLogger(MonedaModuloLector.class);

    public static final String PDV = "PDV";
    public static final String FACTURACION = "FACTURACION";

    private static final String VIGENTE =
            "select moneda_id from financiero.configuracion_moneda_modulo "
                    + "where modulo = ? and sucursal_id %s and moneda_id is not null and activo is not false "
                    + "and (vigente_desde is null or vigente_desde <= now()) "
                    + "order by vigente_desde desc nulls last, id desc limit 1";

    private final JdbcTemplate jdbc;
    private final MonedaService monedaService;
    private final Environment env;

    public MonedaModuloLector(JdbcTemplate jdbc, MonedaService monedaService, Environment env) {
        this.jdbc = jdbc;
        this.monedaService = monedaService;
        this.env = env;
    }

    /** La moneda del PDV de esta sucursal; sin configuracion, la de la empresa. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public Moneda pdv() {
        return configurada(PDV).orElseGet(this::principal);
    }

    /**
     * La moneda configurada para un modulo en esta sucursal, sin caer a la de la empresa: vacio = sin
     * configuracion (o espejo ilegible). FACTURACION vacio significa "la moneda de la venta".
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public Optional<Moneda> configurada(String modulo) {
        try {
            Long sucursalId = sucursalId();
            Long monedaId = null;
            if (sucursalId != null) {
                monedaId = primera(jdbc.queryForList(String.format(VIGENTE, "= ?"), Long.class, modulo, sucursalId));
            }
            if (monedaId == null) {
                monedaId = primera(jdbc.queryForList(String.format(VIGENTE, "is null"), Long.class, modulo));
            }
            return monedaId != null ? monedaService.findById(monedaId) : Optional.empty();
        } catch (Exception e) {
            log.warn("MONEDA_MODULO_ILEGIBLE modulo={}: {}", modulo, e.getMessage());
            return Optional.empty();
        }
    }

    private Moneda principal() {
        try {
            return monedaService.principal();
        } catch (Exception e) {
            log.warn("MONEDA_EMPRESA_ILEGIBLE: {}", e.getMessage());
            return null;
        }
    }

    private Long sucursalId() {
        String s = env.getProperty("sucursalId");
        try {
            return s != null && !s.trim().isEmpty() ? Long.valueOf(s.trim()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long primera(List<Long> ids) {
        return ids == null || ids.isEmpty() ? null : ids.get(0);
    }
}
