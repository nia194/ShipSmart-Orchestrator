package com.shipsmart.api.dto;

import java.util.List;
import java.util.Map;

/**
 * A single shipping service quote.
 * Mirrors the frontend ShippingService type in shipping-data.ts.
 */
public record ShippingServiceDto(
        String id,
        String carrier,
        String name,
        String tier,
        double price,
        Double originalPrice,
        int transitDays,
        String date,
        String deliverBy,
        boolean guaranteed,
        PromoDto promo,
        String ai,
        BreakdownDto breakdown,
        Map<String, String> details,
        List<String> features
) {}
