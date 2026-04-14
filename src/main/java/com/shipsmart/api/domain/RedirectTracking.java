package com.shipsmart.api.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code redirect_tracking} table.
 * Tracks when a user clicks "Book" on a shipping quote.
 */
@Entity
@Table(name = "redirect_tracking")
public class RedirectTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "service_id", nullable = false)
    private String serviceId;

    @Column(nullable = false)
    private String carrier;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(name = "redirect_url", nullable = false)
    private String redirectUrl;

    @Column
    private String origin;

    @Column
    private String destination;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    // ── Getters / Setters ─────────────────────────────────────────────────

    public UUID getId() { return id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }

    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getRedirectUrl() { return redirectUrl; }
    public void setRedirectUrl(String redirectUrl) { this.redirectUrl = redirectUrl; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public Instant getCreatedAt() { return createdAt; }
}
