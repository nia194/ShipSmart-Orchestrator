package com.shipsmart.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shipsmart.api.service.AiClaimGuard.AiBookingClaim;
import com.shipsmart.api.service.AiClaimGuard.Outcome;
import com.shipsmart.api.service.AiClaimGuard.StoredQuote;
import com.shipsmart.api.service.AiClaimGuard.TrustDecision;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AiClaimGuard} — the §5.6 AI trust boundary. Verifies each refusal reason
 * and that an accepted booking is always re-priced from the STORED quote, never the AI's claim.
 */
class AiClaimGuardTest {

    private static final Instant NOW = Instant.parse("2026-07-08T12:00:00Z");
    private final AiClaimGuard guard = new AiClaimGuard(Clock.fixed(NOW, ZoneOffset.UTC));

    private static StoredQuote storedQuote(BigDecimal total, boolean complianceVerified) {
        return new StoredQuote("Q-100", total, "USD", complianceVerified, NOW.plusSeconds(600));
    }

    private static AiBookingClaim claim(
            String quoteId, BigDecimal total, boolean confirmed, boolean aiCompliant) {
        return new AiBookingClaim(quoteId, total, "USD", confirmed, aiCompliant);
    }

    @Test
    void acceptsAConfirmedClaimThatMatchesAliveStoredQuote() {
        TrustDecision d =
                guard.evaluate(
                        claim("Q-100", new BigDecimal("42.50"), true, false),
                        storedQuote(new BigDecimal("42.50"), false));

        assertThat(d.accepted()).isTrue();
        assertThat(d.outcome()).isEqualTo(Outcome.ACCEPT);
        assertThat(d.reason()).isEqualTo(AiClaimGuard.OK);
        assertThat(d.authoritativeTotal()).isEqualByComparingTo("42.50");
    }

    @Test
    void authoritativeTotalIsTheStoredQuoteNotTheClaim() {
        // Claim value equals stored by compareTo (42.5 == 42.50) but the returned figure must be
        // the STORED instance — proving Java, not the model, decides what is charged.
        StoredQuote stored = storedQuote(new BigDecimal("42.50"), false);
        TrustDecision d =
                guard.evaluate(claim("Q-100", new BigDecimal("42.5"), true, false), stored);

        assertThat(d.accepted()).isTrue();
        assertThat(d.authoritativeTotal()).isSameAs(stored.total());
    }

    @Test
    void refusesWhenNoStoredQuoteExists() {
        TrustDecision d =
                guard.evaluate(claim("Q-100", new BigDecimal("42.50"), true, false), null);
        assertThat(d.outcome()).isEqualTo(Outcome.REFUSE);
        assertThat(d.reason()).isEqualTo(AiClaimGuard.REFUSE_NO_QUOTE);
        assertThat(d.authoritativeTotal()).isNull();
    }

    @Test
    void refusesWhenTheClaimedQuoteIdDoesNotMatchStored() {
        TrustDecision d =
                guard.evaluate(
                        claim("Q-FORGED", new BigDecimal("42.50"), true, false),
                        storedQuote(new BigDecimal("42.50"), false));
        assertThat(d.reason()).isEqualTo(AiClaimGuard.REFUSE_NO_QUOTE);
    }

    @Test
    void refusesAnExpiredStoredQuote() {
        StoredQuote expired =
                new StoredQuote("Q-100", new BigDecimal("42.50"), "USD", true, NOW.minusSeconds(1));
        TrustDecision d =
                guard.evaluate(claim("Q-100", new BigDecimal("42.50"), true, true), expired);
        assertThat(d.reason()).isEqualTo(AiClaimGuard.REFUSE_QUOTE_EXPIRED);
    }

    @Test
    void refusesWhenTheUserHasNotConfirmed() {
        TrustDecision d =
                guard.evaluate(
                        claim("Q-100", new BigDecimal("42.50"), false, false),
                        storedQuote(new BigDecimal("42.50"), false));
        assertThat(d.reason()).isEqualTo(AiClaimGuard.REFUSE_UNCONFIRMED);
    }

    @Test
    void refusesWhenTheAiPriceDoesNotMatchTheStoredQuote() {
        TrustDecision d =
                guard.evaluate(
                        claim("Q-100", new BigDecimal("9.99"), true, false),
                        storedQuote(new BigDecimal("42.50"), false));
        assertThat(d.reason()).isEqualTo(AiClaimGuard.REFUSE_PRICE_UNTRUSTED);
    }

    @Test
    void refusesOnCurrencyMismatch() {
        StoredQuote eur =
                new StoredQuote(
                        "Q-100", new BigDecimal("42.50"), "EUR", true, NOW.plusSeconds(600));
        TrustDecision d = guard.evaluate(claim("Q-100", new BigDecimal("42.50"), true, false), eur);
        assertThat(d.reason()).isEqualTo(AiClaimGuard.REFUSE_PRICE_UNTRUSTED);
    }

    @Test
    void refusesAnAiComplianceClaimTheDeterministicCheckerHasNotVerified() {
        TrustDecision d =
                guard.evaluate(
                        claim("Q-100", new BigDecimal("42.50"), true, true),
                        storedQuote(new BigDecimal("42.50"), false));
        assertThat(d.reason()).isEqualTo(AiClaimGuard.REFUSE_COMPLIANCE_UNVERIFIED);
    }

    @Test
    void acceptsWhenComplianceIsBothClaimedAndVerified() {
        TrustDecision d =
                guard.evaluate(
                        claim("Q-100", new BigDecimal("42.50"), true, true),
                        storedQuote(new BigDecimal("42.50"), true));
        assertThat(d.accepted()).isTrue();
    }

    @Test
    void acceptsWhenTheAiMakesNoComplianceClaimEvenIfUnverified() {
        // The downgrade only fires on an AI claim; silence is not an assertion of compliance.
        TrustDecision d =
                guard.evaluate(
                        claim("Q-100", new BigDecimal("42.50"), true, false),
                        storedQuote(new BigDecimal("42.50"), false));
        assertThat(d.accepted()).isTrue();
    }
}
