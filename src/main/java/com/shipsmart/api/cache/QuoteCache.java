package com.shipsmart.api.cache;

import com.shipsmart.api.provider.ProviderQuote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * In-memory LRU cache for fanout results, keyed by {@link QuoteCacheKey}.
 *
 * <p>This class is an intentional survey of Collections Framework pieces:
 * <ul>
 *   <li><b>{@link LinkedHashMap} with {@code accessOrder=true}</b> + an
 *       overridden {@code removeEldestEntry} is the textbook LRU pattern.
 *       We wrap it in {@link Collections#synchronizedMap} so concurrent
 *       request threads can read/write safely (LinkedHashMap is not
 *       thread-safe, and its access-order mutation happens on reads too).</li>
 *   <li><b>{@link ConcurrentHashMap}</b> for the hit/miss counters — lock-free
 *       increment via {@link LongAdder}.</li>
 *   <li><b>{@link TreeMap}</b> (accessed via {@link NavigableMap}) to expose a
 *       sorted view of keys for the debug/analytics endpoints.</li>
 * </ul>
 *
 * <p>Complements, does not replace, Caffeine. Caffeine is the right tool
 * for production. This class exists to (a) cover collections concepts
 * end-to-end and (b) give us a place to hang domain-specific logic like
 * "reject cached entries older than X minutes."
 */
@Component
public class QuoteCache {

    private static final Logger log = LoggerFactory.getLogger(QuoteCache.class);

    /** Package-private so tests can swap in a fixed clock. */
    final Clock clock;
    private final int maxEntries;
    private final long ttlMillis;

    /**
     * Inner (non-static) class: the eldest-entry eviction callback needs
     * access to the enclosing {@code maxEntries}. Declaring it {@code static}
     * is slightly more efficient (no synthetic outer-ref field) and is what
     * a real production variant would use — we keep it non-static here
     * specifically to demonstrate the difference to a learner.
     *
     * <p>See also {@link BoundedAccessOrderMap} below (the static alternative,
     * unused — kept as a commented-out reference only if we switch later).
     */
    private final class LruMap extends LinkedHashMap<QuoteCacheKey, Entry> {
        LruMap() {
            // initialCapacity=16, loadFactor=0.75, accessOrder=true (move on GET).
            super(16, 0.75f, true);
        }
        @Override
        protected boolean removeEldestEntry(Map.Entry<QuoteCacheKey, Entry> eldest) {
            boolean remove = size() > maxEntries;
            if (remove) evictions.increment();
            return remove;
        }
    }

    /**
     * A cached entry. Nested <b>static</b> class — independent of the outer
     * instance. Made a {@code record} so equals/hashCode/toString come for
     * free, and the instance is deeply immutable as long as the list is
     * handed in already-unmodifiable (which we enforce in {@link #put}).
     */
    public record Entry(List<ProviderQuote> quotes, Instant storedAt) {
        public Entry {
            // Compact constructor: defensive copy into an unmodifiable list.
            // Callers can't mutate the cache contents after the fact.
            quotes = List.copyOf(quotes);
        }
    }

    /** The LRU map itself — wrapped for thread-safety. */
    private final Map<QuoteCacheKey, Entry> lru;

    /** Counters. ConcurrentHashMap + LongAdder → lock-free updates. */
    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final LongAdder evictions = new LongAdder();

    public QuoteCache(
            @Value("${shipsmart.quote-cache.max-entries:256}") int maxEntries,
            @Value("${shipsmart.quote-cache.ttl-seconds:120}") long ttlSeconds) {
        this(maxEntries, ttlSeconds, Clock.systemUTC());
    }

    /** Test-visible constructor. */
    QuoteCache(int maxEntries, long ttlSeconds, Clock clock) {
        this.maxEntries = maxEntries;
        this.ttlMillis = ttlSeconds * 1000L;
        this.clock = clock;
        this.lru = Collections.synchronizedMap(new LruMap());
        // Pre-register counters so toString/snapshot output is stable even
        // before the first hit — avoids "missing key" surprises in tests.
        counters.put("hits", new LongAdder());
        counters.put("misses", new LongAdder());
    }

    /**
     * Look up cached quotes. Returns {@code null} if absent or stale.
     * <p>Reads update access order on the LRU map, so this is a mutating
     * operation under the hood — the {@code synchronizedMap} wrapper is
     * necessary for correctness.
     */
    public List<ProviderQuote> get(QuoteCacheKey key) {
        Entry e;
        synchronized (lru) {
            e = lru.get(key);
        }
        if (e == null) {
            counters.get("misses").increment();
            return null;
        }
        if (isStale(e)) {
            synchronized (lru) { lru.remove(key); }
            counters.get("misses").increment();
            return null;
        }
        counters.get("hits").increment();
        return e.quotes(); // already unmodifiable
    }

    /** Insert or replace. The incoming list is defensively copied. */
    public void put(QuoteCacheKey key, List<ProviderQuote> quotes) {
        if (quotes == null || quotes.isEmpty()) return; // don't cache empties
        Entry entry = new Entry(quotes, clock.instant());
        synchronized (lru) {
            lru.put(key, entry);
        }
        log.debug("Cached {} quotes for {}", quotes.size(), key);
    }

    public int size() {
        synchronized (lru) { return lru.size(); }
    }

    public long hits()      { return counters.get("hits").sum(); }
    public long misses()    { return counters.get("misses").sum(); }
    public long evictions() { return evictions.sum(); }

    /**
     * A sorted, read-only view of cached keys. {@link TreeMap} sorts them
     * by {@link QuoteCacheKey}'s natural ordering; the outer
     * {@code unmodifiableNavigableMap} prevents callers from mutating the
     * live cache. Good for a future "GET /admin/cache" endpoint.
     */
    public NavigableMap<QuoteCacheKey, Instant> keysSortedByRoute() {
        NavigableMap<QuoteCacheKey, Instant> sorted = new TreeMap<>();
        synchronized (lru) {
            // Snapshot first, then sort — avoids holding the lock during the
            // (potentially slow) TreeMap build.
            for (Map.Entry<QuoteCacheKey, Entry> e : lru.entrySet()) {
                sorted.put(e.getKey(), e.getValue().storedAt());
            }
        }
        return Collections.unmodifiableNavigableMap(sorted);
    }

    /** Test/admin hook: drop everything. */
    public void clear() {
        synchronized (lru) { lru.clear(); }
    }

    private boolean isStale(Entry e) {
        return clock.instant().toEpochMilli() - e.storedAt().toEpochMilli() > ttlMillis;
    }
}
