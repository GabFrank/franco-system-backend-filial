package com.franco.dev.service.seguridad;

import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.service.personas.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Resuelve el id del usuario que firma la request actual, a partir del JWT.
 *
 * <p>El token no trae el id: {@code JwtGenerator} pone el <b>nickname</b> como subject y
 * {@code JwtValidator} lo lee. De ahi el salto a {@code personas.usuario}, que es unico por
 * nickname (indice {@code usuario_un_nickname}).
 *
 * <p><b>Corre fuera de la transaccion que lo llama</b> ({@code NOT_SUPPORTED}). Quien lo usa es el
 * auditor, y el auditor se llama desde {@code VentaGraphQL.saveVenta}, que es {@code @Transactional}:
 * si este SELECT fallara dentro de esa transaccion la dejaria marcada rollback-only y la venta se
 * perderia, aunque el auditor se trague la excepcion. Mismo mecanismo y mismo motivo que
 * {@code ConfiguracionFacturacionLector}.
 */
@Service
public class UsuarioSesionService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioSesionService.class);

    /**
     * El cache expira a proposito. El nickname es mutable ({@code UsuarioGraphQL.saveUsuario} mapea
     * el input entero) y un rename libera el viejo para otro usuario: un cache eterno devolveria el
     * id equivocado, que es peor que no devolver nada porque en el log no se distingue de un
     * mismatch legitimo.
     */
    private static final long TTL_MS = 60_000L;

    private final Map<String, Entrada> cache = new ConcurrentHashMap<>();

    private final LongSupplier reloj;

    @Autowired
    private UsuarioService usuarioService;

    public UsuarioSesionService() {
        this(System::currentTimeMillis);
    }

    /** Constructor para los tests: permite adelantar el reloj sin dormir el hilo. */
    UsuarioSesionService(LongSupplier reloj) {
        this.reloj = reloj;
    }

    void setUsuarioService(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    /**
     * @return el id del usuario autenticado, o vacio si la request no trae identidad.
     *         <p><b>Vacio no siempre significa «proceso de sistema».</b> Para los resolvers HTTP si:
     *         schedulers y replicacion postean sin sesion. Pero por WebSocket tampoco hay
     *         {@code SecurityContext} (ver {@code CapturaCuponSubscription}), y ahi vacio es un
     *         humano. Quien use esto desde una subscription tiene que saberlo.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public Optional<Long> idDelUsuarioAutenticado() {
        String nickname = nicknameAutenticado();
        if (nickname == null) {
            return Optional.empty();
        }
        String clave = nickname.trim().toUpperCase();
        long ahora = reloj.getAsLong();

        Entrada entrada = cache.get(clave);
        if (entrada != null && ahora - entrada.resueltoEn < TTL_MS) {
            return Optional.ofNullable(entrada.usuarioId);
        }

        Long id = usuarioService.findByNickname(clave).map(Usuario::getId).orElse(null);
        cache.put(clave, new Entrada(id, ahora));
        if (id == null) {
            log.debug("El nickname {} del token no resuelve a ningun usuario", clave);
        }
        return Optional.ofNullable(id);
    }

    /** El nickname del JWT de esta request, o null si el contexto no trae identidad. */
    private String nicknameAutenticado() {
        Authentication auth = SecurityContextHolder.getContext() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (!(principal instanceof UserDetails)) {
            return null;
        }
        String nickname = ((UserDetails) principal).getUsername();
        return nickname == null || nickname.trim().isEmpty() ? null : nickname;
    }

    /** Tamanio actual del cache, para los tests y para monitoreo. */
    public int getTamanioCache() {
        return cache.size();
    }

    private static class Entrada {
        private final Long usuarioId;
        private final long resueltoEn;

        private Entrada(Long usuarioId, long resueltoEn) {
            this.usuarioId = usuarioId;
            this.resueltoEn = resueltoEn;
        }
    }
}
