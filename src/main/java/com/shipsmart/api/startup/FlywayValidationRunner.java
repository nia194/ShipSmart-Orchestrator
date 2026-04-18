package com.shipsmart.api.startup;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Bean-lifecycle demo #2 — on boot, logs Flyway state and refuses to start
 * if any migration is PENDING. Keeps Supabase as schema owner (Flyway runs
 * in validate mode); this runner just makes drift fatal + visible.
 */
@Configuration
public class FlywayValidationRunner {

    private static final Logger log = LoggerFactory.getLogger(FlywayValidationRunner.class);

    @Bean
    @Order(1)
    public ApplicationRunner flywayValidator(Flyway flyway) {
        return args -> {
            var info = flyway.info();
            long pending = java.util.Arrays.stream(info.pending()).count();
            if (pending > 0) {
                log.error("Flyway reports {} pending migrations — refusing to start", pending);
                throw new IllegalStateException("Flyway pending migrations detected");
            }
            log.info("FlywayValidationRunner: schema validated, {} applied, {} total",
                    info.applied().length, info.all().length);
        };
    }
}
