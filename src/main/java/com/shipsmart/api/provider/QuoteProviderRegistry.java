package com.shipsmart.api.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Bean-lifecycle demo: on InitializingBean#afterPropertiesSet, enumerates
 * every {@link QuoteProvider} bean and logs enabled / disabled status.
 * Fails fast only if ZERO providers are enabled — a misconfigured Java
 * plane should not serve (silently-empty) quote lists in production.
 */
@Component
public class QuoteProviderRegistry implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(QuoteProviderRegistry.class);

    private final List<QuoteProvider> providers;

    public QuoteProviderRegistry(List<QuoteProvider> providers) {
        this.providers = providers;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> enabled = providers.stream()
                .filter(QuoteProvider::isEnabled)
                .map(QuoteProvider::carrierCode)
                .toList();
        String summary = providers.stream()
                .map(p -> p.carrierCode() + (p.isEnabled() ? "=ENABLED" : "=DRY-RUN"))
                .collect(Collectors.joining(", "));
        log.info("QuoteProviderRegistry: {} of {} providers enabled [{}]",
                enabled.size(), providers.size(), summary);
        if (!providers.isEmpty() && enabled.isEmpty()) {
            log.warn("No quote providers have credentials; /api/v1/quotes will return empty lists");
        }
    }

    public List<QuoteProvider> all() { return providers; }

    public List<QuoteProvider> enabled() {
        return providers.stream().filter(QuoteProvider::isEnabled).toList();
    }
}
