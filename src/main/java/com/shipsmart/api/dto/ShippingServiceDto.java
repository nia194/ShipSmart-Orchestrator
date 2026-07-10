package com.shipsmart.api.dto;

import java.util.List;
import java.util.Map;

/**
 * A single shipping service quote. Mirrors the frontend ShippingService type in shipping-data.ts.
 *
 * <p>Carries an additive {@link QuoteTrust} component (Product Roadmap §12): quote provenance,
 * freshness, provider status, and restrictions the UI renders as trust metadata. A
 * backward-compatible constructor (the original 15-arg signature) keeps every existing call site
 * compiling and defaults trust to {@link QuoteTrust#unknown()} until a provider populates it.
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
        List<String> features,
        QuoteTrust trust) {

    /** Backward-compatible constructor (pre-trust callers); defaults trust to unknown. */
    public ShippingServiceDto(
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
            List<String> features) {
        this(
                id,
                carrier,
                name,
                tier,
                price,
                originalPrice,
                transitDays,
                date,
                deliverBy,
                guaranteed,
                promo,
                ai,
                breakdown,
                details,
                features,
                QuoteTrust.unknown());
    }
}
