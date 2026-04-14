package com.shipsmart.api.service.provider;

import com.shipsmart.api.dto.ShippingServiceDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * FedEx shipping provider.
 * Integrates with FedEx Developer API (Rate API v1).
 * Handles OAuth2 token management and quote fetching.
 *
 * Requires environment variables:
 *   - FEDEX_CLIENT_ID, FEDEX_CLIENT_SECRET, FEDEX_ACCOUNT_NUMBER
 *   - FEDEX_BASE_URL (default: https://apis-sandbox.fedex.com)
 */
@Component
public class FedExProvider implements ShippingProvider {

    private static final Logger log = LoggerFactory.getLogger(FedExProvider.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US);
    private static final int TOKEN_REFRESH_BUFFER_SECS = 60;
    private static final int API_TIMEOUT_SECS = 20;

    private final String baseUrl;
    private final String clientId;
    private final String clientSecret;
    private final String accountNumber;
    private final RestTemplate restTemplate;

    private String accessToken = "";
    private long tokenExpiresAt = 0L;

    // FedEx service type → (human-readable name, estimated transit days)
    private static final Map<String, FedExService> FEDEX_SERVICES = Map.ofEntries(
            Map.entry("FEDEX_GROUND", new FedExService("FedEx Ground", 5)),
            Map.entry("GROUND_HOME_DELIVERY", new FedExService("FedEx Home Delivery", 5)),
            Map.entry("FEDEX_EXPRESS_SAVER", new FedExService("FedEx Express Saver", 3)),
            Map.entry("FEDEX_2_DAY", new FedExService("FedEx 2Day", 2)),
            Map.entry("FEDEX_2_DAY_AM", new FedExService("FedEx 2Day A.M.", 2)),
            Map.entry("STANDARD_OVERNIGHT", new FedExService("FedEx Standard Overnight", 1)),
            Map.entry("PRIORITY_OVERNIGHT", new FedExService("FedEx Priority Overnight", 1)),
            Map.entry("FIRST_OVERNIGHT", new FedExService("FedEx First Overnight", 1))
    );

    public FedExProvider(
            RestTemplate restTemplate,
            @Value("${shipsmart.fedex.base-url:https://apis-sandbox.fedex.com}") String baseUrl,
            @Value("${shipsmart.fedex.client-id:}") String clientId,
            @Value("${shipsmart.fedex.client-secret:}") String clientSecret,
            @Value("${shipsmart.fedex.account-number:}") String accountNumber
    ) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.accountNumber = accountNumber;
        this.restTemplate = restTemplate;

        if (isConfigured()) {
            log.info("FedExProvider initialized (base_url={})", this.baseUrl);
        } else {
            log.warn("FedExProvider not fully configured: missing credentials");
        }
    }

    @Override
    public String getName() {
        return "fedex";
    }

    @Override
    public List<ShippingServiceDto> getQuotes(ShipmentForQuote shipment) {
        try {
            if (!isConfigured()) {
                log.warn("FedExProvider not configured; skipping quote fetch");
                return List.of();
            }

            ensureToken();

            // Calculate billable weight (includes dimensional weight)
            double dimWeight = calculateDimensionalWeight(shipment);
            double billableWeight = shipment.totalWeight() > dimWeight ? shipment.totalWeight() : dimWeight;

            // Build FedEx rate request
            Map<String, Object> payload = buildRateRequest(shipment, billableWeight);

            // Call FedEx Rate API
            Map<String, Object> response = restTemplate.postForObject(
                    baseUrl + "/rate/v1/rates/quotes",
                    buildHttpRequest(payload),
                    Map.class
            );

            if (response == null) {
                log.warn("FedEx Rate API returned null response");
                return List.of();
            }

            return parseRateResponse(response, shipment.dropOffDate());

        } catch (RestClientException e) {
            log.error("FedEx API network error: {}", e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.error("FedEx quote fetch error: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private boolean isConfigured() {
        return clientId != null && !clientId.isEmpty()
                && clientSecret != null && !clientSecret.isEmpty()
                && accountNumber != null && !accountNumber.isEmpty();
    }

    private double calculateDimensionalWeight(ShipmentForQuote shipment) {
        // DIM_WT = (L × W × H) / 139 for domestic
        // Use the first package for now (multi-package DIM calculation is complex)
        if (shipment.packages().isEmpty()) {
            return 0;
        }
        var pkg = shipment.packages().get(0);
        double l = parseDouble(pkg.l());
        double w = parseDouble(pkg.w());
        double h = parseDouble(pkg.h());
        return (l * w * h) / 139.0;
    }

    private Map<String, Object> buildRateRequest(ShipmentForQuote shipment, double billableWeight) {
        var requestedShipment = new LinkedHashMap<String, Object>();

        requestedShipment.put("shipper", Map.of(
                "address", Map.of(
                        "postalCode", extractZip(shipment.origin()),
                        "countryCode", "US"
                )
        ));

        requestedShipment.put("recipient", Map.of(
                "address", Map.of(
                        "postalCode", extractZip(shipment.destination()),
                        "countryCode", "US"
                )
        ));

        requestedShipment.put("pickupType", "DROPOFF_AT_FEDEX_LOCATION");
        requestedShipment.put("rateRequestType", List.of("LIST"));

        // Package details from the first package (simplified for MVP)
        var pkg = shipment.packages().get(0);
        var packageItem = new LinkedHashMap<String, Object>();
        packageItem.put("weight", Map.of(
                "units", "LB",
                "value", round(billableWeight, 1)
        ));
        packageItem.put("dimensions", Map.of(
                "length", Math.round(parseDouble(pkg.l())),
                "width", Math.round(parseDouble(pkg.w())),
                "height", Math.round(parseDouble(pkg.h())),
                "units", "IN"
        ));

        requestedShipment.put("requestedPackageLineItems", List.of(packageItem));

        var payload = new LinkedHashMap<String, Object>();
        payload.put("accountNumber", Map.of("value", accountNumber));
        payload.put("requestedShipment", requestedShipment);

        return payload;
    }

    @SuppressWarnings("unchecked")
    private List<ShippingServiceDto> parseRateResponse(Map<String, Object> response, String dropOffDate) {
        List<ShippingServiceDto> results = new ArrayList<>();
        LocalDate baseDate = LocalDate.parse(dropOffDate);

        try {
            Map<String, Object> output = (Map<String, Object>) response.get("output");
            if (output == null) {
                return results;
            }

            List<Map<String, Object>> rateReplyDetails = (List<Map<String, Object>>) output.get("rateReplyDetails");
            if (rateReplyDetails == null || rateReplyDetails.isEmpty()) {
                log.debug("FedEx returned no rate details for this route");
                return results;
            }

            for (var rateDetail : rateReplyDetails) {
                String serviceType = (String) rateDetail.getOrDefault("serviceType", "");
                FedExService fedexService = FEDEX_SERVICES.getOrDefault(serviceType,
                        new FedExService("FedEx " + serviceType, 5));

                // Extract price
                double price = 0;
                List<Map<String, Object>> shipmentDetails =
                        (List<Map<String, Object>>) rateDetail.get("ratedShipmentDetails");
                if (shipmentDetails != null && !shipmentDetails.isEmpty()) {
                    Object totalCharges = shipmentDetails.get(0).get("totalNetCharge");
                    if (totalCharges != null) {
                        price = Double.parseDouble(totalCharges.toString());
                    }
                }

                // Extract transit days from commit
                int transitDays = fedexService.estimatedDays();
                Map<String, Object> commit = (Map<String, Object>) rateDetail.get("commit");
                if (commit != null) {
                    Map<String, Object> transitDaysObj = (Map<String, Object>) commit.get("transitDays");
                    if (transitDaysObj != null) {
                        String daysStr = (String) transitDaysObj.get("description");
                        if (daysStr != null && daysStr.matches("\\d+")) {
                            transitDays = Integer.parseInt(daysStr);
                        }
                    }
                }

                String serviceId = sanitizeServiceId(serviceType);
                LocalDate deliveryDate = baseDate.plusDays(transitDays);

                results.add(new ShippingServiceDto(
                        serviceId,
                        "FedEx",
                        fedexService.displayName(),
                        inferTier(transitDays),
                        round(price, 2),
                        null,  // No original price for real quotes
                        transitDays,
                        deliveryDate.format(DATE_FMT),
                        null,  // No specific deliver-by time from FedEx
                        false, // Guaranteed status varies; simplified to false
                        null,  // No promo from API
                        null,  // No AI recommendation
                        null,  // No breakdown from API
                        Map.of(),
                        List.of("Tracking")  // Basic features
                ));
            }

            results.sort(Comparator.comparingDouble(ShippingServiceDto::price));
            return results;

        } catch (Exception e) {
            log.error("Failed to parse FedEx rate response: {}", e.getMessage(), e);
            return results;
        }
    }

    private String sanitizeServiceId(String serviceType) {
        return "fedex-" + serviceType.toLowerCase().replace("_", "-");
    }

    private String inferTier(int transitDays) {
        if (transitDays == 1) {
            return "EXPRESS";
        } else if (transitDays <= 3) {
            return "EXPRESS";
        } else if (transitDays <= 5) {
            return "STANDARD";
        } else {
            return "ECONOMY";
        }
    }

    private String extractZip(String location) {
        // Simple extraction: assume format like "New York, NY 10001" or "NYC, NY"
        String[] parts = location.split(",");
        if (parts.length > 0) {
            String lastPart = parts[parts.length - 1].trim();
            String[] tokens = lastPart.split("\\s+");
            if (tokens.length > 0) {
                String candidate = tokens[tokens.length - 1];
                if (candidate.matches("\\d{5}(-\\d{4})?")) {
                    return candidate;
                }
            }
        }
        // Fallback: use last 5 chars that look like a ZIP
        for (int i = location.length() - 1; i >= location.length() - 10; i--) {
            if (i >= 0 && Character.isDigit(location.charAt(i))) {
                String suffix = location.substring(Math.max(0, i - 4), i + 1);
                if (suffix.matches("\\d+") && suffix.length() >= 5) {
                    return suffix.substring(suffix.length() - 5);
                }
            }
        }
        // If we can't extract ZIP, return a placeholder (FedEx will reject with clear error)
        return "00000";
    }

    private synchronized void ensureToken() throws Exception {
        long now = System.currentTimeMillis() / 1000;
        if (!accessToken.isEmpty() && now < tokenExpiresAt - TOKEN_REFRESH_BUFFER_SECS) {
            return;  // Token is still valid
        }

        try {
            String tokenUrl = baseUrl + "/oauth/token";
            String body = "grant_type=client_credentials&client_id=" + clientId + "&client_secret=" + clientSecret;

            Map<String, Object> response = restTemplate.postForObject(
                    tokenUrl,
                    buildFormUrlEncodedRequest(body),
                    Map.class
            );

            if (response == null || !response.containsKey("access_token")) {
                throw new RuntimeException("FedEx OAuth2: missing access_token in response");
            }

            accessToken = (String) response.get("access_token");
            int expiresIn = ((Number) response.getOrDefault("expires_in", 3600)).intValue();
            tokenExpiresAt = now + expiresIn;

            log.info("FedEx OAuth2 token acquired (expires_in={}s)", expiresIn);

        } catch (Exception e) {
            accessToken = "";
            log.error("FedEx OAuth2 token request failed: {}", e.getMessage());
            throw new RuntimeException("Failed to acquire FedEx API token", e);
        }
    }

    private org.springframework.http.HttpEntity<?> buildHttpRequest(Map<String, Object> payload) {
        var headers = new org.springframework.http.HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("Content-Type", "application/json");
        headers.set("X-locale", "en_US");
        return new org.springframework.http.HttpEntity<>(payload, headers);
    }

    private org.springframework.http.HttpEntity<?> buildFormUrlEncodedRequest(String body) {
        var headers = new org.springframework.http.HttpHeaders();
        headers.set("Content-Type", "application/x-www-form-urlencoded");
        return new org.springframework.http.HttpEntity<>(body, headers);
    }

    private static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException("Places must be >= 0");
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0;
        }
    }

    record FedExService(String displayName, int estimatedDays) {}
}
