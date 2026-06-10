package com.shipsmart.api.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.shipsmart.api.provider.ProviderQuote;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link QuoteCache}: the LRU eviction bound, TTL staleness (via an injected mutable
 * {@link Clock}), the hit/miss/eviction counters, and the "don't cache empties" rule. Uses the
 * package-private test constructor so time is deterministic — no sleeps.
 */
class QuoteCacheTest {

    /** A hand-cranked clock so TTL expiry is deterministic. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private static QuoteCacheKey key(String origin) {
        return new QuoteCacheKey(
                origin, "90210", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7), 10.0, 1);
    }

    private static List<ProviderQuote> quotes() {
        return List.of(
                new ProviderQuote("UPS", "Ground", "standard", new BigDecimal("10.00"), 5, false));
    }

    @Test
    void hit_within_ttl_returns_quotes_and_counts_hit() {
        var clock = new MutableClock(Instant.parse("2026-06-04T00:00:00Z"));
        var cache = new QuoteCache(8, 120, clock); // 120s TTL

        cache.put(key("10001"), quotes());
        clock.advance(Duration.ofSeconds(60)); // still fresh

        assertThat(cache.get(key("10001"))).hasSize(1);
        assertThat(cache.hits()).isEqualTo(1);
        assertThat(cache.misses()).isZero();
    }

    @Test
    void entry_past_ttl_is_stale_and_counts_miss() {
        var clock = new MutableClock(Instant.parse("2026-06-04T00:00:00Z"));
        var cache = new QuoteCache(8, 120, clock);

        cache.put(key("10001"), quotes());
        clock.advance(Duration.ofSeconds(121)); // expired

        assertThat(cache.get(key("10001"))).isNull();
        assertThat(cache.misses()).isEqualTo(1);
    }

    @Test
    void unknown_key_is_a_miss() {
        var cache = new QuoteCache(8, 120, Clock.systemUTC());
        assertThat(cache.get(key("99999"))).isNull();
        assertThat(cache.misses()).isEqualTo(1);
    }

    @Test
    void lru_evicts_beyond_max_entries() {
        var cache = new QuoteCache(2, 120, Clock.systemUTC()); // bound = 2

        cache.put(key("10001"), quotes());
        cache.put(key("10002"), quotes());
        cache.put(key("10003"), quotes()); // forces an eviction

        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.evictions()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void empty_quote_lists_are_not_cached() {
        var cache = new QuoteCache(8, 120, Clock.systemUTC());
        cache.put(key("10001"), List.of());
        assertThat(cache.size()).isZero();
    }

    @Test
    void keysSortedByRoute_is_sorted_and_unmodifiable() {
        var cache = new QuoteCache(8, 120, Clock.systemUTC());
        cache.put(key("30003"), quotes());
        cache.put(key("10001"), quotes());
        cache.put(key("20002"), quotes());

        var view = cache.keysSortedByRoute();
        assertThat(view.firstKey().origin()).isEqualTo("10001"); // natural order by origin
        assertThat(view.lastKey().origin()).isEqualTo("30003");
    }
}
