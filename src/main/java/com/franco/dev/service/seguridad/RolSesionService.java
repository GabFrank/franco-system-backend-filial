package com.franco.dev.service.seguridad;

import com.franco.dev.domain.personas.Role;
import com.franco.dev.service.personas.RoleService;
import graphql.GraphQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Control de rol de las mutations del filial, con el usuario del token (nunca con un id que mande el cliente).
 *
 * <p>Los roles se leen de {@code personas.usuario_role}, que llega del central por replicacion. Pasa siempre el
 * superusuario: rol ADMIN o nickname ADMIN, igual que {@code TesoreriaSecurityService} del central.
 *
 * <p>{@code NOT_SUPPORTED}: si la consulta fallara dentro de la transaccion del llamador la dejaria rollback-only.
 */
@Service
public class RolSesionService {

    private static final Logger log = LoggerFactory.getLogger(RolSesionService.class);
    static final String ADMIN = "ADMIN";

    @Autowired
    private UsuarioSesionService usuarioSesionService;
    @Autowired
    private RoleService roleService;

    /** Pasa con ADMIN o con alguno de los roles; si no, lanza "No autorizado: ...". */
    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public void exigirRol(String... roles) {
        String nickname = nickname();
        if (nickname == null) {
            log.warn("No autorizado: {} sin sesion", operacion());
            throw new GraphQLException("No autorizado: volvé a iniciar sesión.");
        }
        if (ADMIN.equalsIgnoreCase(nickname.trim())) return;
        Optional<Long> usuarioId = usuarioSesionService.idDelUsuarioAutenticado();
        if (!usuarioId.isPresent()) {
            log.warn("No autorizado: {} el nickname {} no existe en esta sucursal", operacion(), nickname);
            throw new GraphQLException("No autorizado: el usuario no existe en esta sucursal.");
        }
        Set<String> mios = nombres(roleService.findByUsuarioId(usuarioId.get()));
        if (mios.contains(ADMIN)) return;
        for (String r : roles) {
            if (mios.contains(r.trim().toUpperCase())) return;
        }
        String requeridos = roles.length == 0 ? ADMIN : String.join(" o ", roles);
        log.warn("No autorizado: {} {} sin {}", operacion(), nickname, requeridos);
        throw new GraphQLException("No autorizado: se requiere el rol " + requeridos + ".");
    }

    /** Solo superusuario. Lleva su propia anotacion: la llamada interna a exigirRol no pasa por el proxy. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    public void exigirAdmin() {
        exigirRol();
    }

    private static Set<String> nombres(List<Role> roles) {
        return roles.stream().filter(Objects::nonNull).map(Role::getNombre).filter(Objects::nonNull)
                .map(n -> n.trim().toUpperCase()).collect(Collectors.toSet());
    }

    private static String nickname() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null || auth.getName().trim().isEmpty()) {
            return null;
        }
        return auth.getName();
    }

    /** El metodo del resolver que pidio el control, para el log. */
    private static String operacion() {
        return Arrays.stream(Thread.currentThread().getStackTrace())
                .filter(e -> e.getClassName().contains(".graphql."))
                .findFirst().map(e -> e.getMethodName()).orElse("?");
    }
}
