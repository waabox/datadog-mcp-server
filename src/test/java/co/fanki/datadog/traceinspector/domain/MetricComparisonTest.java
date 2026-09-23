package co.fanki.datadog.traceinspector.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for MetricComparison.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class MetricComparisonTest {

    @Test
    void whenComputingDelta_givenIncrease_shouldReturnPositivePercent() {
        assertEquals(50.0, new MetricComparison(150.0, 100.0).deltaPct(), 0.0001);
    }

    @Test
    void whenComputingDelta_givenDecrease_shouldReturnNegativePercent() {
        assertEquals(-50.0, new MetricComparison(100.0, 200.0).deltaPct(), 0.0001);
    }

    @Test
    void whenComputingDelta_givenZeroBaseline_shouldReturnNull() {
        assertNull(new MetricComparison(10.0, 0.0).deltaPct());
    }

    @Test
    void whenComputingDelta_givenNullCurrent_shouldReturnNull() {
        assertNull(new MetricComparison(null, 10.0).deltaPct());
    }

    @Test
    void whenComputingDelta_givenNullBaseline_shouldReturnNull() {
        assertNull(new MetricComparison(10.0, null).deltaPct());
    }
}
