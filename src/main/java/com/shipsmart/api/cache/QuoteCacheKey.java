package com.shipsmart.api.cache;

import com.shipsmart.api.provider.ProviderQuoteRequest;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable cache key for a quote request.
 *
 * <p>Why a dedicated class instead of {@link ProviderQuoteRequest}?
 * <ul>
 *   <li>{@code ProviderQuoteRequest} carries a {@code double totalWeight}.
 *       Using a double directly in equals/hashCode is unsafe (NaN, ±0).
 *       We bucket weight to the nearest kg here — quotes for 10.01 kg and
 *       10.03 kg should hit the same cache entry.</li>
 *   <li>The key implements {@link Comparable} so it can be indexed in a
 *       {@code TreeSet}/{@code TreeMap} (used by the
 *       {@link QuoteCache#keysSortedByRoute()} helper).</li>
 * </ul>
 *
 * <p>Style: this is a classic Value Object — final class, final fields,
 * explicit equals/hashCode/toString, no setters, defensive normalization
 * in the constructor. Swapping to a record would work too, but keeping
 * this as a final class lets us document the invariants inline.
 */
public final class QuoteCacheKey implements Comparable<QuoteCacheKey> {

    private final String origin;
    private final String destination;
    private final LocalDate dropOffDate;
    private final LocalDate expectedDeliveryDate;
    private final int weightBucketKg;
    private final int totalItems;

    public QuoteCacheKey(
            String origin,
            String destination,
            LocalDate dropOffDate,
            LocalDate expectedDeliveryDate,
            double totalWeight,
            int totalItems) {
        this.origin = normalize(origin);
        this.destination = normalize(destination);
        // LocalDate is already immutable — no defensive copy needed.
        // If we were storing java.util.Date, we'd copy into new Date(orig.getTime()).
        this.dropOffDate = Objects.requireNonNull(dropOffDate, "dropOffDate");
        this.expectedDeliveryDate = Objects.requireNonNull(expectedDeliveryDate, "expectedDeliveryDate");
        this.weightBucketKg = Math.max(0, (int) Math.round(totalWeight));
        this.totalItems = Math.max(0, totalItems);
    }

    /** Convenience: lift from the existing request record. */
    public static QuoteCacheKey from(ProviderQuoteRequest r) {
        return new QuoteCacheKey(
                r.origin(), r.destination(),
                r.dropOffDate(), r.expectedDeliveryDate(),
                r.totalWeight(), r.totalItems());
    }

    public String origin()                  { return origin; }
    public String destination()             { return destination; }
    public LocalDate dropOffDate()          { return dropOffDate; }
    public LocalDate expectedDeliveryDate() { return expectedDeliveryDate; }
    public int weightBucketKg()             { return weightBucketKg; }
    public int totalItems()                 { return totalItems; }

    // ── Object contract ──────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QuoteCacheKey k)) return false;
        return weightBucketKg == k.weightBucketKg
                && totalItems == k.totalItems
                && origin.equals(k.origin)
                && destination.equals(k.destination)
                && dropOffDate.equals(k.dropOffDate)
                && expectedDeliveryDate.equals(k.expectedDeliveryDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(origin, destination, dropOffDate,
                expectedDeliveryDate, weightBucketKg, totalItems);
    }

    @Override
    public String toString() {
        return origin + "→" + destination
                + " [" + dropOffDate + "…" + expectedDeliveryDate + "]"
                + " " + weightBucketKg + "kg×" + totalItems;
    }

    /**
     * Natural ordering: origin, then destination, then drop-off date.
     * Lets a {@link java.util.TreeMap} view keys in a stable, readable order
     * — handy for the cache dump endpoint (future work).
     */
    @Override
    public int compareTo(QuoteCacheKey o) {
        int c = origin.compareTo(o.origin);
        if (c != 0) return c;
        c = destination.compareTo(o.destination);
        if (c != 0) return c;
        return dropOffDate.compareTo(o.dropOffDate);
    }

    private static String normalize(String s) {
        Objects.requireNonNull(s, "location");
        return s.trim().toUpperCase();
    }
}
