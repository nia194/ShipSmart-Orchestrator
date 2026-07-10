package com.shipsmart.api.dto;

import java.util.List;

/**
 * Quote trust metadata (Product Roadmap §12 — the trust layer).
 *
 * <p>A real shipping search engine renders more than carrier/service/price/days: it shows where a
 * number came from and whether it is still good. This value object carries that metadata so the UI
 * can badge a quote (live vs estimated vs mock/sandbox vs cached), show freshness/expiry, surface a
 * degraded provider instead of a silent whole-search failure, and list carrier restrictions.
 *
 * <p>Additive: {@link ShippingServiceDto} gains a single {@code trust} component plus a
 * backward-compatible constructor, so existing call sites keep compiling and default to {@link
 * #unknown()} until a provider populates real values.
 */
public record QuoteTrust(
        String source, // "live" | "estimated" | "mock" | "cached" | "unknown"
        String lastUpdated, // ISO-8601 timestamp, or null
        String expiresAt, // ISO-8601 timestamp, or null
        String providerStatus, // "ok" | "timeout" | "unavailable" | "unknown"
        List<String> restrictions) {

    public QuoteTrust {
        restrictions = restrictions == null ? List.of() : List.copyOf(restrictions);
    }

    /** The default until a provider records real provenance — never silently "live". */
    public static QuoteTrust unknown() {
        return new QuoteTrust("unknown", null, null, "unknown", List.of());
    }

    /** A live-provider quote with an ok status. */
    public static QuoteTrust live(String lastUpdated, String expiresAt) {
        return new QuoteTrust("live", lastUpdated, expiresAt, "ok", List.of());
    }

    /** A sandbox/mock quote (e.g. FedEx sandbox) — must be badged, never presented as live. */
    public static QuoteTrust mock() {
        return new QuoteTrust("mock", null, null, "ok", List.of());
    }
}
