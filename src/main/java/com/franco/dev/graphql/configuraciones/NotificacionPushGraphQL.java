package com.franco.dev.graphql.configuraciones;

import com.franco.dev.domain.configuracion.NotificacionUsuario;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.fmc.model.PushNotificationRequest;
import com.franco.dev.fmc.service.NotificationTemplateService;
import com.franco.dev.fmc.service.PushNotificationService;
import com.franco.dev.graphql.configuraciones.input.NotificacionPushInput;
import com.franco.dev.repository.configuraciones.NotificacionUsuarioRepository;
import com.franco.dev.service.personas.UsuarioService;
import graphql.kickstart.tools.GraphQLMutationResolver;
import graphql.kickstart.tools.GraphQLQueryResolver;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NotificacionPushGraphQL implements GraphQLQueryResolver, GraphQLMutationResolver {

    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private PushNotificationService pushNotificationService;
    @Autowired
    private NotificationTemplateService notificationTemplateService;
    @Autowired
    private NotificacionUsuarioRepository notificacionUsuarioRepository;

    public Boolean requestPushNotification(NotificacionPushInput notificacionPushInput) {
        try {
            Usuario usuario = usuarioService.findByPersonaId(notificacionPushInput.getPersonaId());
            PushNotificationRequest request = notificationTemplateService.manual(
                    notificacionPushInput.getTitulo(),
                    notificacionPushInput.getMensaje(),
                    notificacionPushInput.getData(),
                    "MANUAL");
            request.setUsuarioIds(Collections.singletonList(usuario.getId()));
            pushNotificationService.sendPushNotificationToToken(request);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public java.util.List<NotificacionUsuario> notificacionesPorToken(String tokenFcm) {
        if (tokenFcm == null || tokenFcm.isBlank()) return java.util.Collections.emptyList();
        java.util.List<NotificacionUsuario> list = notificacionUsuarioRepository.findAllByTokenFcm(tokenFcm);
        list.sort(java.util.Comparator.comparing(
                (NotificacionUsuario nu) -> nu.getNotificacion() != null ? nu.getNotificacion().getCreadoEn() : null,
                java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())
        ).reversed());
        return list;
    }
}
