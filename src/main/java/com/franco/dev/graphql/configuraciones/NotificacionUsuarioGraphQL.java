package com.franco.dev.graphql.configuraciones;

import com.franco.dev.domain.configuracion.NotificacionUsuario;
import com.franco.dev.service.configuracion.NotificacionUsuarioService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;

@Component
public class NotificacionUsuarioGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private NotificacionUsuarioService notificacionUsuarioService;

    @Autowired
    private com.franco.dev.service.personas.UsuarioService usuarioService;

    public Boolean marcarNotificacionLeida(Long notificacionUsuarioId) {
        try {
            return notificacionUsuarioService.marcarComoLeida(notificacionUsuarioId);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public Boolean registrarInteraccionNotificacion(Long notificacionUsuarioId, String accion) {
        try {
            return notificacionUsuarioService.registrarInteraccion(notificacionUsuarioId, accion);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public NotificacionUsuarioPage getNotificacionesUsuario(String tokenFcm, Boolean leidas,
            Integer page, Integer size) {

        org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Usuario no autenticado");
        }

        String username = authentication.getName();
        com.franco.dev.domain.personas.Usuario usuario = usuarioService.findByNickname(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 20 : size;
        Pageable pageable = PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "creadoEn"));
        Page<NotificacionUsuario> result = notificacionUsuarioService.findByUsuarioId(usuario.getId(), tokenFcm,
                pageable);
        List<NotificacionUsuario> content = (leidas == null) ? result.getContent()
                : result.getContent().stream()
                        .filter(nu -> Boolean.TRUE.equals(nu.getLeida()) == leidas)
                        .toList();

        return new NotificacionUsuarioPage(content, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }
}
