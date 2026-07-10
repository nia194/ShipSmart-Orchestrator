package com.shipsmart.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.shipsmart.api.domain.TrackingStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tracking-model tests (Product Roadmap §15 P6). */
class TrackingStatusDtoTest {

    private static TrackingStatusDto timeline(
            TrackingStatus current, List<TrackingEventDto> events) {
        return new TrackingStatusDto("1Z999", "UPS", current, "2026-07-15", events);
    }

    @Test
    void latestEventIsTheMostRecent() {
        var t =
                timeline(
                        TrackingStatus.IN_TRANSIT,
                        List.of(
                                new TrackingEventDto(
                                        "2026-07-10T08:00:00Z",
                                        TrackingStatus.LABEL_CREATED,
                                        "LA",
                                        "Label created"),
                                new TrackingEventDto(
                                        "2026-07-11T09:00:00Z",
                                        TrackingStatus.IN_TRANSIT,
                                        "Denver",
                                        "In transit")));
        assertThat(t.latestEvent()).isPresent();
        assertThat(t.latestEvent().get().location()).isEqualTo("Denver");
    }

    @Test
    void completeAndAttentionFlagsFollowTheStatus() {
        assertThat(timeline(TrackingStatus.DELIVERED, List.of()).isComplete()).isTrue();
        assertThat(timeline(TrackingStatus.RETURNED, List.of()).isComplete()).isTrue();
        assertThat(timeline(TrackingStatus.IN_TRANSIT, List.of()).isComplete()).isFalse();
        assertThat(timeline(TrackingStatus.EXCEPTION, List.of()).needsAttention()).isTrue();
        assertThat(timeline(TrackingStatus.IN_TRANSIT, List.of()).needsAttention()).isFalse();
    }

    @Test
    void eventsAreDefensivelyCopiedAndNullSafe() {
        assertThat(timeline(TrackingStatus.LABEL_CREATED, null).events()).isEmpty();
        assertThat(timeline(TrackingStatus.LABEL_CREATED, null).latestEvent()).isEmpty();
    }

    @Test
    void terminalAndActionableSemantics() {
        assertThat(TrackingStatus.DELIVERED.isTerminal()).isTrue();
        assertThat(TrackingStatus.EXCEPTION.isActionable()).isTrue();
        assertThat(TrackingStatus.IN_TRANSIT.isTerminal()).isFalse();
    }
}
