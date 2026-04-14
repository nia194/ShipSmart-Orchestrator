package com.shipsmart.api.repository;

import com.shipsmart.api.domain.ShipmentRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@code shipment_requests}.
 */
public interface ShipmentRequestRepository extends JpaRepository<ShipmentRequest, UUID> {
}
