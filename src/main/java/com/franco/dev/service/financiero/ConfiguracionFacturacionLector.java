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
        Integer intervaloProperty = facturaCountDownProperty();
        PoliticaFacturacion porDefecto = intervaloProperty != null
                ? PoliticaFacturacion.desdeProperty(intervaloProperty)
                : PoliticaFacturacion.sinFacturacionAutomatica();
        try {
            List<ConfiguracionFacturacion> filas = new ArrayList<>(repository.findAllByOrderByModificadoEnDescIdDesc());
            filas.sort(MAS_RECIENTE_PRIMERO);
            Long sucursalId = sucursalIdPropia();

            PoliticaFacturacion politica = null;
            if (sucursalId != null) {
                politica = primeraValida(filas, sucursalId, intervaloProperty, PoliticaFacturacion.Origen.SUCURSAL);
            }
            if (politica == null) {
                politica = primeraValida(filas, null, intervaloProperty, PoliticaFacturacion.Origen.GLOBAL);
            }
            return politica != null ? politica : porDefecto;
        } catch (Exception e) {
            log.error("No se pudo leer la politica de facturacion; se usa la property facturaCountDown ({}): {}",
                    porDefecto, e.getMessage(), e);
            return porDefecto;
        }
    }

    /**
     * La primera fila de esa clave con un {@code modo} reconocible. Una fila con modo NULL o
     * desconocido no se toma como "no facturar": se la ignora y se sigue con la siguiente del orden.
     * Lo mismo una INTERVALO sin intervalo usable cuando la property tampoco da uno.
     * <p>
     * Si la fila <b>mas reciente</b> de la clave esta inactiva, se saltea la clave entera: desactivar
     * es "esta sucursal sigue a la global", y caer a un duplicado viejo activo lo desmentiria.
     * {@code activo} NULL cuenta como activa.
     */
    private PoliticaFacturacion primeraValida(List<ConfiguracionFacturacion> filas, Long sucursalId,
                                              Integer intervaloProperty, PoliticaFacturacion.Origen origen) {
        boolean primeraDeLaClave = true;
        for (ConfiguracionFacturacion fila : filas) {
            if (!Objects.equals(fila.getSucursalId(), sucursalId)) continue;
            if (primeraDeLaClave && Boolean.FALSE.equals(fila.getActivo())) {
                return null;
            }
            primeraDeLaClave = false;
            String modo = modoReconocido(fila.getModo());
            if (modo == null) {
                log.warn("configuracion_facturacion id={} con modo '{}' desconocido: se ignora", fila.getId(), fila.getModo());
                continue;
            }
            Integer n = fila.getVentasSinFactura();
            Integer intervalo = n != null && n >= 0 ? n : intervaloProperty;
            if (intervalo == null) {
                if (ConfiguracionFacturacion.MODO_INTERVALO.equals(modo)) {
                    log.warn("configuracion_facturacion id={} sin intervalo usable y sin property: se ignora", fila.getId());
                    continue;
                }
                intervalo = 0;
            }
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

    /**
     * Leida en cada venta. NULL si es negativa, falta o no es numerica: esos casos NO se llevan a 0
     * (0 = facturar cada venta), porque un negativo era la forma historica de apagar la facturacion
     * silenciosa. Ver {@link PoliticaFacturacion#sinFacturacionAutomatica()}.
     */
    private Integer facturaCountDownProperty() {
        String valor = env.getProperty("facturaCountDown");
        if (valor == null) return null;
        try {
            int n = Integer.parseInt(valor.trim());
            return n >= 0 ? n : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
