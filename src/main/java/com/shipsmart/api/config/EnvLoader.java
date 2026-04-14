package com.shipsmart.api.config;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Loads environment variables from .env file at application startup.
 * This component must be initialized before DataSource configuration.
 */
@Component
public class EnvLoader {

    private static final Logger log = LoggerFactory.getLogger(EnvLoader.class);

    @PostConstruct
    public void loadEnv() {
        try {
            Dotenv dotenv = Dotenv.configure()
                    .ignoreIfMissing()
                    .load();

            // Load variables into System properties and let local .env win over
            // inherited shell env vars. This keeps `./gradlew bootRun` stable
            // when the developer terminal already contains datasource settings
            // from another project.
            dotenv.entries().forEach(entry ->
                    System.setProperty(entry.getKey(), entry.getValue())
            );

            log.info("Environment variables loaded from .env file (overriding inherited shell env)");
        } catch (DotenvException e) {
            log.warn("No .env file found or error reading it (OK for production): {}", e.getMessage());
        }
    }
}
