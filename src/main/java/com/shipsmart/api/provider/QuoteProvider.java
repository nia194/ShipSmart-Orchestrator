package com.shipsmart.api.provider;

import java.util.List;

/**
 * Strategy contract for carrier quote providers. Implementations live alongside
 * the carrier adapter (see {@link com.shipsmart.api.service.provider}). The
 * registry scans all beans of this type at startup; each declares whether it
 * {@link #isEnabled() has credentials} so misconfigured providers fail fast
 * instead of silently returning empty quotes at request time.
 *
 * <p>Interface design notes (study aid):
 * <ul>
 *   <li><b>Default methods</b> let us add {@link #priority()} without
 *       breaking existing implementations (backwards-compatible API
 *       evolution — a core reason Java 8 added them).</li>
 *   <li>Implementations that care about order simply override the default;
 *       everything else keeps working at {@link #DEFAULT_PRIORITY}.</li>
 * </ul>
 */
public interface QuoteProvider {

    /** Neutral priority. Lower values = higher priority (fanout iterates earlier). */
    int DEFAULT_PRIORITY = 100;

    String carrierCode();
    boolean isEnabled();

    /** Returns normalized internal ProviderQuote objects; never throws for single-provider failure. */
    List<ProviderQuote> quote(ProviderQuoteRequest request);

    /**
     * Fanout priority — lower means "try first." Providers with real-time
     * APIs and tight SLAs (e.g. FedEx live) override to a lower number;
     * slower or experimental providers leave this at the default.
     *
     * <p>This is a {@code default} method so adding it doesn't break any
     * existing {@code QuoteProvider} implementation.
     */
    default int priority() {
        return DEFAULT_PRIORITY;
    }
}
