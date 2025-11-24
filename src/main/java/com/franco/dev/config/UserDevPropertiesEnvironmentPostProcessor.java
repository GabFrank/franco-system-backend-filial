package com.franco.dev.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Properties;

/**
 * EnvironmentPostProcessor to load user-specific development properties.
 * This runs VERY EARLY in the Spring Boot startup process, before @ConfigurationProperties
 * are processed, ensuring that application-user-dev.properties can override values from
 * application.properties and application-dev.properties.
 * 
 * The file application-user-dev.properties should be in src/main/resources/
 * and is ignored by git (added to .gitignore).
 * 
 * IMPORTANT: This only runs when the "dev" profile is active.
 */
public class UserDevPropertiesEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UserDevPropertiesEnvironmentPostProcessor.class);
    private static final String USER_DEV_PROPERTIES = "application-user-dev.properties";
    private static final String PROPERTY_SOURCE_NAME = "userDevProperties";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // Only process if "dev" profile is active
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isDevProfile = false;
        for (String profile : activeProfiles) {
            if ("dev".equals(profile)) {
                isDevProfile = true;
                break;
            }
        }

        if (!isDevProfile) {
            return;
        }

        ClassPathResource resource = new ClassPathResource(USER_DEV_PROPERTIES);
        if (resource.exists()) {
            try {
                Properties properties = new Properties();
                properties.load(resource.getInputStream());
                
                // Add as a PropertySource with HIGH priority to override existing properties
                PropertiesPropertySource propertySource = new PropertiesPropertySource(
                    PROPERTY_SOURCE_NAME, 
                    properties
                );
                
                // Add at the beginning of the property source list to ensure it has highest priority
                // This ensures it overrides properties from application.properties and application-dev.properties
                // even if they were resolved with default values from ${VAR:default} syntax
                environment.getPropertySources().addFirst(propertySource);
                
                logger.info("✓ User-specific development properties loaded from: {} ({} properties loaded)", 
                    USER_DEV_PROPERTIES, properties.size());
                
                // Log the certificate path if it's in the properties to help with debugging
                String certPath = properties.getProperty("sifen.certificado.archivo");
                if (certPath != null) {
                    logger.info("  → SIFEN certificate path will be: {}", certPath);
                }
            } catch (IOException e) {
                logger.warn("Failed to load user-specific development properties from: {}", 
                    USER_DEV_PROPERTIES, e);
            }
        } else {
            logger.debug("User-specific development properties file not found: {}. Using default values.", 
                USER_DEV_PROPERTIES);
        }
    }
}

