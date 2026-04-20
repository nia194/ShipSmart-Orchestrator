package com.shipsmart.api.service;

import com.shipsmart.api.audit.Audited;
import com.shipsmart.api.domain.ShipmentRequest;
import com.shipsmart.api.domain.ShipmentStatus;
import com.shipsmart.api.dto.CreateShipmentRequest;
import com.shipsmart.api.dto.PatchShipmentRequest;
import com.shipsmart.api.dto.ShipmentSummaryDto;
import com.shipsmart.api.exception.OwnershipException;
import com.shipsmart.api.exception.ResourceConflictException;
import com.shipsmart.api.exception.ResourceNotFoundException;
import com.shipsmart.api.repository.ShipmentRequestRepository;
import com.shipsmart.api.repository.ShipmentRequestSpecifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Facade for shipment lifecycle. Single entry point for controllers;
 * orchestrates repository, cache, and audit.
 */
@Service
public class ShipmentService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentService.class);

    private final ShipmentRequestRepository repo;

    public ShipmentService(ShipmentRequestRepository repo) {
        this.repo = repo;
    }

    // ── Reads ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @Cacheable(value = "shipmentById", key = "#id + ':' + #userId")
    public ShipmentSummaryDto getById(UUID id, String userId) {
        ShipmentRequest s = repo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", id.toString()));
        return ShipmentSummaryDto.from(s);
    }

    @Transactional(readOnly = true)
    public Page<ShipmentSummaryDto> list(String userId, ShipmentStatus status,
                                         Instant createdAfter, Pageable pageable) {
        Specification<ShipmentRequest> spec = Specification
                .where(ShipmentRequestSpecifications.ownedBy(userId))
                .and(ShipmentRequestSpecifications.hasStatus(status))
                .and(ShipmentRequestSpecifications.createdAfter(createdAfter));
        return repo.findAll(spec, pageable).map(ShipmentSummaryDto::from);
    }

    // ── Writes ───────────────────────────────────────────────────────────────

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Audited(action = "CREATE", entity = "Shipment")
    public ShipmentSummaryDto create(CreateShipmentRequest req, String userId) {
        ShipmentRequest s = new ShipmentRequest();
        s.setUserId(userId);
        s.setOrigin(req.origin());
        s.setDestination(req.destination());
        s.setDropOffDate(req.dropOffDate());
        s.setExpectedDeliveryDate(req.expectedDeliveryDate());
        s.setPackagesJson(req.packages() == null ? java.util.List.of() : req.packages());
        s.setTotalWeight(req.totalWeight());
        s.setTotalItems(req.totalItems());
        s.setStatus(ShipmentStatus.DRAFT);
        ShipmentRequest saved = repo.save(s);
        log.info("Created shipment {} for user {}", saved.getId(), userId);
        return ShipmentSummaryDto.from(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CacheEvict(value = {"shipmentById", "quotesByShipmentId"}, allEntries = true)
    @Audited(action = "PATCH", entity = "Shipment")
    public ShipmentSummaryDto updatePartial(UUID id, String userId,
                                            PatchShipmentRequest patch, Long expectedVersion) {
        ShipmentRequest s = repo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", id.toString()));

        if (expectedVersion != null && !expectedVersion.equals(s.getVersion())) {
            throw new ResourceConflictException(
                    "If-Match version " + expectedVersion + " does not match current " + s.getVersion());
        }

        if (patch.origin() != null) s.setOrigin(patch.origin());
        if (patch.destination() != null) s.setDestination(patch.destination());
        if (patch.dropOffDate() != null) s.setDropOffDate(patch.dropOffDate());
        if (patch.expectedDeliveryDate() != null) s.setExpectedDeliveryDate(patch.expectedDeliveryDate());
        if (patch.totalWeight() != null) s.setTotalWeight(patch.totalWeight());
        if (patch.totalItems() != null) s.setTotalItems(patch.totalItems());
        if (patch.status() != null) s.setStatus(patch.status());
        // @Version handled by Hibernate on flush
        return ShipmentSummaryDto.from(s);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CacheEvict(value = {"shipmentById", "quotesByShipmentId"}, allEntries = true)
    @Audited(action = "DELETE", entity = "Shipment")
    public void softDelete(UUID id, String userId) {
        ShipmentRequest s = repo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", id.toString()));
        s.setDeletedAt(Instant.now());
    }

    public void assertOwner(UUID shipmentId, String userId) {
        repo.findByIdAndUserId(shipmentId, userId)
                .orElseThrow(() -> new OwnershipException("User does not own shipment " + shipmentId));
    }
}
