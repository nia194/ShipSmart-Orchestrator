package com.shipsmart.api.service.provider;

import com.shipsmart.api.dto.ShippingServiceDto;
import java.util.List;

/**
 * Abstraction for shipping carrier providers.
 * Implementations fetch real quotes from carrier APIs.
 * Enables clean separation and testability.
 */
public interface ShippingProvider {
    /**
     * Get the provider identifier (e.g., "fedex", "ups", "dhl").
     */
    String getName();

    /**
     * Get shipping quotes for the given shipment.
     * Returns an empty list if the route is not supported or if an error occurs.
     *
     * @param shipment the shipment details
     * @return list of available shipping services (empty if unavailable)
     */
    List<ShippingServiceDto> getQuotes(ShipmentForQuote shipment);
}
