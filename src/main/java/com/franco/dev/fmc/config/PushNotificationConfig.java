package com.franco.dev.fmc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class PushNotificationConfig {

    @Bean
    public Gson pushNotificationGson() {
        return new GsonBuilder()
                .disableHtmlEscaping()
                .create();
    }

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("push-notification-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(500);
        executor.initialize();
        return executor;
    }
}
