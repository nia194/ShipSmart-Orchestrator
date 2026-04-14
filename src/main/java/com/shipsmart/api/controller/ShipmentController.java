package com.shipsmart.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Shipment API endpoints.
 * Owns core shipment request lifecycle (create, list, get).
 *
 * TODO: Implement shipment service and connect to Supabase Postgres via JPA/JDBC.
 * TODO: Add authentication via Supabase JWT validation.
 * TODO: Replace Map<> response types with proper DTOs.
 *
 * Service boundary: This controller owns shipment records.
 * The FastAPI service (api-python) may call this service for orchestration workflows.
 */
@RestController
@RequestMapping("/api/v1/shipments")
public class ShipmentController {

    // TODO: Inject ShipmentService once implemented
    // private final ShipmentService shipmentService;

    /**
     * GET /api/v1/shipments
     * List shipments for the authenticated user.
     * TODO: Implement — filter by user from JWT subject.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listShipments() {
        // TODO: return shipmentService.listForUser(currentUserId);
        return ResponseEntity.ok(Map.of(
                "data", java.util.List.of(),
                "message", "TODO: implement shipment listing"
        ));
    }

    /**
     * GET /api/v1/shipments/{id}
     * Get a single shipment by ID.
     * TODO: Implement — validate user owns the shipment.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getShipment(@PathVariable String id) {
        // TODO: return shipmentService.getById(id, currentUserId);
        return ResponseEntity.ok(Map.of(
                "id", id,
                "message", "TODO: implement shipment retrieval"
        ));
    }

    /**
     * POST /api/v1/shipments
     * Create a new shipment request.
     * TODO: Implement — validate request body, persist to Supabase, return created record.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createShipment(@RequestBody Map<String, Object> body) {
        // TODO: return shipmentService.create(body, currentUserId);
        return ResponseEntity.status(201).body(Map.of(
                "message", "TODO: implement shipment creation",
                "received", body
        ));
    }
}
