package com.shipsmart.api.provider;

import com.shipsmart.api.dto.PackageItemDto;
import com.shipsmart.api.dto.ShippingServiceDto;
import com.shipsmart.api.service.provider.FedExProvider;
import com.shipsmart.api.service.provider.ShipmentForQuote;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Adapter — exposes the existing {@link FedExProvider} (carrier-shaped API,
 * carrier-shaped DTOs) through the internal {@link QuoteProvider} strategy
 * contract. This is the anti-corruption boundary: nothing in the domain or
 * fanout service knows about FedEx types, and the legacy provider stays
 * untouched so carrier-integration tests keep passing.
 */
@Component
public class FedExQuoteProviderAdapter extends AbstractQuoteProvider {

    private final FedExProvider fedex;
    private final boolean credentialsPresent;

    public FedExQuoteProviderAdapter(
            FedExProvider fedex,
            @Value("${shipsmart.fedex.client-id:}") String clientId,
            @Value("${shipsmart.fedex.client-secret:}") String clientSecret,
            @Value("${shipsmart.fedex.account-number:}") String accountNumber) {
        this.fedex = fedex;
        this.credentialsPresent =
                !clientId.isBlank() && !clientSecret.isBlank() && !accountNumber.isBlank();
    }

    @Override public String carrierCode() { return "fedex"; }

    @Override public boolean isEnabled() { return credentialsPresent; }

    @Override
    protected List<ProviderQuote> callCarrier(ProviderQuoteRequest req) {
        ShipmentForQuote legacy = new ShipmentForQuote(
                req.origin(),
                req.destination(),
                req.dropOffDate().toString(),
                req.expectedDeliveryDate().toString(),
                List.<PackageItemDto>of(),
                req.totalWeight(),
                req.totalItems());

        List<ShippingServiceDto> out = fedex.getQuotes(legacy);
        return out.stream()
                .map(s -> new ProviderQuote(
                        "FedEx",
                        s.name(),
                        s.tier(),
                        BigDecimal.valueOf(s.price()),
                        s.transitDays(),
                        s.guaranteed()))
                .toList();
    }
}
