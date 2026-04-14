package com.shipsmart.api.dto;

/**
 * Promotional pricing on a shipping service.
 */
public record PromoDto(
        String code,
        String pct,
        double save,
        String label
) {}
