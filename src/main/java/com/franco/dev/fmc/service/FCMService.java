package com.franco.dev.fmc.service;

import com.franco.dev.fmc.model.DeliveryResult;
import com.franco.dev.fmc.model.PushNotificationRequest;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.google.gson.Gson;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FCMService {

    private static final String DEFAULT_DATA_PATH = "/";
    private final Logger logger = LoggerFactory.getLogger(FCMService.class);
    private final Gson gson;

    public FCMService(Gson gson) {
        this.gson = gson;
    }

    public DeliveryResult sendToToken(String token, PushNotificationRequest request) {
        try {
            Message message = baseMessageBuilder(request)
                    .setToken(token)
                    .putData("path", request.getData() != null ? request.getData() : DEFAULT_DATA_PATH)
                    .build();
            FirebaseMessaging.getInstance().send(message);
            if (logger.isDebugEnabled()) {
                logger.debug("Notificación enviada a token {}", token);
            }
            return DeliveryResult.success();
        } catch (FirebaseMessagingException ex) {
            MessagingErrorCode code = ex.getMessagingErrorCode();
            if (logger.isDebugEnabled()) {
                logger.debug("Error FCM [{}] al enviar a token {} payload {}", code, token,
                        gson.toJson(safeLogPayload(request)));
            }
            if (code == MessagingErrorCode.INVALID_ARGUMENT || code == MessagingErrorCode.UNREGISTERED) {
                return DeliveryResult.invalidToken(ex.getMessage(), code);
            }
            if (code == MessagingErrorCode.UNAVAILABLE || code == MessagingErrorCode.INTERNAL) {
                return DeliveryResult.transientError(ex.getMessage(), code);
            }
            return DeliveryResult.failure(ex.getMessage(), code);
        } catch (Exception ex) {
            logger.error("Error no controlado enviando notificación a token {}", token, ex);
            return DeliveryResult.failure(ex.getMessage(), null);
        }
    }

    public DeliveryResult sendToTopic(PushNotificationRequest request) {
        try {
            Message message = baseMessageBuilder(request)
                    .setTopic(request.getTopic())
                    .putData("path", request.getData() != null ? request.getData() : DEFAULT_DATA_PATH)
                    .build();
            FirebaseMessaging.getInstance().send(message);
            return DeliveryResult.success();
        } catch (FirebaseMessagingException ex) {
            MessagingErrorCode code = ex.getMessagingErrorCode();
            return DeliveryResult.failure(ex.getMessage(), code);
        }
    }

    private Message.Builder baseMessageBuilder(PushNotificationRequest request) {
        return Message.builder()
                .setApnsConfig(getApnsConfig(request.getTopic()))
                .setAndroidConfig(getAndroidConfig(request.getTopic()))
                .setNotification(Notification.builder()
                        .setTitle(request.getTitle())
                        .setBody(request.getMessage())
                        .build());
    }

    private AndroidConfig getAndroidConfig(String topic) {
        String collapseKey = topic != null ? topic : "direct-notification";
        return AndroidConfig.builder()
                .setTtl(Duration.ofMinutes(2).toMillis())
                .setCollapseKey(collapseKey)
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(AndroidNotification.builder().setTag(collapseKey).build())
                .build();
    }

    private ApnsConfig getApnsConfig(String topic) {
        String apnsTopic = topic != null ? topic : "direct-notification";
        return ApnsConfig.builder()
                .setAps(Aps.builder().setCategory(apnsTopic).setThreadId(apnsTopic).build())
                .build();
    }

    private Object safeLogPayload(PushNotificationRequest request) {
        return new Object() {
            final String title = request.getTitle();
            final String type = request.getType();
            final String topic = request.getTopic();
        };
    }
}
