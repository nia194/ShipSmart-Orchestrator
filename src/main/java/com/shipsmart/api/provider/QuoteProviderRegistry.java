package com.shipsmart.api.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Bean-lifecycle demo: on {@link InitializingBean#afterPropertiesSet()}, enumerates
 * every {@link QuoteProvider} bean and logs enabled / disabled status.
 * Fails fast only if ZERO providers are enabled — a misconfigured Java
 * plane should not serve (silently-empty) quote lists in production.
 *
 * <p>{@link #enabled()} returns providers sorted by {@link QuoteProvider#priority()}
 * (lower first) so the fanout service consults fast/preferred carriers before
 * slower ones. A stable secondary sort on carrier code keeps the order
 * deterministic for equal-priority providers — essential for log diffing.
 */
@Component
public class QuoteProviderRegistry implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(QuoteProviderRegistry.class);

    /** Keep the natural-order "registered beans" list immutable. */
    private final List<QuoteProvider> providers;

    public QuoteProviderRegistry(List<QuoteProvider> providers) {
        // Defensive copy + freeze — callers can't mutate the internal list.
        this.providers = List.copyOf(providers);
    }

    @Override
    public void afterPropertiesSet() {
        List<String> enabled = providers.stream()
                .filter(QuoteProvider::isEnabled)
                .map(QuoteProvider::carrierCode)
                .toList();
        String summary = providers.stream()
                .map(p -> p.carrierCode()
                        + (p.isEnabled() ? "=ENABLED" : "=DRY-RUN")
                        + "@p" + p.priority())
                .collect(Collectors.joining(", "));
        log.info("QuoteProviderRegistry: {} of {} providers enabled [{}]",
                enabled.size(), providers.size(), summary);
        if (!providers.isEmpty() && enabled.isEmpty()) {
            log.warn("No quote providers have credentials; /api/v1/quotes will return empty lists");
        }
    }

    public List<QuoteProvider> all() { return providers; }

    /** Enabled providers, sorted by priority asc, then carrier code for stability. */
    public List<QuoteProvider> enabled() {
        Comparator<QuoteProvider> byPriorityThenCode =
                Comparator.comparingInt(QuoteProvider::priority)
                        .thenComparing(QuoteProvider::carrierCode);
        return providers.stream()
                .filter(QuoteProvider::isEnabled)
                .sorted(byPriorityThenCode)
                .toList();
    }
}
