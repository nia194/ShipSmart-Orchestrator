package com.shipsmart.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipsmart.api.domain.SavedOption;
import com.shipsmart.api.dto.*;
import com.shipsmart.api.repository.SavedOptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * SavedOptionService — business logic for saved shipping options.
 * All operations are scoped to the authenticated user's ID.
 */
@Service
public class SavedOptionService {

    private static final Logger log = LoggerFactory.getLogger(SavedOptionService.class);
    private static final DateTimeFormatter SAVED_AT_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SavedOptionRepository repository;

    public SavedOptionService(SavedOptionRepository repository) {
        this.repository = repository;
    }

    /** List all saved options for a user, newest first. */
    public List<SavedOptionResponse> listForUser(String userId) {
        UUID uid = UUID.fromString(userId);
        return repository.findByUserIdOrderByCreatedAtDesc(uid).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Save a shipping option for a user. Returns the saved option response. */
    public SavedOptionResponse save(String userId, SaveOptionRequest request) {
        SavedOption entity = new SavedOption();
        entity.setUserId(UUID.fromString(userId));
        entity.setQuoteServiceId(request.quoteServiceId());
        entity.setCarrier(request.carrier());
        entity.setServiceName(request.serviceName());
        entity.setTier(request.tier() != null ? request.tier() : "STANDARD");
        entity.setPrice(BigDecimal.valueOf(request.price() != null ? request.price() : 0));
        entity.setOriginalPrice(request.originalPrice() != null ? BigDecimal.valueOf(request.originalPrice()) : null);
        entity.setTransitDays(request.transitDays() != null ? request.transitDays() : 0);
        entity.setEstimatedDelivery(request.estimatedDelivery());
        entity.setDeliverByTime(request.deliverByTime());
        entity.setGuaranteed(request.guaranteed() != null && request.guaranteed());
        entity.setPromo(toJson(request.promo()));
        entity.setAiRecommendation(request.aiRecommendation());
        entity.setBreakdown(toJson(request.breakdown()));
        entity.setDetails(toJson(request.details()));
        entity.setFeatures(request.features() != null ? request.features().toArray(new String[0]) : new String[0]);
        entity.setOrigin(request.origin());
        entity.setDestination(request.destination());
        entity.setDropOffDate(request.dropOffDate());
        entity.setExpectedDeliveryDate(request.expectedDeliveryDate());
        entity.setPackageSummary(request.packageSummary());
        entity.setBookUrl(request.bookUrl());

        SavedOption saved = repository.save(entity);
        log.debug("Saved option {} for user {}", saved.getId(), userId);
        return toResponse(saved);
    }

    /**
     * Remove a saved option. Returns true if deleted, false if not found or not owned.
     */
    public boolean remove(String userId, String optionId) {
        UUID uid = UUID.fromString(userId);
        UUID oid = UUID.fromString(optionId);
        Optional<SavedOption> option = repository.findByIdAndUserId(oid, uid);
        if (option.isEmpty()) {
            return false;
        }
        repository.delete(option.get());
        log.debug("Removed saved option {} for user {}", optionId, userId);
        return true;
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private SavedOptionResponse toResponse(SavedOption entity) {
        return new SavedOptionResponse(
                entity.getId().toString(),
                entity.getQuoteServiceId(),
                buildServiceDto(entity),
                entity.getOrigin(),
                entity.getDestination(),
                entity.getDropOffDate(),
                entity.getExpectedDeliveryDate(),
                entity.getPackageSummary(),
                entity.getBookUrl(),
                formatSavedAt(entity.getCreatedAt())
        );
    }

    private ShippingServiceDto buildServiceDto(SavedOption e) {
        return new ShippingServiceDto(
                e.getQuoteServiceId(),
                e.getCarrier(),
                e.getServiceName(),
                e.getTier(),
                e.getPrice().doubleValue(),
                e.getOriginalPrice() != null ? e.getOriginalPrice().doubleValue() : null,
                e.getTransitDays(),
                e.getEstimatedDelivery(),
                e.getDeliverByTime(),
                e.isGuaranteed(),
                fromJson(e.getPromo(), PromoDto.class),
                e.getAiRecommendation(),
                fromJson(e.getBreakdown(), BreakdownDto.class),
                fromJsonMap(e.getDetails()),
                e.getFeatures() != null ? List.of(e.getFeatures()) : List.of()
        );
    }

    private String formatSavedAt(Instant instant) {
        if (instant == null) {
            return Instant.now().atZone(ZoneId.of("UTC")).format(SAVED_AT_FMT);
        }
        return instant.atZone(ZoneId.of("UTC")).format(SAVED_AT_FMT);
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialize to JSON: {}", e.getMessage());
            return null;
        }
    }

    private <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            log.warn("Failed to deserialize JSON: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> fromJsonMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("Failed to deserialize JSON map: {}", e.getMessage());
            return Map.of();
        }
    }
}
