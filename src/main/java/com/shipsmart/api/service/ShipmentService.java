package com.shipsmart.api.service;

import org.springframework.stereotype.Service;

/**
 * ShipmentService — core business logic for shipment requests.
 *
 * TODO: Inject ShipmentRepository (JPA or JDBC) when database layer is wired.
 * TODO: Add validation logic before persisting.
 * TODO: Map domain objects to DTOs and vice versa.
 *
 * This service owns the shipment_requests table in Supabase Postgres.
 * Schema reference (from Lovable migration):
 *   - id, created_at, user_id, origin, destination
 *   - drop_off_date, expected_delivery_date
 *   - packages (JSONB), total_items, total_weight
 */
@Service
public class ShipmentService {

    // TODO: private final ShipmentRepository shipmentRepository;

    // TODO: public List<ShipmentDto> listForUser(String userId) { ... }
    // TODO: public ShipmentDto getById(String id, String userId) { ... }
    // TODO: public ShipmentDto create(CreateShipmentRequest request, String userId) { ... }
}
