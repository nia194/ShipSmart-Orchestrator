package com.shipsmart.api.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Template Method skeleton — handles logging + disabled short-circuit;
 * subclasses implement {@link #callCarrier(ProviderQuoteRequest)}.
 */
public abstract class AbstractQuoteProvider implements QuoteProvider {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public final List<ProviderQuote> quote(ProviderQuoteRequest request) {
        if (!isEnabled()) {
            log.debug("Provider {} disabled (missing credentials); returning empty quotes", carrierCode());
            return List.of();
        }
        try {
            return callCarrier(request);
        } catch (Exception e) {
            log.warn("Provider {} failed: {}", carrierCode(), e.getMessage());
            return List.of();
        }
    }

    protected abstract List<ProviderQuote> callCarrier(ProviderQuoteRequest request);
}
