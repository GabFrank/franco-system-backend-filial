package com.franco.dev.fmc.service;

import com.franco.dev.domain.configuracion.Notificacion;
import com.franco.dev.domain.configuracion.NotificacionUsuario;
import com.franco.dev.domain.configuracion.enums.EstadoEnvio;
import com.franco.dev.domain.configuracion.enums.EstadoNotificacion;
import com.franco.dev.domain.personas.Usuario;
import com.franco.dev.fmc.model.DeliveryResult;
import com.franco.dev.fmc.model.PushNotificationRequest;
import com.franco.dev.repository.configuraciones.NotificacionRepository;
import com.franco.dev.repository.configuraciones.NotificacionUsuarioRepository;
import com.franco.dev.service.configuracion.InicioSesionService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.validation.Valid;
import javax.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class PushNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PushNotificationService.class);
    private final Logger logger = LoggerFactory.getLogger(PushNotificationService.class);
    private final NotificacionRepository notificacionRepository;
    private final NotificacionUsuarioRepository notificacionUsuarioRepository;
    private final NotificationDispatchService dispatchService;
    private final InicioSesionService inicioSesionService;
    private final FCMService fcmService;

    @PersistenceContext
    private EntityManager entityManager;

    public PushNotificationService(
            NotificacionRepository notificacionRepository,
            NotificacionUsuarioRepository notificacionUsuarioRepository,
            NotificationDispatchService dispatchService,
            InicioSesionService inicioSesionService,
            FCMService fcmService) {
        this.notificacionRepository = notificacionRepository;
        this.notificacionUsuarioRepository = notificacionUsuarioRepository;
        this.dispatchService = dispatchService;
        this.inicioSesionService = inicioSesionService;
        this.fcmService = fcmService;
    }

    public void sendPushNotificationToToken(@Valid PushNotificationRequest request) {
        enqueue(request);
    }

    public void sendPushNotificationToTopic(@Valid PushNotificationRequest request) {
        if (!request.hasTopic()) {
            throw new ValidationException("Debe definir el topic para enviar la notificación");
        }
        DeliveryResult result = fcmService.sendToTopic(request);
        if (result.getOutcome() != DeliveryResult.DeliveryOutcome.SUCCESS) {
            LOGGER.warn("Fallo envío a tópico {} - {}", request.getTopic(), result.getMessage());
        } else {
            LOGGER.info("Notificación enviada al tópico {}", request.getTopic());
        }
    }

    private void enqueue(PushNotificationRequest request) {
        if (!request.hasTopic() && !request.hasDirectTokens() && !request.hasUsuarios()) {
            throw new ValidationException("Debe proveer al menos un token, tópico o usuario destino");
        }
        if (request.hasTopic()) {
            sendPushNotificationToTopic(request);
            return;
        }

        Notificacion notificacion = buildNotification(request);
        List<Target> targets = resolveTargets(request);

        if (targets.isEmpty()) {
            notificacion.setEstado(EstadoNotificacion.CANCELADA);
            notificacion.setUltimoError("No existen tokens activos para la solicitud");
            notificacionRepository.save(notificacion);
            LOGGER.warn("No se encontraron tokens para la notificación {}", request.getTitle());
            return;
        }

        notificacionRepository.save(notificacion);
        List<NotificacionUsuario> usuarios = targets.stream()
                .map(target -> buildNotificacionUsuario(notificacion, target))
                .collect(Collectors.toList());
        notificacionUsuarioRepository.saveAll(usuarios);
        dispatchService.dispatchAsync();
        LOGGER.debug("Notificación {} encolada para {} destinatarios", notificacion.getId(), usuarios.size());
    }

    private Notificacion buildNotification(PushNotificationRequest request) {
        Notificacion notificacion = new Notificacion();
        notificacion.setTitulo(request.getTitle());
        notificacion.setMensaje(request.getMessage());
        notificacion.setData(request.getData());
        notificacion.setTipo(request.getType() != null ? request.getType() : "GENERAL");
        notificacion.setEstado(EstadoNotificacion.ACTIVA);
        notificacion.setIntentosEnvio(0);
        return notificacion;
    }

    private List<Target> resolveTargets(PushNotificationRequest request) {
        Set<String> dedup = new LinkedHashSet<>();
        List<Target> targets = new ArrayList<>();

        // En servidor filial, simplificamos la lógica de tokens
        // Asumimos que los tokens vienen directamente en la request
        if (request.hasDirectTokens()) {
            List<String> directTokens = new ArrayList<>();
            if (request.getToken() != null) {
                directTokens.add(request.getToken());
            }
            if (request.getTokens() != null) {
                directTokens.addAll(request.getTokens());
            }
            directTokens.stream()
                    .filter(Objects::nonNull)
                    .filter(token -> !token.isBlank())
                    .filter(dedup::add)
                    .forEach(token -> targets.add(new Target(null, token)));
        }

        // Para usuarios específicos, por ahora usamos lógica simplificada
        // En futuras versiones se puede implementar búsqueda de tokens por usuario
        if (request.hasUsuarios() && !request.hasDirectTokens()) {
            // TODO: Implementar búsqueda de tokens FCM por usuario ID en servidor filial
            // Por ahora, esta funcionalidad requiere tokens directos
            logger.warn("Envío por usuario ID no implementado en servidor filial. Use tokens directos.");
        }

        return targets;
    }

    private NotificacionUsuario buildNotificacionUsuario(Notificacion notificacion, Target target) {
        NotificacionUsuario entity = new NotificacionUsuario();
        entity.setNotificacion(notificacion);
        if (target.getUsuarioId() != null) {
            Usuario reference = entityManager.getReference(Usuario.class, target.getUsuarioId());
            entity.setUsuario(reference);
        }
        entity.setTokenFcm(target.getToken());
        entity.setEstadoEnvio(EstadoEnvio.PENDIENTE);
        return entity;
    }

    private static class Target {
        private final Long usuarioId;
        private final String token;

        private Target(Long usuarioId, String token) {
            this.usuarioId = usuarioId;
            this.token = token;
        }

        public Long getUsuarioId() {
            return usuarioId;
        }

        public String getToken() {
            return token;
        }
    }
}
