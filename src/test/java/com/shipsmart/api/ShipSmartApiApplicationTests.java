package com.shipsmart.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Full-context smoke test: boots the entire Spring application context against the in-memory H2
 * test profile and asserts every bean wires.
 *
 * <p>This is the cheap guard for boot-time regressions that slice tests (@WebMvcTest
 * / @DataJpaTest) can't catch — e.g. a {@code @Component} with two constructors and no
 * {@code @Autowired} hint (Spring "No default constructor found"), or a misconfigured tracing
 * exporter. The full {@code java -jar} boot exercised by ShipSmart-Test's live e2e hits the same
 * wiring; this catches it in CI without Docker or a real Postgres.
 *
 * <p>Flyway is pointed at an empty location so its validator bean still wires
 * (FlywayValidationRunner injects a {@link org.flywaydb.core.Flyway}) but no Postgres-specific
 * migration runs against H2; Hibernate create-drop builds the schema from the entities (see
 * application-test.yml).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "spring.flyway.locations=classpath:db/e2e-none",
            "spring.flyway.fail-on-missing-locations=false",
        })
class ShipSmartApiApplicationTests {

    @Test
    void contextLoads() {
        // Success = the full ApplicationContext started and every bean (incl.
        // QuoteCache, the provider fanout, security, idempotency, audit) wired.
    }
}
