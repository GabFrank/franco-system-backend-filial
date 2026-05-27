package com.franco.dev.graphql.configuraciones;

import com.franco.dev.domain.configuracion.NotificacionDestinatario;
import com.franco.dev.domain.personas.Usuario;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class NotificacionUsuarioGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    public NotificacionDestinatarioPage notificacionesUsuario(Boolean leidas, Integer page, Integer size,
            String estadoTablero, String fechaInicio, String fechaFin) {
        int p = page != null ? page : 0;
        int s = size != null ? size : 10;
        return new NotificacionDestinatarioPage(new ArrayList<>(), p, s, 0L, 0);
    }

    public Long conteoNotificacionesNoLeidas() {
        return 0L;
    }

    public List<Usuario> usuariosDestinatariosNotificacion(Long notificacionId) {
        return Collections.emptyList();
    }

    public List<Usuario> usuariosConAccesoNotificacion(Long notificacionId) {
        return Collections.emptyList();
    }

    public Boolean marcarNotificacionLeida(Long notificacionId) {
        return true;
    }

    public Boolean marcarTodasNotificacionesLeidas() {
        return true;
    }

    public Boolean cambiarEstadoTableroNotificacion(Long notificacionId, String estado) {
        return true;
    }
}
