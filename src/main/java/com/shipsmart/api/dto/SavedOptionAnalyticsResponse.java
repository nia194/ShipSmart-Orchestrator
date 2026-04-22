package com.shipsmart.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Analytics view over a user's saved options. All collections are
 * deliberately chosen to exercise a different part of the Collections
 * framework (see {@link com.shipsmart.api.service.SavedOptionAnalyticsService}).
 */
public record SavedOptionAnalyticsResponse(
        long totalSavedOptions,
        /** TreeMap-backed: carriers sorted alphabetically, count per carrier. */
        Map<String, Long> carriersSortedAlphabetical,
        /** LinkedHashMap: carriers ordered by count DESC (most-saved first). */
        Map<String, Long> carriersSortedByCount,
        /** TreeSet-derived: tiers sorted alphabetically, no duplicates. */
        List<String> tiersAlphabetical,
        /** LinkedHashSet-derived: distinct carriers in insertion order. */
        Set<String> distinctCarriersInOrder,
        /** PriorityQueue-derived top-N by price (highest first). */
        List<TopExpensive> topExpensive,
        /** TreeMap<YearMonth as "YYYY-MM", Long>: saves per month, chronological. */
        Map<String, Long> savesByMonth,
        /** EnumMap-derived: route frequency bucket counts. */
        Map<String, Long> routeFrequencyBuckets
) {
    /** Nested record — the top-N entry shape. */
    public record TopExpensive(String carrier, String serviceName, BigDecimal price) {}
}
