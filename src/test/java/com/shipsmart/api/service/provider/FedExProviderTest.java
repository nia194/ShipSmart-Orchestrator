package com.shipsmart.api.service.provider;

import com.shipsmart.api.dto.PackageItemDto;
import com.shipsmart.api.dto.ShippingServiceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FedExProviderTest {

    @Mock
    private RestTemplate restTemplate;

    private FedExProvider provider;

    @BeforeEach
    void setUp() {
        // Initialize with dummy credentials for testing
        provider = new FedExProvider(
                restTemplate,
                "https://apis-sandbox.fedex.com",
                "test-client-id",
                "test-client-secret",
                "test-account-123"
        );
    }

    @Test
    void getName_returnsCorrectProvider() {
        assertEquals("fedex", provider.getName());
    }

    @Test
    void getQuotes_returnsEmptyListWhenNotConfigured() {
        // Initialize with empty credentials
        FedExProvider unconfigured = new FedExProvider(
                restTemplate,
                "https://apis-sandbox.fedex.com",
                "",
                "",
                ""
        );

        ShipmentForQuote shipment = new ShipmentForQuote(
                "NYC, NY 10001",
                "LA, CA 90001",
                "2026-04-15",
                "2026-04-20",
                List.of(new PackageItemDto("luggage", "1", "25", "24", "15", "10", "standard")),
                25.0,
                1
        );

        List<ShippingServiceDto> quotes = unconfigured.getQuotes(shipment);
        assertTrue(quotes.isEmpty());
    }

    @Test
    void getQuotes_returnsEmptyListOnNetworkError() {
        when(restTemplate.postForObject(anyString(), any(), any()))
                .thenThrow(new RestClientException("Network error"));

        ShipmentForQuote shipment = new ShipmentForQuote(
                "NYC, NY 10001",
                "LA, CA 90001",
                "2026-04-15",
                "2026-04-20",
                List.of(new PackageItemDto("luggage", "1", "25", "24", "15", "10", "standard")),
                25.0,
                1
        );

        List<ShippingServiceDto> quotes = provider.getQuotes(shipment);
        assertTrue(quotes.isEmpty());
    }

    @Test
    void getQuotes_parsesValidFedExResponse() {
        // Mock OAuth token response
        Map<String, Object> tokenResponse = Map.of(
                "access_token", "test-token-12345",
                "expires_in", 3600
        );

        // Mock FedEx rate response
        @SuppressWarnings("unchecked")
        Map<String, Object> rateResponse = Map.of(
                "output", Map.of(
                        "rateReplyDetails", List.of(
                                Map.of(
                                        "serviceType", "FEDEX_GROUND",
                                        "ratedShipmentDetails", List.of(
                                                Map.of("totalNetCharge", "45.50")
                                        ),
                                        "commit", Map.of(
                                                "transitDays", Map.of("description", "5")
                                        )
                                ),
                                Map.of(
                                        "serviceType", "FEDEX_EXPRESS_SAVER",
                                        "ratedShipmentDetails", List.of(
                                                Map.of("totalNetCharge", "95.00")
                                        ),
                                        "commit", Map.of(
                                                "transitDays", Map.of("description", "3")
                                        )
                                )
                        )
                )
        );

        when(restTemplate.postForObject(
                contains("/oauth/token"),
                any(),
                eq(Map.class)
        )).thenReturn(tokenResponse);

        when(restTemplate.postForObject(
                contains("/rate/v1/rates/quotes"),
                any(),
                eq(Map.class)
        )).thenReturn(rateResponse);

        ShipmentForQuote shipment = new ShipmentForQuote(
                "NYC, NY 10001",
                "LA, CA 90001",
                "2026-04-15",
                "2026-04-20",
                List.of(new PackageItemDto("luggage", "1", "25", "24", "15", "10", "standard")),
                25.0,
                1
        );

        List<ShippingServiceDto> quotes = provider.getQuotes(shipment);

        assertFalse(quotes.isEmpty());
        assertEquals(2, quotes.size());

        // Quotes should be sorted by price (ascending)
        ShippingServiceDto first = quotes.get(0);
        ShippingServiceDto second = quotes.get(1);

        assertEquals(45.50, first.price(), 0.01);
        assertEquals(95.00, second.price(), 0.01);
        assertEquals("FedEx Ground", first.name());
        assertEquals("FedEx Express Saver", second.name());
    }

    @Test
    void getQuotes_returnsEmptyListForEmptyRateResponse() {
        Map<String, Object> tokenResponse = Map.of(
                "access_token", "test-token",
                "expires_in", 3600
        );

        Map<String, Object> emptyResponse = Map.of(
                "output", Map.of("rateReplyDetails", List.of())
        );

        when(restTemplate.postForObject(
                contains("/oauth/token"),
                any(),
                eq(Map.class)
        )).thenReturn(tokenResponse);

        when(restTemplate.postForObject(
                contains("/rate/v1/rates/quotes"),
                any(),
                eq(Map.class)
        )).thenReturn(emptyResponse);

        ShipmentForQuote shipment = new ShipmentForQuote(
                "NYC, NY 10001",
                "LA, CA 90001",
                "2026-04-15",
                "2026-04-20",
                List.of(new PackageItemDto("luggage", "1", "25", "24", "15", "10", "standard")),
                25.0,
                1
        );

        List<ShippingServiceDto> quotes = provider.getQuotes(shipment);
        assertTrue(quotes.isEmpty());
    }

    @Test
    void getQuotes_handlesNullResponse() {
        Map<String, Object> tokenResponse = Map.of(
                "access_token", "test-token",
                "expires_in", 3600
        );

        when(restTemplate.postForObject(
                contains("/oauth/token"),
                any(),
                eq(Map.class)
        )).thenReturn(tokenResponse);

        when(restTemplate.postForObject(
                contains("/rate/v1/rates/quotes"),
                any(),
                eq(Map.class)
        )).thenReturn(null);

        ShipmentForQuote shipment = new ShipmentForQuote(
                "NYC, NY 10001",
                "LA, CA 90001",
                "2026-04-15",
                "2026-04-20",
                List.of(new PackageItemDto("luggage", "1", "25", "24", "15", "10", "standard")),
                25.0,
                1
        );

        List<ShippingServiceDto> quotes = provider.getQuotes(shipment);
        assertTrue(quotes.isEmpty());
    }
}
