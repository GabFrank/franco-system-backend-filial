package com.franco.dev.service.sorteo;

import com.franco.dev.domain.financiero.FormaPago;
import com.franco.dev.graphql.operaciones.input.CobroDetalleInput;
import com.franco.dev.service.financiero.FormaPagoService;
import com.franco.dev.service.sorteo.SorteoFuente.Premio;
import com.franco.dev.service.sorteo.SorteoFuente.Sorteo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Decide en que sorteos vigentes de ESTA sucursal participa una venta y cuantos tickets le tocan.
 * <p>
 * <b>Nada del sorteo puede impedir una venta:</b> cualquier excepcion devuelve lista vacia y se loguea.
 * El dia se evalua en -03 fijo, no en la zona de la JVM (hay filiales con tzdb viejo que aplican -04).
 * {@code sorteo.habilitado=false} apaga todo sin deploy.
 */
@Service
public class SorteoLector {

    private static final Logger log = LoggerFactory.getLogger(SorteoLector.class);
    public static final ZoneId ZONA = ZoneOffset.ofHours(-3);

    private final SorteoFuente fuente;
    private final FormaPagoService formaPagoService;
    private final Environment env;
    private final Clock clock;

    @Autowired
    public SorteoLector(SorteoFuente fuente, FormaPagoService formaPagoService, Environment env) {
        this(fuente, formaPagoService, env, Clock.systemUTC());
    }

    SorteoLector(SorteoFuente fuente, FormaPagoService formaPagoService, Environment env, Clock clock) {
        this.fuente = fuente;
        this.formaPagoService = formaPagoService;
        this.env = env;
        this.clock = clock;
    }

    public List<SorteoAplicable> aplicables(double totalGs, List<CobroDetalleInput> detalles) {
        try {
            Long sucursalId = sucursalIdPropia();
            if (sucursalId == null || !habilitado()) return Collections.emptyList();
            List<Sorteo> candidatos = fuente.vigentesCandidatos(sucursalId);
            if (candidatos == null || candidatos.isEmpty()) return Collections.emptyList();
            LocalDate hoy = LocalDate.now(clock.withZone(ZONA));
            Set<Long> tarjetas = null;
            List<SorteoAplicable> resultado = new ArrayList<>();
            for (Sorteo s : candidatos) {
                if (!esVigente(s, hoy) || s.getMontoMinimo() == null) continue;
                boolean incluyeTarjeta = !Boolean.FALSE.equals(s.getIncluyeTarjeta());
                if (!incluyeTarjeta && tarjetas == null) tarjetas = formaPagoTarjetaIds();
                double monto = SorteoCalculo.montoComputable(totalGs, detalles, tarjetas, incluyeTarjeta);
                int tickets = SorteoCalculo.tickets(s.getModoParticipacion(), monto, s.getMontoMinimo(),
                        s.getCantidadFija(), s.getTopePorVenta());
                if (tickets <= 0) continue;
                resultado.add(new SorteoAplicable(s.getId(), s.getNombre(), s.getDescripcion(), tickets,
                        s.getFechaSorteo(), s.getFechaDesde(), s.getFechaHasta(), premiosEnTexto(s)));
            }
            return resultado;
        } catch (Exception ex) {
            log.error("No se pudieron evaluar los sorteos; la venta sigue sin sorteo: {}", ex.getMessage(), ex);
            return Collections.emptyList();
        }
    }

    static boolean esVigente(Sorteo s, LocalDate hoy) {
        return s != null && s.getId() != null && Boolean.TRUE.equals(s.getActivo())
                && (s.getFechaDesde() == null || !s.getFechaDesde().isAfter(hoy))
                && (s.getFechaHasta() == null || !s.getFechaHasta().isBefore(hoy));
    }

    private static List<String> premiosEnTexto(Sorteo s) {
        List<String> premios = new ArrayList<>();
        for (Premio p : s.getPremios()) {
            if (p.getDescripcion() == null || p.getDescripcion().trim().isEmpty()) continue;
            premios.add((p.getCantidad() != null ? p.getCantidad() : 1) + " x " + p.getDescripcion().trim());
        }
        return premios;
    }

    /** Mismo criterio que PdvCajaService: la descripcion de la forma de pago contiene "TARJETA". */
    private Set<Long> formaPagoTarjetaIds() {
        try {
            Set<Long> ids = new HashSet<>();
            for (FormaPago fp : formaPagoService.findAll2()) {
                if (fp != null && fp.getId() != null && fp.getDescripcion() != null
                        && fp.getDescripcion().contains("TARJETA")) ids.add(fp.getId());
            }
            return ids;
        } catch (Exception ex) {
            log.warn("No se pudieron leer las formas de pago para el sorteo; se asume que ninguna es tarjeta: {}", ex.getMessage());
            return Collections.emptySet();
        }
    }

    private boolean habilitado() {
        return !"false".equalsIgnoreCase(String.valueOf(env.getProperty("sorteo.habilitado", "true")).trim());
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
}
