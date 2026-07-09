package com.shipsmart.api.service;

import java.math.BigDecimal;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The AI trust boundary (Governance &amp; Guardrails Control System §5.6).
 *
 * <p>The AI concierge (ShipSmart-API) is <em>advisory</em>. It can draft a reply, suggest a
 * service, and pre-fill a form — but it must never become the system of record for anything with
 * money, legal, or safety consequences. This Orchestrator is that system of record, and this guard
 * is where an AI-assisted booking is re-derived from trusted state before it is allowed to proceed.
 *
 * <p>Four invariants, each a distinct refusal reason:
 *
 * <ol>
 *   <li><b>No unquoted bookings.</b> A booking must reference a {@link StoredQuote} the server
 *       actually produced. The LLM cannot invent a {@code quoteId} or conjure a price out of prose.
 *   <li><b>Live quote only.</b> An expired stored quote is refused rather than honoured at a stale
 *       price.
 *   <li><b>Explicit human confirmation.</b> The AI proposing a booking is not consent; the user
 *       must have confirmed it.
 *   <li><b>Java wins on price.</b> The AI-stated total is re-validated against the stored quote. On
 *       any mismatch the booking is refused (the user must never be charged a number the model
 *       fabricated), and on acceptance the <em>authoritative</em> total returned is always the
 *       stored one — never the claim's.
 * </ol>
 *
 * <p>Plus the policy-aware refusal: an AI "this looks compliant" is advisory only. If the
 * deterministic compliance checker has not verified the shipment, an AI compliance claim is
 * downgraded to a refusal — the model can direct attention, never clear a shipment.
 *
 * <p>Pure and deterministic (time via an injected {@link Clock}); no Spring wiring, so it
 * unit-tests without a context. Wiring into the live booking controller lands with the concierge
 * integration.
 */
public final class AiClaimGuard {

    private static final Logger log = LoggerFactory.getLogger(AiClaimGuard.class);

    /** What the AI concierge proposed. None of these fields is trusted on its own. */
    public record AiBookingClaim(
            String quoteId,
            BigDecimal claimedTotal,
            String currency,
            boolean userConfirmed,
            boolean aiClaimsCompliant) {}

    /** The server's own record of a quote — the authoritative source of truth. */
    public record StoredQuote(
            String quoteId,
            BigDecimal total,
            String currency,
            boolean complianceVerified,
            java.time.Instant expiresAt) {}

    public enum Outcome {
        ACCEPT,
        REFUSE
    }

    /**
     * The guard's verdict. On {@link Outcome#ACCEPT} the {@code authoritativeTotal}/{@code
     * currency} are the <em>stored</em> quote's values, so callers physically cannot proceed on an
     * AI number.
     */
    public record TrustDecision(
            Outcome outcome, String reason, BigDecimal authoritativeTotal, String currency) {
        public boolean accepted() {
            return outcome == Outcome.ACCEPT;
        }
    }

    // Refusal reasons — stable codes the caller can log/emit and tests can assert on.
    public static final String OK = "ok";
    public static final String REFUSE_NO_QUOTE = "booking.unquoted";
    public static final String REFUSE_QUOTE_EXPIRED = "booking.quote_expired";
    public static final String REFUSE_UNCONFIRMED = "booking.unconfirmed";
    public static final String REFUSE_PRICE_UNTRUSTED = "booking.price_untrusted";
    public static final String REFUSE_COMPLIANCE_UNVERIFIED = "booking.compliance_unverified";

    private final Clock clock;

    public AiClaimGuard(Clock clock) {
        this.clock = clock;
    }

    /**
     * Re-derive an AI-assisted booking from trusted state.
     *
     * @param claim what the AI proposed (untrusted)
     * @param stored the server's quote for {@code claim.quoteId()}, or {@code null} if none exists
     * @return an {@link Outcome#ACCEPT} carrying the authoritative stored total, or a refusal
     */
    public TrustDecision evaluate(AiBookingClaim claim, StoredQuote stored) {
        // 1. The booking must reference a real, matching server-side quote.
        if (claim == null
                || claim.quoteId() == null
                || stored == null
                || !claim.quoteId().equals(stored.quoteId())) {
            return refuse(REFUSE_NO_QUOTE);
        }
        // 2. The stored quote must still be live.
        if (stored.expiresAt() != null && !stored.expiresAt().isAfter(clock.instant())) {
            return refuse(REFUSE_QUOTE_EXPIRED);
        }
        // 3. Booking requires explicit human confirmation — an AI proposal is not consent.
        if (!claim.userConfirmed()) {
            return refuse(REFUSE_UNCONFIRMED);
        }
        // 4. Java wins on price: the AI-stated total must match the stored quote exactly
        //    (same currency, same amount by value). Any drift is refused.
        if (claim.claimedTotal() == null
                || claim.currency() == null
                || !claim.currency().equals(stored.currency())
                || claim.claimedTotal().compareTo(stored.total()) != 0) {
            return refuse(REFUSE_PRICE_UNTRUSTED);
        }
        // 5. Policy-aware refusal: an AI compliance claim is advisory; the deterministic
        //    checker must have verified the shipment, else the model does not get to clear it.
        if (claim.aiClaimsCompliant() && !stored.complianceVerified()) {
            return refuse(REFUSE_COMPLIANCE_UNVERIFIED);
        }
        // Accept — but the authoritative figures are the STORED quote's, never the claim's.
        return new TrustDecision(Outcome.ACCEPT, OK, stored.total(), stored.currency());
    }

    private TrustDecision refuse(String reason) {
        log.debug("AiClaimGuard refused an AI-assisted booking: {}", reason);
        return new TrustDecision(Outcome.REFUSE, reason, null, null);
    }
}
