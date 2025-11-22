package com.franco.dev.fmc.health;

import com.google.firebase.FirebaseApp;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class NotificationHealthIndicator implements HealthIndicator {

    private final ResourceLoader resourceLoader;

    @Value("${app.firebase-configuration-file}")
    private String firebaseConfigFile;

    public NotificationHealthIndicator(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public Health health() {
        try {
            Resource resource = resourceLoader.getResource("classpath:" + firebaseConfigFile);
            if (!resource.exists()) {
                return Health.down().withDetail("firebaseConfig", "Archivo no encontrado").build();
            }
            if (FirebaseApp.getApps().isEmpty()) {
                return Health.down().withDetail("firebase", "Aplicación no inicializada").build();
            }
            resource.getInputStream().close();
            return Health.up().build();
        } catch (IOException e) {
            return Health.down(e).build();
        }
    }
}
