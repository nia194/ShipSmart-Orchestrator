package com.shipsmart.api.provider;

import java.util.Comparator;

/**
 * Static {@link Comparator} library for {@link ProviderQuote}.
 *
 * <p>Comparator vs Comparable (for the learner):
 * <ul>
 *   <li>{@code Comparable} is the <em>natural</em> ordering a type
 *       defines for itself. A {@code ProviderQuote} doesn't have one —
 *       reasonable people would disagree on whether price or transit
 *       days wins — so we leave the record un-Comparable.</li>
 *   <li>{@code Comparator} is external: a caller picks the policy
 *       (cheapest? fastest? guaranteed first?) at sort time. Comparators
 *       also chain cleanly via {@link Comparator#thenComparing}.</li>
 * </ul>
 *
 * <p>This class is {@code final} with a private constructor — a classic
 * "utility class" pattern ruling out subclassing or instantiation.
 */
public final class QuoteComparators {

    private QuoteComparators() { /* no instances */ }

    /** Cheapest first. Uses BigDecimal's compareTo (never use equals for numbers!). */
    public static final Comparator<ProviderQuote> BY_PRICE_ASC =
            Comparator.comparing(ProviderQuote::price);

    /** Fastest first (fewest transit days). */
    public static final Comparator<ProviderQuote> BY_TRANSIT_ASC =
            Comparator.comparingInt(ProviderQuote::transitDays);

    /** Guaranteed services first (true before false), then cheapest. Chaining demo. */
    public static final Comparator<ProviderQuote> GUARANTEED_THEN_PRICE =
            Comparator.comparing(ProviderQuote::guaranteed).reversed()
                    .thenComparing(BY_PRICE_ASC);

    /**
     * Balanced score: normalized price + transit. Useful for a
     * "recommended" sort. The returned Comparator builds a fresh closure
     * over a snapshot of the caller's pricing scale.
     */
    public static Comparator<ProviderQuote> balanced(double priceWeight, double transitWeight) {
        return Comparator.comparingDouble(q ->
                priceWeight * q.price().doubleValue() + transitWeight * q.transitDays());
    }
}
