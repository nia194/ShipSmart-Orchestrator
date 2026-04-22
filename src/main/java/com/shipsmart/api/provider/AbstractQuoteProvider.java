package com.shipsmart.api.provider;

import com.shipsmart.api.provider.metrics.ProviderCallEvent;
import com.shipsmart.api.provider.metrics.ProviderCallOutcome;
import com.shipsmart.api.provider.metrics.ProviderMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

/**
 * Template Method skeleton for quote providers.
 *
 * <p>Why this is a useful OOP study vehicle:
 * <ul>
 *   <li><b>Abstract class</b> (vs interface-only) — lets us hoist
 *       cross-cutting behavior (logging, metrics, disabled short-circuit)
 *       out of each subclass. Interfaces can't hold mutable shared state
 *       or final method templates with fallback behavior in the same way.</li>
 *   <li><b>{@code final} template method</b> — {@link #quote} is marked
 *       {@code final} so subclasses can't accidentally bypass the
 *       metrics/try-catch envelope when overriding.</li>
 *   <li><b>Protected hook</b> — {@link #callCarrier(ProviderQuoteRequest)}
 *       is the single extension point subclasses must implement.</li>
 *   <li><b>{@code @Autowired} setter</b> — optional dependency; lets
 *       metrics be null in narrow unit tests that instantiate a subclass
 *       directly without Spring. Constructor injection would force every
 *       subclass to propagate the dependency.</li>
 * </ul>
 */
public abstract class AbstractQuoteProvider implements QuoteProvider {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private ProviderMetrics metrics; // optional; wired by Spring when present

    @Autowired(required = false)
    public final void setMetrics(ProviderMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public final List<ProviderQuote> quote(ProviderQuoteRequest request) {
        if (!isEnabled()) {
            log.debug("Provider {} disabled (missing credentials); returning empty quotes", carrierCode());
            recordEvent(ProviderCallOutcome.DISABLED, 0, 0);
            return List.of();
        }
        long start = System.nanoTime();
        try {
            List<ProviderQuote> out = callCarrier(request);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            recordEvent(ProviderCallOutcome.SUCCESS, elapsedMs, out == null ? 0 : out.size());
            return out == null ? List.of() : out;
        } catch (Exception e) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            log.warn("Provider {} failed: {}", carrierCode(), e.getMessage());
            recordEvent(classify(e), elapsedMs, 0);
            return List.of();
        }
    }

    /** Subclasses: do the real carrier HTTP call here. */
    protected abstract List<ProviderQuote> callCarrier(ProviderQuoteRequest request);

    private void recordEvent(ProviderCallOutcome outcome, long latencyMs, int count) {
        if (metrics == null) return;
        metrics.record(new ProviderCallEvent(
                carrierCode(), outcome, latencyMs, count, Instant.now()));
    }

    private static ProviderCallOutcome classify(Throwable t) {
        // Walk the cause chain looking for a TimeoutException.
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur instanceof java.util.concurrent.TimeoutException) {
                return ProviderCallOutcome.TIMEOUT;
            }
        }
        return ProviderCallOutcome.ERROR;
    }
}
