package com.shipsmart.api.provider.metrics;

import java.time.Instant;

/**
 * A single provider-call observation. Records are the right shape for
 * immutable, equals-by-value DTOs — Java generates equals/hashCode/toString.
 */
public record ProviderCallEvent(
        String carrier,
        ProviderCallOutcome outcome,
        long latencyMillis,
        int quotesReturned,
        Instant observedAt
) {}
