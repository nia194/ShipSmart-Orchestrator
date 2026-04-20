package com.shipsmart.api.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code saved_options} table.
 * Schema defined in {@code supabase/migrations/20260404030225_*.sql}.
 */
@Entity
@Table(name = "saved_options")
@org.hibernate.annotations.SQLRestriction("deleted_at IS NULL")
public class SavedOption extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "quote_service_id", nullable = false)
    private String quoteServiceId;

    @Column(nullable = false)
    private String carrier;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(nullable = false)
    private String tier;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "original_price")
    private BigDecimal originalPrice;

    @Column(name = "transit_days", nullable = false)
    private int transitDays;

    @Column(name = "estimated_delivery")
    private String estimatedDelivery;

    @Column(name = "deliver_by_time")
    private String deliverByTime;

    @Column
    private boolean guaranteed;

    /** JSONB column — stored as raw JSON string. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String promo;

    @Column(name = "ai_recommendation")
    private String aiRecommendation;

    /** JSONB column — stored as raw JSON string. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String breakdown;

    /** JSONB column — stored as raw JSON string. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String details;

    /** TEXT[] in Postgres — stored as comma-separated or array via converter. */
    @Column(columnDefinition = "text[]")
    private String[] features;

    @Column(nullable = false)
    private String origin;

    @Column(nullable = false)
    private String destination;

    @Column(name = "drop_off_date")
    private String dropOffDate;

    @Column(name = "expected_delivery_date")
    private String expectedDeliveryDate;

    @Column(name = "package_summary")
    private String packageSummary;

    @Column(name = "book_url")
    private String bookUrl;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    // ── Getters / Setters ─────────────────────────────────────────────────

    public UUID getId() { return id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getQuoteServiceId() { return quoteServiceId; }
    public void setQuoteServiceId(String quoteServiceId) { this.quoteServiceId = quoteServiceId; }

    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(BigDecimal originalPrice) { this.originalPrice = originalPrice; }

    public int getTransitDays() { return transitDays; }
    public void setTransitDays(int transitDays) { this.transitDays = transitDays; }

    public String getEstimatedDelivery() { return estimatedDelivery; }
    public void setEstimatedDelivery(String estimatedDelivery) { this.estimatedDelivery = estimatedDelivery; }

    public String getDeliverByTime() { return deliverByTime; }
    public void setDeliverByTime(String deliverByTime) { this.deliverByTime = deliverByTime; }

    public boolean isGuaranteed() { return guaranteed; }
    public void setGuaranteed(boolean guaranteed) { this.guaranteed = guaranteed; }

    public String getPromo() { return promo; }
    public void setPromo(String promo) { this.promo = promo; }

    public String getAiRecommendation() { return aiRecommendation; }
    public void setAiRecommendation(String aiRecommendation) { this.aiRecommendation = aiRecommendation; }

    public String getBreakdown() { return breakdown; }
    public void setBreakdown(String breakdown) { this.breakdown = breakdown; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String[] getFeatures() { return features; }
    public void setFeatures(String[] features) { this.features = features; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public String getDropOffDate() { return dropOffDate; }
    public void setDropOffDate(String dropOffDate) { this.dropOffDate = dropOffDate; }

    public String getExpectedDeliveryDate() { return expectedDeliveryDate; }
    public void setExpectedDeliveryDate(String expectedDeliveryDate) { this.expectedDeliveryDate = expectedDeliveryDate; }

    public String getPackageSummary() { return packageSummary; }
    public void setPackageSummary(String packageSummary) { this.packageSummary = packageSummary; }

    public String getBookUrl() { return bookUrl; }
    public void setBookUrl(String bookUrl) { this.bookUrl = bookUrl; }

    public Instant getCreatedAt() { return createdAt; }
}
