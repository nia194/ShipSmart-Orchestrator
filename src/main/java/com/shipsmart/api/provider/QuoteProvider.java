package com.shipsmart.api.provider;

import java.util.List;

/**
 * Strategy contract for carrier quote providers. Implementations live alongside
 * the carrier adapter (see {@link com.shipsmart.api.service.provider}). The
 * registry scans all beans of this type at startup; each declares whether it
 * {@link #isEnabled() has credentials} so misconfigured providers fail fast
 * instead of silently returning empty quotes at request time.
 */
public interface QuoteProvider {
    String carrierCode();
    boolean isEnabled();

    /** Returns normalized internal ProviderQuote objects; never throws for single-provider failure. */
    List<ProviderQuote> quote(ProviderQuoteRequest request);
}
