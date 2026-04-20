package com.shipsmart.api.service;

import com.shipsmart.api.provider.ProviderQuote;
import com.shipsmart.api.provider.ProviderQuoteRequest;
import com.shipsmart.api.provider.QuoteProvider;
import com.shipsmart.api.provider.QuoteProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Bounded parallel fanout across {@link QuoteProvider}s.
 * - Uses the dedicated {@code quoteProviderExecutor} (bounded pool, observable metrics).
 * - Per-call {@code orTimeout} prevents one slow carrier from stalling the request.
 * - Failures downgrade to an empty list — never fail the whole request for one provider.
 * - Persistence happens on the request thread, after the join, inside {@code @Transactional}.
 */
@Service
public class QuoteFanoutService {

    private static final Logger log = LoggerFactory.getLogger(QuoteFanoutService.class);

    private final QuoteProviderRegistry registry;
    private final ExecutorService quoteProviderExecutor;

    public QuoteFanoutService(QuoteProviderRegistry registry, ExecutorService quoteProviderExecutor) {
        this.registry = registry;
        this.quoteProviderExecutor = quoteProviderExecutor;
    }

    public List<ProviderQuote> fanout(ProviderQuoteRequest request) {
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
        return futures.stream().flatMap(f -> f.join().stream()).toList();
    }
}
