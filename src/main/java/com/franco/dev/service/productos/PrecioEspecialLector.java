package com.franco.dev.service.productos;

import com.franco.dev.domain.productos.PrecioPorSucursal;
import com.franco.dev.domain.productos.Presentacion;
import com.franco.dev.service.productos.PrecioEspecialFuente.Fila;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Aplica los precios especiales de ESTA sucursal a los precios y presentaciones que se devuelven
 * por GraphQL (SPEC-PRECIO-ESPECIAL-SUCURSAL.md en el central).
 * <p>
 * <b>Nunca modifica las entidades recibidas.</b> El filial tiene OpenEntityManagerInViewFilter:
 * una entidad devuelta por el repositorio sigue administrada todo el request y un
 * {@code @Transactional} posterior (saveVenta) flushearia un setter a la tabla global. Se
 * devuelven copias nuevas con el mismo id, asi venta_item.precio_id sigue apuntando al precio real.
 * <p>
 * <b>Una falla aca no puede impedir vender:</b> la lectura va por JDBC (PrecioEspecialFuente) y
 * cualquier excepcion devuelve lo recibido, es decir el precio global.
 * <p>
 * El dia se evalua en -03 fijo (Paraguay no tiene horario de verano), no en la zona de la JVM:
 * hay filiales con tzdb viejo que aplican -04 (gotcha "Timezone Paraguay").
 * <p>
 * {@code precio.especial.habilitado=false} apaga todo sin deploy.
 */
@Service
public class PrecioEspecialLector {

    private static final Logger log = LoggerFactory.getLogger(PrecioEspecialLector.class);
    public static final ZoneId ZONA = ZoneOffset.ofHours(-3);

    private final PrecioEspecialFuente fuente;
    private final Environment env;
    private final Clock clock;

    @Autowired
    public PrecioEspecialLector(PrecioEspecialFuente fuente, Environment env) {
        this(fuente, env, Clock.systemUTC());
    }

    PrecioEspecialLector(PrecioEspecialFuente fuente, Environment env, Clock clock) {
        this.fuente = fuente;
        this.env = env;
        this.clock = clock;
    }

    @PostConstruct
    void informar() {
        log.info("Precio especial: sucursalId={}, habilitado={}, hoy={} (zona {})",
                sucursalIdPropia(), habilitado(), hoy(), ZONA);
    }

    public List<PrecioPorSucursal> aplicar(List<PrecioPorSucursal> precios) {
        if (precios == null || precios.isEmpty()) return precios;
        Long sucursalId = sucursalIdPropia();
        if (sucursalId == null || !habilitado()) return precios;
        try {
            List<Long> ids = precios.stream().filter(Objects::nonNull).map(PrecioPorSucursal::getId)
                    .filter(Objects::nonNull).collect(Collectors.toList());
            if (ids.isEmpty()) return precios;
            Map<Long, Fila> vigentes = vigentesPorClave(fuente.porPrecios(sucursalId, ids), Fila::getPrecioId);
            if (vigentes.isEmpty()) return precios;
            List<PrecioPorSucursal> resultado = new ArrayList<>(precios.size());
            for (PrecioPorSucursal p : precios) {
                Fila e = p != null ? vigentes.get(p.getId()) : null;
                resultado.add(e != null ? copiaConEspecial(p, e) : p);
            }
            return resultado;
        } catch (Exception ex) {
            log.error("No se pudieron aplicar los precios especiales; se usan los globales: {}", ex.getMessage(), ex);
            return precios;
        }
    }

    /** Presentaciones inactivas con algun precio con especial vigente aca salen como copias activas. */
    public List<Presentacion> habilitarPresentaciones(List<Presentacion> presentaciones) {
        if (presentaciones == null || presentaciones.isEmpty()) return presentaciones;
        Long sucursalId = sucursalIdPropia();
        if (sucursalId == null || !habilitado()) return presentaciones;
        List<Long> inactivas = presentaciones.stream()
                .filter(p -> p != null && p.getId() != null && !Boolean.TRUE.equals(p.getActivo()))
                .map(Presentacion::getId).collect(Collectors.toList());
        if (inactivas.isEmpty()) return presentaciones;
        try {
            Set<Long> habilitadas = vigentesPorClave(fuente.porPresentaciones(sucursalId, inactivas), Fila::getPresentacionId).keySet();
            if (habilitadas.isEmpty()) return presentaciones;
            List<Presentacion> resultado = new ArrayList<>(presentaciones.size());
            for (Presentacion p : presentaciones) {
                resultado.add(p != null && habilitadas.contains(p.getId()) && !Boolean.TRUE.equals(p.getActivo())
                        ? copiaActiva(p) : p);
            }
            return resultado;
        } catch (Exception ex) {
            log.error("No se pudieron evaluar presentaciones con precio especial: {}", ex.getMessage(), ex);
            return presentaciones;
        }
    }

    private Map<Long, Fila> vigentesPorClave(List<Fila> filas, java.util.function.Function<Fila, Long> clave) {
        LocalDate hoy = hoy();
        Map<Long, Fila> vigentes = new HashMap<>();
        for (Fila f : filas) {
            if (!esVigente(f, hoy) || clave.apply(f) == null) continue;
            Fila actual = vigentes.get(clave.apply(f));
            // Dos vigentes para el mismo precio no deberian existir (el central lo valida); si una
            // carrera los deja, gana el de menor id para que todas las cajas coincidan.
            if (actual == null || f.getId() < actual.getId()) vigentes.put(clave.apply(f), f);
        }
        return vigentes;
    }

    static boolean esVigente(Fila f, LocalDate hoy) {
        return f != null && f.getId() != null && f.getPrecioId() != null
                && Boolean.TRUE.equals(f.getActivo())
                && f.getPrecio() != null && f.getPrecio() > 0
                && (f.getFechaDesde() == null || !f.getFechaDesde().isAfter(hoy))
                && (f.getFechaHasta() == null || !f.getFechaHasta().isBefore(hoy));
    }

    private LocalDate hoy() {
        return LocalDate.now(clock.withZone(ZONA));
    }

    private boolean habilitado() {
        return !"false".equalsIgnoreCase(String.valueOf(env.getProperty("precio.especial.habilitado", "true")).trim());
    }

    private static PrecioPorSucursal copiaConEspecial(PrecioPorSucursal o, Fila e) {
        return new PrecioPorSucursal(o.getId(), o.getPrincipal(), o.getPresentacion(), o.getTipoPrecio(),
                o.getSucursal(), e.getPrecio(), o.getCreadoEn(), o.getUsuario(), true);
    }

    private static Presentacion copiaActiva(Presentacion o) {
        return new Presentacion(o.getId(), o.getDescripcion(), o.getCantidad(), true, o.getPrincipal(),
                o.getCreadoEn(), o.getProducto(), o.getTipoPresentacion(), o.getUsuario());
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
