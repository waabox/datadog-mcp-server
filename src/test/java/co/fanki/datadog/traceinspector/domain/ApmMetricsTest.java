package co.fanki.datadog.traceinspector.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for ApmMetrics.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class ApmMetricsTest {

    @Test
    void whenComputingErrorRate_givenZeroHits_shouldReturnZero() {
        assertEquals(0.0, ApmMetrics.empty().errorRate());
    }

    @Test
    void whenComputingErrorRate_givenHitsAndErrors_shouldReturnPercent() {
        final ApmMetrics metrics = new ApmMetrics(1000L, 25L, null, null, null);

        assertEquals(2.5, metrics.errorRate(), 0.0001);
    }

    @Test
    void whenCreatingEmpty_shouldHaveZeroCountsAndNoLatency() {
        final ApmMetrics empty = ApmMetrics.empty();

        assertEquals(0L, empty.hits());
        assertEquals(0L, empty.errors());
        assertNull(empty.latencyP95());
        assertFalse(empty.hasLatency());
    }

    @Test
    void whenCheckingLatency_givenOnlyP95_shouldReportLatency() {
        assertTrue(new ApmMetrics(10L, 0L, null, 120.0, null).hasLatency());
    }

    @Test
    void whenCreating_givenNegativeHits_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> new ApmMetrics(-1L, 0L, null, null, null));
    }

    @Test
    void whenCreating_givenNegativeErrors_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> new ApmMetrics(0L, -1L, null, null, null));
    }
}
