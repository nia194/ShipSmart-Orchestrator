package com.shipsmart.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Trust-layer tests (Product Roadmap §12): additive trust + backward-compatible construction. */
class QuoteTrustTest {

    private static ShippingServiceDto legacyQuote() {
        // The original 15-arg call site — must still compile and default trust to unknown.
        return new ShippingServiceDto(
                "id-1",
                "FedEx",
                "Ground",
                "economy",
                42.50,
                null,
                3,
                "2026-07-15",
                "5pm",
                false,
                null,
                null,
                null,
                Map.of(),
                List.of());
    }

    @Test
    void legacyConstructorDefaultsTrustToUnknown() {
        ShippingServiceDto q = legacyQuote();
        assertThat(q.trust()).isNotNull();
        assertThat(q.trust().source()).isEqualTo("unknown");
        assertThat(q.trust().providerStatus()).isEqualTo("unknown");
        assertThat(q.trust().restrictions()).isEmpty();
    }

    @Test
    void canonicalConstructorCarriesTrust() {
        QuoteTrust trust =
                new QuoteTrust(
                        "live",
                        "2026-07-10T00:00:00Z",
                        "2026-07-10T01:00:00Z",
                        "ok",
                        List.of("no lithium"));
        ShippingServiceDto q =
                new ShippingServiceDto(
                        "id-2",
                        "UPS",
                        "2nd Day",
                        "express",
                        88.0,
                        null,
                        2,
                        "2026-07-14",
                        "noon",
                        true,
                        null,
                        null,
                        null,
                        Map.of(),
                        List.of(),
                        trust);
        assertThat(q.trust().source()).isEqualTo("live");
        assertThat(q.trust().restrictions()).containsExactly("no lithium");
    }

    @Test
    void factoriesProduceExpectedProvenance() {
        assertThat(QuoteTrust.mock().source()).isEqualTo("mock");
        assertThat(QuoteTrust.mock().providerStatus()).isEqualTo("ok");
        assertThat(QuoteTrust.live("t", "e").source()).isEqualTo("live");
        assertThat(QuoteTrust.unknown().source()).isEqualTo("unknown");
    }

    @Test
    void restrictionsAreDefensivelyCopiedAndNullSafe() {
        QuoteTrust t = new QuoteTrust("live", null, null, "ok", null);
        assertThat(t.restrictions()).isEmpty();
    }
}
