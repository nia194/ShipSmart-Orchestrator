package com.shipsmart.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Request body for POST /api/v1/saved-options.
 * Matches the payload sent by the frontend useSavedOptions hook.
 */
public record SaveOptionRequest(
        @NotBlank @Size(max = 100) String quoteServiceId,
        @NotBlank @Size(max = 100) String carrier,
        @NotBlank @Size(max = 100) String serviceName,
        @NotBlank @Size(max = 200) String origin,
        @NotBlank @Size(max = 200) String destination,
        @Size(max = 50) String tier,
        @Positive Double price,
        @Positive Double originalPrice,
        @PositiveOrZero Integer transitDays,
        @Size(max = 50) String estimatedDelivery,
        @Size(max = 50) String deliverByTime,
        Boolean guaranteed,
        Object promo,
        @Size(max = 500) String aiRecommendation,
        Object breakdown,
        Object details,
        List<String> features,
        @Size(max = 50) String dropOffDate,
        @Size(max = 50) String expectedDeliveryDate,
        @Size(max = 500) String packageSummary,
        @Size(max = 2000) String bookUrl
) {}
