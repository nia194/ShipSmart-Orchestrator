package com.shipsmart.api.repository;

import com.shipsmart.api.domain.ShipmentRequest;
import com.shipsmart.api.domain.ShipmentStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

/**
 * Composable JPA Specifications for shipment list filtering.
 * Soft-delete is already enforced by the entity-level @SQLRestriction.
 */
public final class ShipmentRequestSpecifications {

    private ShipmentRequestSpecifications() {}

    public static Specification<ShipmentRequest> ownedBy(String userId) {
        return (root, q, cb) -> cb.equal(root.get("userId"), userId);
    }

    public static Specification<ShipmentRequest> hasStatus(ShipmentStatus status) {
        if (status == null) return null;
        return (root, q, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<ShipmentRequest> createdAfter(Instant instant) {
        if (instant == null) return null;
        return (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), instant);
    }
}
