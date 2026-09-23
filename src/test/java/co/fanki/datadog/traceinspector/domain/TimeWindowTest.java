package co.fanki.datadog.traceinspector.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for TimeWindow.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class TimeWindowTest {

    private static final Instant T13 = Instant.parse("2026-09-23T13:00:00Z");
    private static final Instant T14 = Instant.parse("2026-09-23T14:00:00Z");

    @Test
    void whenGettingLength_givenOneHourWindow_shouldReturnOneHour() {
        assertEquals(Duration.ofHours(1), new TimeWindow(T13, T14).length());
    }

    @Test
    void whenGettingPrevious_givenOneHourWindow_shouldReturnPrecedingHour() {
        final TimeWindow previous = new TimeWindow(T13, T14).previous();

        assertEquals(Instant.parse("2026-09-23T12:00:00Z"), previous.from());
        assertEquals(T13, previous.to());
    }

    @Test
    void whenCreating_givenFromEqualToTo_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> new TimeWindow(T13, T13));
    }

    @Test
    void whenCreating_givenFromAfterTo_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> new TimeWindow(T14, T13));
    }

    @Test
    void whenCreating_givenNullFrom_shouldThrow() {
        assertThrows(NullPointerException.class, () -> new TimeWindow(null, T14));
    }
}
