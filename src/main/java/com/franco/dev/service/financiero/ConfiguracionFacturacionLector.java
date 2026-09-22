package com.franco.dev.service.financiero;

import com.franco.dev.domain.financiero.ConfiguracionFacturacion;
import com.franco.dev.repository.financiero.ConfiguracionFacturacionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Resuelve la politica de facturacion de esta sucursal (issue #127).
 * <p>
 * Orden: fila de la sucursal propia → fila global ({@code sucursal_id NULL}) → property
 * {@code facturaCountDown}. Con la tabla vacia, el filial se comporta como antes de que existiera.
 * <p>
 * <b>Corre fuera de la transaccion de la venta</b> ({@code NOT_SUPPORTED}). {@code saveVenta} es
 * {@code @Transactional}: si esta lectura fallara dentro de esa transaccion, la dejaria marcada
 * rollback-only y la venta se perderia aunque el error se ataje aca. Suspendida la transaccion
 * de la venta, una falla de lectura solo cuesta caer al default. Mismo mecanismo que documenta
 * {@code FacturaLegalBuilder}.
 * <p>
 * Sin cache: la tabla cambia por replicacion y el cambio tiene que valer desde la venta siguiente.
 */
@Service
public class ConfiguracionFacturacionLector {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionFacturacionLector.class);

    /** Primero la modificada mas recientemente; las filas sin fecha, al final. */
    private static final Comparator<ConfiguracionFacturacion> MAS_RECIENTE_PRIMERO =
            Comparator.comparing(ConfiguracionFacturacion::getModificadoEn,
                            Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder()))
                    .thenComparing(ConfiguracionFacturacion::getId,
                            Comparator.nullsLast(Comparator.<Long>reverseOrder()));

    private final ConfiguracionFacturacionRepository repository;
    private final Environment env;

    public ConfiguracionFacturacionLector(ConfiguracionFacturacionRepository repository, Environment env) {
        this.repository = repository;
        this.env = env;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public PoliticaFacturacion resolver() {
        int defaultIntervalo = facturaCountDownProperty();
        try {
            List<ConfiguracionFacturacion> filas = new ArrayList<>(repository.findAllByOrderByModificadoEnDescIdDesc());
            filas.sort(MAS_RECIENTE_PRIMERO);
            Long sucursalId = sucursalIdPropia();

            PoliticaFacturacion politica = null;
            if (sucursalId != null) {
                politica = primeraValida(filas, sucursalId, defaultIntervalo, PoliticaFacturacion.Origen.SUCURSAL);
            }
            if (politica == null) {
                politica = primeraValida(filas, null, defaultIntervalo, PoliticaFacturacion.Origen.GLOBAL);
            }
            return politica != null ? politica : PoliticaFacturacion.desdeProperty(defaultIntervalo);
        } catch (Exception e) {
            log.error("No se pudo leer la politica de facturacion; se usa facturaCountDown={}: {}",
                    defaultIntervalo, e.getMessage(), e);
            return PoliticaFacturacion.desdeProperty(defaultIntervalo);
        }
    }

    /**
     * La primera fila de esa clave con un {@code modo} reconocible. Una fila con modo NULL o
     * desconocido no se toma como "no facturar": se la ignora y se sigue con la siguiente del orden.
     */
    private PoliticaFacturacion primeraValida(List<ConfiguracionFacturacion> filas, Long sucursalId,
                                              int defaultIntervalo, PoliticaFacturacion.Origen origen) {
        for (ConfiguracionFacturacion fila : filas) {
            if (!Objects.equals(fila.getSucursalId(), sucursalId)) continue;
            String modo = modoReconocido(fila.getModo());
            if (modo == null) {
                log.warn("configuracion_facturacion id={} con modo '{}' desconocido: se ignora", fila.getId(), fila.getModo());
                continue;
            }
            Integer n = fila.getVentasSinFactura();
            int intervalo = n != null && n >= 0 ? n : defaultIntervalo;
            boolean respeta = Boolean.TRUE.equals(fila.getVentaTicketRespetaPolitica());
            return new PoliticaFacturacion(modo, intervalo, respeta, origen);
        }
        return null;
    }

    private static String modoReconocido(String modo) {
        if (modo == null) return null;
        String m = modo.trim().toUpperCase();
        if (ConfiguracionFacturacion.MODO_TODAS.equals(m)
                || ConfiguracionFacturacion.MODO_INTERVALO.equals(m)
                || ConfiguracionFacturacion.MODO_A_PEDIDO.equals(m)) {
            return m;
        }
        return null;
    }

    private Long sucursalIdPropia() {
        String valor = env.getProperty("sucursalId");
        if (valor == null) return null;
        try {
            return Long.valueOf(valor.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Leida en cada venta: es el default de siempre, y un valor roto no puede frenar la caja. */
    private int facturaCountDownProperty() {
        String valor = env.getProperty("facturaCountDown");
        if (valor == null) return 0;
        try {
            int n = Integer.parseInt(valor.trim());
            return Math.max(n, 0);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
