package com.shipsmart.api.service;

import com.shipsmart.api.dto.*;
import com.shipsmart.api.repository.ShipmentRequestRepository;
import com.shipsmart.api.service.provider.FedExProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

    @Mock
    private ShipmentRequestRepository shipmentRequestRepository;

    @Mock
    private FedExProvider fedExProvider;

    private QuoteService quoteService;

    @BeforeEach
    void setUp() {
        // By default, mock FedEx to return empty list (uses mock fallback)
        when(fedExProvider.getQuotes(any())).thenReturn(List.of());
        quoteService = new QuoteService(shipmentRequestRepository, fedExProvider);
    }

    @Test
    void generateQuotes_returnsCorrectStructure() {
        QuoteRequest request = new QuoteRequest(
                "New York, NY",
                "Los Angeles, CA",
                "2026-04-15",
                "2026-04-20",
                List.of(new PackageItemDto("luggage", "1", "25", "24", "15", "10", "standard"))
        );

        QuoteResponse response = quoteService.generateQuotes(request, null);

        assertNotNull(response);
        assertNotNull(response.prime());
        assertNotNull(response.privateSection());
        assertEquals(3, response.prime().top().size(), "Prime should have 3 top picks");
        assertEquals(2, response.prime().more().size(), "Prime should have 2 more options");
        assertEquals(2, response.privateSection().top().size(), "Private should have 2 top picks");
        assertEquals(1, response.privateSection().more().size(), "Private should have 1 more option");
    }

    @Test
    void generateQuotes_priceMultiplierClampedToMinimum() {
        // Very light package: totalWeight = 5 lbs, pm = 5/30 = 0.167 -> clamped to 0.8
        QuoteRequest request = new QuoteRequest(
                "Chicago, IL", "Houston, TX",
                "2026-04-15", "2026-04-20",
                List.of(new PackageItemDto("envelope", "1", "5", "12", "9", "1", "standard"))
        );

        QuoteResponse response = quoteService.generateQuotes(request, null);

        // UPS Ground base price: 58.90 * 0.8 = 47.12
        ShippingServiceDto ups = response.prime().top().get(0);
        assertEquals("ups-ground", ups.id());
        assertEquals(47.12, ups.price(), 0.01);
    }

    @Test
    void generateQuotes_priceMultiplierClampedToMaximum() {
        // Heavy package: totalWeight = 100 lbs, pm = 100/30 = 3.33 -> clamped to 2.0
        QuoteRequest request = new QuoteRequest(
                "Miami, FL", "Seattle, WA",
                "2026-04-15", "2026-04-20",
                List.of(new PackageItemDto("boxes", "1", "100", "30", "20", "20", "heavy"))
        );

        QuoteResponse response = quoteService.generateQuotes(request, null);

        // UPS Ground base price: 58.90 * 2.0 = 117.80
        ShippingServiceDto ups = response.prime().top().get(0);
        assertEquals("ups-ground", ups.id());
        assertEquals(117.80, ups.price(), 0.01);
    }

    @Test
    void generateQuotes_correctCarrierIds() {
        QuoteRequest request = new QuoteRequest(
                "Austin, TX", "Denver, CO",
                "2026-05-01", "2026-05-10",
                List.of(new PackageItemDto("luggage", "1", "30", "24", "15", "10", "standard"))
        );

        QuoteResponse response = quoteService.generateQuotes(request, null);

        assertEquals("ups-ground", response.prime().top().get(0).id());
        assertEquals("fedex-express", response.prime().top().get(1).id());
        assertEquals("dhl-express", response.prime().top().get(2).id());
        assertEquals("fedex-ground", response.prime().more().get(0).id());
        assertEquals("fedex-economy", response.prime().more().get(1).id());
        assertEquals("ll-std", response.privateSection().top().get(0).id());
        assertEquals("lts-std", response.privateSection().top().get(1).id());
        assertEquals("lts-econ", response.privateSection().more().get(0).id());
    }

    @Test
    void generateQuotes_dateFormattingMatchesLegacy() {
        // 2026-04-15 is a Wednesday. +7 days = Apr 22 (Wed)
        QuoteRequest request = new QuoteRequest(
                "Boston, MA", "Portland, OR",
                "2026-04-15", "2026-04-22",
                List.of(new PackageItemDto("luggage", "1", "30", "24", "15", "10", "standard"))
        );

        QuoteResponse response = quoteService.generateQuotes(request, null);

        ShippingServiceDto ups = response.prime().top().get(0);
        assertEquals("Wed, Apr 22", ups.date()); // baseDate + 7 days
        assertEquals(7, ups.transitDays());
    }

    @Test
    void generateQuotes_promoFieldsPresent() {
        QuoteRequest request = new QuoteRequest(
                "NYC, NY", "LA, CA",
                "2026-04-15", "2026-04-20",
                List.of(new PackageItemDto("luggage", "1", "30", "24", "15", "10", "standard"))
        );

        QuoteResponse response = quoteService.generateQuotes(request, null);

        // FedEx Express Saver should have SPRING26 promo
        ShippingServiceDto fedex = response.prime().top().get(1);
        assertNotNull(fedex.promo());
        assertEquals("SPRING26", fedex.promo().code());
        assertEquals("15%", fedex.promo().pct());
        assertEquals("Spring Sale", fedex.promo().label());

        // UPS Ground should have no promo
        assertNull(response.prime().top().get(0).promo());
    }

    @Test
    void generateQuotes_multiplePackagesAggregateWeight() {
        // 2 packages: 20 lbs x 2qty + 10 lbs x 1qty = 50 lbs, pm = 50/30 = 1.667
        QuoteRequest request = new QuoteRequest(
                "A, AA", "B, BB",
                "2026-04-15", "2026-04-20",
                List.of(
                        new PackageItemDto("luggage", "2", "20", "24", "15", "10", "standard"),
                        new PackageItemDto("boxes", "1", "10", "20", "15", "12", "standard")
                )
        );

        QuoteResponse response = quoteService.generateQuotes(request, null);

        // pm = 50/30 ≈ 1.6667; UPS Ground: 58.90 * 1.6667 = 98.17
        ShippingServiceDto ups = response.prime().top().get(0);
        assertEquals(98.17, ups.price(), 0.01);
    }

    @Test
    void generateQuotes_integratesRealFedExQuotes() {
        // Mock FedEx provider to return real quotes
        ShippingServiceDto realFedExGround = new ShippingServiceDto(
                "fedex-ground",
                "FedEx",
                "FedEx Ground",
                "STANDARD",
                45.50,
                null,
                5,
                "Wed, Apr 20",
                null,
                false,
                null,
                null,
                null,
                Map.of(),
                List.of("Tracking")
        );

        ShippingServiceDto realFedExExpress = new ShippingServiceDto(
                "fedex-express",
                "FedEx",
                "FedEx Express Saver",
                "EXPRESS",
                95.00,
                null,
                3,
                "Mon, Apr 18",
                null,
                false,
                null,
                null,
                null,
                Map.of(),
                List.of("Tracking")
        );

        when(fedExProvider.getQuotes(any())).thenReturn(
                List.of(realFedExGround, realFedExExpress)
        );

        QuoteRequest request = new QuoteRequest(
                "NYC, NY", "LA, CA",
                "2026-04-15", "2026-04-20",
                List.of(new PackageItemDto("luggage", "1", "25", "24", "15", "10", "standard"))
        );

        QuoteResponse response = quoteService.generateQuotes(request, null);

        // Verify structure
        assertNotNull(response.prime());
        assertNotNull(response.prime().top());
        assertFalse(response.prime().top().isEmpty());

        // UPS should be first
        assertEquals("ups-ground", response.prime().top().get(0).id());

        // Real FedEx should be second (the cheaper one)
        assertEquals("fedex-ground", response.prime().top().get(1).id());
        assertEquals(45.50, response.prime().top().get(1).price(), 0.01);

        // DHL should be third
        assertEquals("dhl-express", response.prime().top().get(2).id());

        // Remaining FedEx quote should be in "more"
        assertTrue(response.prime().more().stream()
                .anyMatch(s -> s.id().equals("fedex-express")));
    }

    @Test
    void generateQuotes_fallsBackToMockWhenFedExUnavailable() {
        // FedEx provider returns empty (simulating API failure)
        when(fedExProvider.getQuotes(any())).thenReturn(List.of());

        QuoteRequest request = new QuoteRequest(
                "NYC, NY", "LA, CA",
                "2026-04-15", "2026-04-20",
                List.of(new PackageItemDto("luggage", "1", "25", "24", "15", "10", "standard"))
        );

        QuoteResponse response = quoteService.generateQuotes(request, null);

        // Should still have quotes (using mock fallback)
        assertEquals(3, response.prime().top().size(), "Prime top should have 3 quotes (UPS, FedEx mock, DHL)");
        assertEquals(2, response.prime().more().size(), "Prime more should have 2 mock FedEx quotes");

        // Verify structure matches legacy behavior
        assertEquals("ups-ground", response.prime().top().get(0).id());
        assertEquals("fedex-express", response.prime().top().get(1).id());  // Mock FedEx
        assertEquals("dhl-express", response.prime().top().get(2).id());
    }
}
