package com.shipsmart.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shipsmart.api.cache.QuoteCache;
import com.shipsmart.api.cache.QuoteCacheKey;
import com.shipsmart.api.provider.ProviderQuote;
import com.shipsmart.api.provider.ProviderQuoteRequest;
import com.shipsmart.api.provider.QuoteProvider;
import com.shipsmart.api.provider.QuoteProviderRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link QuoteFanoutService}: the cache short-circuit, the parallel provider merge +
 * cache-fill on a miss, the no-providers degenerate case, and canonical sorting. Providers +
 * registry are mocked; the executor and {@link QuoteCache} are real (the cache is deterministic
 * with a system clock because we never cross its TTL here).
 */
@ExtendWith(MockitoExtension.class)
class QuoteFanoutServiceTest {

    @Mock QuoteProviderRegistry registry;
    @Mock QuoteProvider ups;
    @Mock QuoteProvider fedex;

    private ExecutorService executor;
    private QuoteCache cache;
    private QuoteFanoutService service;

    private static final ProviderQuoteRequest REQ =
            new ProviderQuoteRequest(
                    "10001", "90210", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7), 10.0, 1);

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        cache = new QuoteCache(64, 120L); // public ctor: systemUTC clock, never crosses TTL here
        service = new QuoteFanoutService(registry, executor, cache);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private static ProviderQuote quote(String carrier, String price, int days) {
        return new ProviderQuote(
                carrier, carrier + " Service", "standard", new BigDecimal(price), days, false);
    }

    @Test
    void cache_hit_skips_provider_fanout() {
        cache.put(QuoteCacheKey.from(REQ), List.of(quote("UPS", "10.00", 5)));

        List<ProviderQuote> result = service.fanout(REQ);

        assertThat(result).hasSize(1);
        verify(registry, never()).enabled(); // never dispatched to carriers
    }

    @Test
    void cache_miss_merges_all_providers_and_fills_cache() {
        when(registry.enabled()).thenReturn(List.of(ups, fedex));
        when(ups.quote(any())).thenReturn(List.of(quote("UPS", "10.00", 5)));
        when(fedex.quote(any())).thenReturn(List.of(quote("FedEx", "25.00", 2)));

        List<ProviderQuote> result = service.fanout(REQ);

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(ProviderQuote::carrier)
                .containsExactlyInAnyOrder("UPS", "FedEx");
        // The merged result is now cached: a second call must not re-dispatch.
        service.fanout(REQ);
        verify(registry).enabled(); // exactly once across both calls
    }

    @Test
    void no_enabled_providers_returns_empty() {
        when(registry.enabled()).thenReturn(List.of());
        assertThat(service.fanout(REQ)).isEmpty();
    }

    @Test
    void fanoutSorted_orders_by_price_ascending_by_default() {
        when(registry.enabled()).thenReturn(List.of(ups, fedex));
        when(ups.quote(any())).thenReturn(List.of(quote("UPS", "25.00", 5)));
        when(fedex.quote(any())).thenReturn(List.of(quote("FedEx", "10.00", 2)));

        List<ProviderQuote> sorted = service.fanoutSorted(REQ, null);

        assertThat(sorted)
                .extracting(ProviderQuote::price)
                .containsExactly(new BigDecimal("10.00"), new BigDecimal("25.00"));
    }
}
