package com.hospitalfamilia.server.events.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospitalfamilia.server.events.exception.EventException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class EventQueryRangeTests {

    private static final Instant NOW = Instant.parse("2026-06-22T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void usesCompatibleThirtyDayWindowWhenRangeIsOmitted() {
        EventQueryRange range = EventQueryRange.resolve(null, null, CLOCK);

        assertThat(range.from()).isEqualTo(NOW);
        assertThat(range.to()).isEqualTo(Instant.parse("2026-07-22T12:00:00Z"));
        assertThat(range.explicit()).isFalse();
    }

    @Test
    void acceptsExplicitRangeUpToMaximumDuration() {
        EventQueryRange range = EventQueryRange.resolve(
            "2025-06-22T12:00:00Z",
            "2026-06-23T12:00:00Z",
            CLOCK
        );

        assertThat(range.explicit()).isTrue();
        assertThat(range.from()).isEqualTo(Instant.parse("2025-06-22T12:00:00Z"));
        assertThat(range.to()).isEqualTo(Instant.parse("2026-06-23T12:00:00Z"));
    }

    @Test
    void rejectsIncompleteInvalidReversedAndExcessiveRanges() {
        assertThatThrownBy(() -> EventQueryRange.resolve("2026-06-01T00:00:00Z", null, CLOCK))
            .isInstanceOf(EventException.class)
            .hasMessageContaining("deben enviarse juntos");
        assertThatThrownBy(() -> EventQueryRange.resolve("invalido", "2026-06-02T00:00:00Z", CLOCK))
            .isInstanceOf(EventException.class)
            .hasMessageContaining("formato ISO-8601");
        assertThatThrownBy(() -> EventQueryRange.resolve(
            "2026-06-02T00:00:00Z",
            "2026-06-01T00:00:00Z",
            CLOCK
        ))
            .isInstanceOf(EventException.class)
            .hasMessageContaining("anterior");
        assertThatThrownBy(() -> EventQueryRange.resolve(
            "2025-06-21T00:00:00Z",
            "2026-06-23T00:00:00Z",
            CLOCK
        ))
            .isInstanceOf(EventException.class)
            .hasMessageContaining("366 dias");
    }
}
