package com.shipsmart.api.controller;

import com.shipsmart.api.auth.AuthHelper;
import com.shipsmart.api.dto.CreateShipmentRequest;
import com.shipsmart.api.dto.PatchShipmentRequest;
import com.shipsmart.api.dto.ShipmentSummaryDto;
import com.shipsmart.api.exception.OwnershipException;
import com.shipsmart.api.service.ShipmentService;
import com.shipsmart.api.web.Idempotent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import com.shipsmart.api.domain.ShipmentStatus;

@RestController
@RequestMapping("/api/v1/shipments")
@Tag(name = "Shipments", description = "Create, read, update, soft-delete user shipments")
public class ShipmentController {

    private final ShipmentService shipments;

    public ShipmentController(ShipmentService shipments) {
        this.shipments = shipments;
    }

    @GetMapping
    @Operation(summary = "List shipments for the authenticated user (paginated, filterable)")
    public Page<ShipmentSummaryDto> list(@RequestParam(required = false) ShipmentStatus status,
                                         @RequestParam(required = false) Instant createdAfter,
                                         @PageableDefault(size = 20) Pageable pageable) {
        String userId = requireUserId();
        return shipments.list(userId, status, createdAfter, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single shipment the caller owns")
    public ResponseEntity<ShipmentSummaryDto> get(@PathVariable UUID id) {
        String userId = requireUserId();
        ShipmentSummaryDto dto = shipments.getById(id, userId);
        return ResponseEntity.ok()
                .eTag("\"" + dto.version() + "\"")
                .body(dto);
    }

    @PostMapping
    @Idempotent
    @Operation(summary = "Create a new shipment. Requires Idempotency-Key header.")
    public ResponseEntity<ShipmentSummaryDto> create(@Valid @RequestBody CreateShipmentRequest body,
                                                     UriComponentsBuilder uri) {
        String userId = requireUserId();
        ShipmentSummaryDto created = shipments.create(body, userId);
        URI location = uri.path("/api/v1/shipments/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location)
                .eTag("\"" + created.version() + "\"")
                .body(created);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Partial update; enforces If-Match for optimistic concurrency")
    public ResponseEntity<ShipmentSummaryDto> patch(@PathVariable UUID id,
                                                    @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                    @Valid @RequestBody PatchShipmentRequest body) {
        String userId = requireUserId();
        Long expected = parseIfMatch(ifMatch);
        ShipmentSummaryDto updated = shipments.updatePartial(id, userId, body, expected);
        return ResponseEntity.ok()
                .eTag("\"" + updated.version() + "\"")
                .body(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete; resource remains recoverable for 30 days")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        String userId = requireUserId();
        shipments.softDelete(id, userId);
        return ResponseEntity.noContent().build();
    }

    private String requireUserId() {
        return AuthHelper.getUserId()
                .orElseThrow(() -> new OwnershipException("Authenticated user required"));
    }

    private Long parseIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) return null;
        String trimmed = ifMatch.replace("\"", "").replace("W/", "").trim();
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
