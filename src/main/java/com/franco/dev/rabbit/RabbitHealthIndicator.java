package com.franco.dev.rabbit;

import io.jsonwebtoken.lang.Assert;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;

public class RabbitHealthIndicator extends AbstractHealthIndicator {

    private final RabbitTemplate rabbitTemplate;

    public RabbitHealthIndicator(RabbitTemplate rabbitTemplate) {
        super("Rabbit health check failed");
        Assert.notNull(rabbitTemplate, "RabbitTemplate must not be null");
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        builder.up().withDetail("version", getVersion());
    }

    private String getVersion() {
        return this.rabbitTemplate
                .execute((channel) -> channel.getConnection().getServerProperties().get("version").toString());
    }

}