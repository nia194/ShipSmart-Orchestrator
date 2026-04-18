package com.shipsmart.api.repository;

import com.shipsmart.api.domain.ShipmentRequest;
import com.shipsmart.api.domain.ShipmentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers-backed integration test for {@link ShipmentRequestRepository}.
 *
 * Exercises the interview-critical behaviours that unit tests can't:
 *   - JPA Specifications filtering (owner, status, createdAfter)
 *   - Soft-delete enforcement via {@code @SQLRestriction("deleted_at IS NULL")}
 *   - Native-query escape hatch ({@code findByIdIncludingDeleted}) that bypasses it
 *   - Pagination + sort on the composite spec
 *
 * Runs against real Postgres (not H2) so JSONB + UUID + timestamptz all behave
 * as they do in production.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
})
@DirtiesContext
@ExtendWith(ShipmentRequestRepositoryIT.DockerAvailable.class)
class ShipmentRequestRepositoryIT {

    /** Skip the whole class when no Docker daemon is reachable (e.g. CI without DinD, dev laptop with Docker Desktop down). */
    static class DockerAvailable implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext ctx) {
            try {
                return DockerClientFactory.instance().isDockerAvailable()
                        ? ConditionEvaluationResult.enabled("Docker available")
                        : ConditionEvaluationResult.disabled("Docker not available");
            } catch (Throwable t) {
                return ConditionEvaluationResult.disabled("Docker not available: " + t.getMessage());
            }
        }
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withReuse(true);

    @Autowired private ShipmentRequestRepository repo;

    private static final String USER_A = "user-a";
    private static final String USER_B = "user-b";

    @BeforeEach
    void cleanSlate() {
        repo.deleteAll();
    }

    private ShipmentRequest make(String userId, ShipmentStatus status, String origin) {
        ShipmentRequest s = new ShipmentRequest();
        s.setUserId(userId);
        s.setOrigin(origin);
        s.setDestination("90210");
        s.setDropOffDate(LocalDate.of(2026, 5, 1));
        s.setExpectedDeliveryDate(LocalDate.of(2026, 5, 7));
        s.setPackagesJson(java.util.List.of());
        s.setTotalWeight(10.0);
        s.setTotalItems(1);
        s.setStatus(status);
        return s;
    }

    // ── Specifications ───────────────────────────────────────────────────────

    @Test
    void ownedBy_filtersByUserId() {
        repo.save(make(USER_A, ShipmentStatus.DRAFT, "10001"));
        repo.save(make(USER_A, ShipmentStatus.DRAFT, "10002"));
        repo.save(make(USER_B, ShipmentStatus.DRAFT, "20001"));

        var page = repo.findAll(
                Specification.where(ShipmentRequestSpecifications.ownedBy(USER_A)),
                PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).allMatch(s -> s.getUserId().equals(USER_A));
    }

    @Test
    void hasStatus_narrowsResults() {
        repo.save(make(USER_A, ShipmentStatus.DRAFT, "10001"));
        repo.save(make(USER_A, ShipmentStatus.QUOTED, "10002"));
        repo.save(make(USER_A, ShipmentStatus.BOOKED, "10003"));

        var spec = Specification
                .where(ShipmentRequestSpecifications.ownedBy(USER_A))
                .and(ShipmentRequestSpecifications.hasStatus(ShipmentStatus.QUOTED));

        var page = repo.findAll(spec, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getStatus()).isEqualTo(ShipmentStatus.QUOTED);
    }

    @Test
    void createdAfter_nullSpecIsNoOp() {
        repo.save(make(USER_A, ShipmentStatus.DRAFT, "10001"));

        var spec = Specification
                .where(ShipmentRequestSpecifications.ownedBy(USER_A))
                .and(ShipmentRequestSpecifications.createdAfter(null));

        assertThat(repo.findAll(spec, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
    }

    // ── Pagination ───────────────────────────────────────────────────────────

    @Test
    void pagination_returnsRequestedPageSize() {
        for (int i = 0; i < 25; i++) {
            repo.save(make(USER_A, ShipmentStatus.DRAFT, "1000" + i));
        }

        var page = repo.findAll(
                Specification.where(ShipmentRequestSpecifications.ownedBy(USER_A)),
                PageRequest.of(1, 10, Sort.by("origin")));

        assertThat(page.getNumber()).isEqualTo(1);
        assertThat(page.getContent()).hasSize(10);
        assertThat(page.getTotalElements()).isEqualTo(25);
        assertThat(page.getTotalPages()).isEqualTo(3);
    }

    // ── Soft delete ──────────────────────────────────────────────────────────

    @Test
    void softDeletedRows_excludedFromDefaultQueries() {
        ShipmentRequest live = repo.save(make(USER_A, ShipmentStatus.DRAFT, "10001"));
        ShipmentRequest deleted = repo.save(make(USER_A, ShipmentStatus.DRAFT, "10002"));
        deleted.setDeletedAt(Instant.now());
        repo.save(deleted);

        var page = repo.findAll(
                Specification.where(ShipmentRequestSpecifications.ownedBy(USER_A)),
                PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getId()).isEqualTo(live.getId());

        // default findById must respect the filter too
        assertThat(repo.findById(deleted.getId())).isEmpty();
    }

    @Test
    void findByIdIncludingDeleted_bypassesSoftDeleteFilter() {
        ShipmentRequest s = repo.save(make(USER_A, ShipmentStatus.DRAFT, "10001"));
        s.setDeletedAt(Instant.now());
        repo.save(s);

        assertThat(repo.findById(s.getId())).isEmpty();
        assertThat(repo.findByIdIncludingDeleted(s.getId())).isPresent();
    }

    // ── Owner + id lookup ────────────────────────────────────────────────────

    @Test
    void findByIdAndUserId_enforcesOwnership() {
        ShipmentRequest a = repo.save(make(USER_A, ShipmentStatus.DRAFT, "10001"));

        assertThat(repo.findByIdAndUserId(a.getId(), USER_A)).isPresent();
        assertThat(repo.findByIdAndUserId(a.getId(), USER_B)).isEmpty();
    }
}
