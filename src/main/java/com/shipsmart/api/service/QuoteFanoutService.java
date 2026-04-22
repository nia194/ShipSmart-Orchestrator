package com.shipsmart.api.service;

import com.shipsmart.api.cache.QuoteCache;
import com.shipsmart.api.cache.QuoteCacheKey;
import com.shipsmart.api.provider.ProviderQuote;
import com.shipsmart.api.provider.ProviderQuoteRequest;
import com.shipsmart.api.provider.QuoteComparators;
import com.shipsmart.api.provider.QuoteProvider;
import com.shipsmart.api.provider.QuoteProviderRegistry;
import com.shipsmart.api.provider.QuoteSortOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Bounded parallel fanout across {@link QuoteProvider}s.
 * <ul>
 *   <li>Consults the {@link QuoteCache} (LRU, TTL-bounded) before dispatching
 *       carrier calls. Cache hits skip the executor entirely.</li>
 *   <li>Uses the dedicated {@code quoteProviderExecutor} (bounded pool,
 *       observable metrics).</li>
 *   <li>Per-call {@code orTimeout} prevents one slow carrier from stalling
 *       the request.</li>
 *   <li>Failures downgrade to an empty list — never fail the whole request
 *       for one provider.</li>
 *   <li>Exposes {@link #fanoutSorted} so callers can request a canonical
 *       ordering (cheapest / fastest / recommended).</li>
 * </ul>
 */
@Service
public class QuoteFanoutService {

    private static final Logger log = LoggerFactory.getLogger(QuoteFanoutService.class);

    private final QuoteProviderRegistry registry;
    private final ExecutorService quoteProviderExecutor;
    private final QuoteCache cache;

    public QuoteFanoutService(
            QuoteProviderRegistry registry,
            ExecutorService quoteProviderExecutor,
            QuoteCache cache) {
        this.registry = registry;
        this.quoteProviderExecutor = quoteProviderExecutor;
        this.cache = cache;
    }

    public List<ProviderQuote> fanout(ProviderQuoteRequest request) {
        QuoteCacheKey key = QuoteCacheKey.from(request);
        List<ProviderQuote> cached = cache.get(key);
        if (cached != null) {
            log.debug("Fanout cache HIT for {} ({} quotes)", key, cached.size());
            return cached;
        }

        List<QuoteProvider> providers = registry.enabled();
        if (providers.isEmpty()) {
            log.debug("No enabled providers; skipping fanout");
            return List.of();
        }
        List<CompletableFuture<List<ProviderQuote>>> futures = providers.stream()
                .map(p -> CompletableFuture
                        .supplyAsync(() -> p.quote(request), quoteProviderExecutor)
                        .orTimeout(3, TimeUnit.SECONDS)
                        .exceptionally(ex -> {
                            log.warn("Provider {} timed out or failed: {}", p.carrierCode(), ex.getMessage());
                            return List.of();
                        }))
                .toList();
        List<ProviderQuote> merged = futures.stream()
                .flatMap(f -> f.join().stream())
                .toList();
        cache.put(key, merged);
        return merged;
    }

    /**
     * Fanout, then sort by the caller-chosen policy. Returns a fresh
     * mutable list — callers sometimes pipe through further filtering.
     */
    public List<ProviderQuote> fanoutSorted(ProviderQuoteRequest request, QuoteSortOption sort) {
        List<ProviderQuote> base = fanout(request);
        if (base.isEmpty()) return new ArrayList<>();
        List<ProviderQuote> copy = new ArrayList<>(base);
        Comparator<ProviderQuote> cmp =
                sort == null ? QuoteComparators.BY_PRICE_ASC : sort.comparator();
        copy.sort(cmp);
        return copy;
    }
}
