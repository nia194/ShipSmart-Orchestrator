package com.shipsmart.api.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity for the {@code shipment_requests} table.
 * Schema defined in {@code supabase/migrations/20260404030225_*.sql}.
 */
@Entity
@Table(name = "shipment_requests")
@org.hibernate.annotations.SQLRestriction("deleted_at IS NULL")
public class ShipmentRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentStatus status = ShipmentStatus.DRAFT;

    @Column(name = "user_id")
    private String userId;

    @Column(nullable = false)
    private String origin;

    @Column(nullable = false)
    private String destination;

    @Column(name = "drop_off_date", nullable = false)
    private LocalDate dropOffDate;

    @Column(name = "expected_delivery_date", nullable = false)
    private LocalDate expectedDeliveryDate;

    /** Stored as JSONB in Postgres. We persist the raw JSON string. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String packages;

    @Column(name = "total_weight")
    private Double totalWeight;

    @Column(name = "total_items")
    private Integer totalItems;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    // ── Getters / Setters ─────────────────────────────────────────────────

    public UUID getId() { return id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public LocalDate getDropOffDate() { return dropOffDate; }
    public void setDropOffDate(LocalDate dropOffDate) { this.dropOffDate = dropOffDate; }

    public LocalDate getExpectedDeliveryDate() { return expectedDeliveryDate; }
    public void setExpectedDeliveryDate(LocalDate expectedDeliveryDate) { this.expectedDeliveryDate = expectedDeliveryDate; }

    public String getPackages() { return packages; }
    public void setPackages(String packages) { this.packages = packages; }

    /** Convenience setter that serialises a list of DTOs to JSON. */
    public void setPackagesJson(Object packagesObj) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            this.packages = mapper.writeValueAsString(packagesObj);
        } catch (Exception e) {
            this.packages = "[]";
        }
    }

    public Double getTotalWeight() { return totalWeight; }
    public void setTotalWeight(Double totalWeight) { this.totalWeight = totalWeight; }

    public Integer getTotalItems() { return totalItems; }
    public void setTotalItems(Integer totalItems) { this.totalItems = totalItems; }

    public Instant getCreatedAt() { return createdAt; }

    public ShipmentStatus getStatus() { return status; }
    public void setStatus(ShipmentStatus status) { this.status = status; }
}
