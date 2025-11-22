package com.franco.dev.fmc.service;

import com.franco.dev.domain.configuracion.Notificacion;
import com.franco.dev.domain.configuracion.NotificacionUsuario;
import com.franco.dev.domain.configuracion.enums.EstadoEnvio;
import com.franco.dev.domain.configuracion.enums.EstadoNotificacion;
import com.franco.dev.fmc.model.DeliveryResult;
import com.franco.dev.fmc.model.PushNotificationRequest;
import com.franco.dev.repository.configuraciones.NotificacionUsuarioRepository;
import com.franco.dev.service.configuracion.InicioSesionService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDispatchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final NotificacionUsuarioRepository notificacionUsuarioRepository;
    private final FCMService fcmService;
    private final InicioSesionService inicioSesionService;
    private final Optional<MeterRegistry> meterRegistry;

    @Value("${app.notifications.batch-size:25}")
    private int batchSize;

    @Value("${app.notifications.max-attempts:5}")
    private int maxAttempts;

    public NotificationDispatchService(
            NotificacionUsuarioRepository notificacionUsuarioRepository,
            FCMService fcmService,
            InicioSesionService inicioSesionService,
            Optional<MeterRegistry> meterRegistry) {
        this.notificacionUsuarioRepository = notificacionUsuarioRepository;
        this.fcmService = fcmService;
        this.inicioSesionService = inicioSesionService;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${app.notifications.dispatch-interval:5000}")
    @Transactional
    public void scheduledDispatch() {
        dispatchInternal();
    }

    @Async("notificationExecutor")
    @Transactional
    public void dispatchAsync() {
        dispatchInternal();
    }

    protected void dispatchInternal() {
        List<NotificacionUsuario> pendientes = notificacionUsuarioRepository.findBatchByEstado(
                EstadoEnvio.PENDIENTE, PageRequest.of(0, batchSize));
        if (pendientes.isEmpty()) {
            return;
        }
        LOGGER.debug("Procesando {} notificaciones pendientes", pendientes.size());
        for (NotificacionUsuario target : pendientes) {
            Notificacion notificacion = target.getNotificacion();
            PushNotificationRequest request = new PushNotificationRequest();
            request.setTitle(notificacion.getTitulo());
            request.setMessage(notificacion.getMensaje());
            request.setData(notificacion.getData());
            request.setType(notificacion.getTipo());

            DeliveryResult result = fcmService.sendToToken(target.getTokenFcm(), request);
            handleResult(target, notificacion, result);
        }
    }

    private void handleResult(NotificacionUsuario target, Notificacion notificacion, DeliveryResult result) {
        LocalDateTime now = LocalDateTime.now();
        notificacion.setIntentosEnvio(Optional.ofNullable(notificacion.getIntentosEnvio()).orElse(0) + 1);
        switch (result.getOutcome()) {
            case SUCCESS:
                target.setEstadoEnvio(EstadoEnvio.ENVIADO);
                target.setFechaEnvio(now);
                target.setMensajeError(null);
                meter("notifications.success");
                break;
            case INVALID_TOKEN:
                target.setEstadoEnvio(EstadoEnvio.FALLO_DESTINO);
                target.setMensajeError(result.getMessage());
                meter("notifications.invalid-token");
                // TODO: Implementar limpieza de tokens inválidos en servidor filial
                // inicioSesionService.clearToken(target.getTokenFcm());
                break;
            case TRANSIENT_ERROR:
                if (notificacion.getIntentosEnvio() >= maxAttempts) {
                    target.setEstadoEnvio(EstadoEnvio.FALLO_ENVIO);
                    target.setMensajeError(result.getMessage());
                    meter("notifications.failure.max-attempts");
                } else {
                    target.setEstadoEnvio(EstadoEnvio.PENDIENTE);
                    target.setMensajeError(result.getMessage());
                    meter("notifications.transient-error");
                }
                break;
            case FAILURE:
                target.setEstadoEnvio(EstadoEnvio.FALLO_ENVIO);
                target.setMensajeError(result.getMessage());
                meter("notifications.failure");
                break;
        }
        notificacion.setUltimoError(result.getMessage());
        if (EstadoEnvio.ENVIADO.equals(target.getEstadoEnvio())) {
            maybeFinalizeNotification(notificacion);
        }
    }

    private void maybeFinalizeNotification(Notificacion notificacion) {
        boolean existsPending = notificacionUsuarioRepository.existsByNotificacionIdAndEstadoEnvioIn(
                notificacion.getId(), Arrays.asList(EstadoEnvio.PENDIENTE, EstadoEnvio.FALLO_ENVIO));
        if (!existsPending) {
            notificacion.setEstado(EstadoNotificacion.FINALIZADA);
        }
    }

    private void meter(String name) {
        meterRegistry.ifPresent(registry -> registry.counter(name).increment());
    }
}
