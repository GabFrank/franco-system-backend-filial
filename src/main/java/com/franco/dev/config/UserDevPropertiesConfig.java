package com.franco.dev.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.ClassPathResource;

import javax.annotation.PostConstruct;

/**
 * Configuration class to load user-specific development properties.
 * This allows each developer to have their own configuration without conflicts.
 * 
 * The file application-user-dev.properties should be in src/main/resources/
 * and is ignored by git (added to .gitignore).
 * 
 * If the file doesn't exist, this configuration will be skipped silently.
 * 
 * IMPORTANT: This file will only be loaded when the "dev" profile is active.
 * To use it, run the application with: -Dspring.profiles.active=dev
 * 
 * NOTE: @PropertySource loads properties AFTER the standard Spring Boot property files,
 * allowing it to override properties from application.properties and application-dev.properties.
 * 
 * Spring Boot loads properties in this order:
 * 1. application.properties (base)
 * 2. application-dev.properties (automatic when dev profile is active)
 * 3. application-user-dev.properties (via @PropertySource, CAN override previous values)
 */
@Configuration
@Profile("dev")
@PropertySource(value = "classpath:application-user-dev.properties", ignoreResourceNotFound = true)
public class UserDevPropertiesConfig {

    private static final Logger logger = LoggerFactory.getLogger(UserDevPropertiesConfig.class);
    private static final String USER_DEV_PROPERTIES = "application-user-dev.properties";

    @PostConstruct
    public void loadUserDevProperties() {
        ClassPathResource resource = new ClassPathResource(USER_DEV_PROPERTIES);
        if (resource.exists()) {
            logger.info("✓ User-specific development properties loaded from: {}", USER_DEV_PROPERTIES);
        } else {
            logger.debug("User-specific development properties file not found: {}. Using default values from application-dev.properties", USER_DEV_PROPERTIES);
        }
    }
}

