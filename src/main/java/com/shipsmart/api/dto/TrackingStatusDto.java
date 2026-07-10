package com.shipsmart.api.dto;

import com.shipsmart.api.domain.TrackingStatus;
import java.util.List;
import java.util.Optional;

/**
 * A normalized tracking timeline for a shipment (Product Roadmap §15 P6).
 *
 * <p>The product renders one consistent timeline regardless of carrier: a current status, the
 * estimated delivery, and the ordered milestone events. Java owns this model (transactional truth);
 * the assistant explains it and never invents a status. Populated from carrier tracking/webhooks
 * [S19] — this DTO is the stable shape.
 */
public record TrackingStatusDto(
        String trackingNumber,
        String carrier,
        TrackingStatus currentStatus,
        String estimatedDelivery,
        List<TrackingEventDto> events) {

    public TrackingStatusDto {
        events = events == null ? List.of() : List.copyOf(events);
    }

    /** The most recent event (events are stored oldest-first), if any. */
    public Optional<TrackingEventDto> latestEvent() {
        return events.isEmpty() ? Optional.empty() : Optional.of(events.get(events.size() - 1));
    }

    /** Whether the timeline is complete (delivered or returned). */
    public boolean isComplete() {
        return currentStatus != null && currentStatus.isTerminal();
    }

    /** Whether the shipment needs attention (a delivery exception). */
    public boolean needsAttention() {
        return currentStatus != null && currentStatus.isActionable();
    }
}
