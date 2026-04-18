package com.shipsmart.api.repository;

import com.shipsmart.api.domain.ShipmentRequest;
import com.shipsmart.api.domain.ShipmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@code shipment_requests}.
 * Default queries respect the {@code @SQLRestriction("deleted_at IS NULL")}
 * filter on {@link ShipmentRequest}; native methods below bypass it for
 * admin / purge paths.
 */
public interface ShipmentRequestRepository
        extends JpaRepository<ShipmentRequest, UUID>,
                JpaSpecificationExecutor<ShipmentRequest> {

    Optional<ShipmentRequest> findByIdAndUserId(UUID id, String userId);

    Page<ShipmentRequest> findByUserId(String userId, Pageable pageable);

    Page<ShipmentRequest> findByUserIdAndStatus(String userId, ShipmentStatus status, Pageable pageable);

    @Query(value = "SELECT * FROM shipment_requests WHERE id = :id", nativeQuery = true)
    Optional<ShipmentRequest> findByIdIncludingDeleted(UUID id);

    @Query(value = "SELECT id FROM shipment_requests WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff",
            nativeQuery = true)
    List<UUID> findPurgeable(Instant cutoff);
}
