package co.fanki.datadog.traceinspector.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for ServiceHealth degraded rules.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class ServiceHealthTest {

    private static final TimeWindow WINDOW = new TimeWindow(
            Instant.parse("2026-09-23T13:00:00Z"),
            Instant.parse("2026-09-23T14:00:00Z")
    );

    private static ServiceHealth health(final ApmMetrics current, final ApmMetrics baseline) {
        final ApmOperation operation = ApmOperation.provided("servlet.request");
        return new ServiceHealth("payments", "prod", operation, WINDOW, current, baseline);
    }

    private static ApmMetrics metrics(final long hits, final long errors, final Double p95) {
        return new ApmMetrics(hits, errors, 10.0, p95, 500.0);
    }

    @Test
    void whenGettingBaselineWindow_shouldReturnPreviousWindow() {
        final ServiceHealth health = health(metrics(1000, 0, 100.0), metrics(1000, 0, 100.0));

        assertEquals(Instant.parse("2026-09-23T12:00:00Z"), health.baselineWindow().from());
    }

    @Test
    void whenEvaluating_givenStableMetrics_shouldNotBeDegraded() {
        final ServiceHealth health = health(metrics(1000, 10, 100.0), metrics(1000, 10, 100.0));

        assertFalse(health.isDegraded());
        assertTrue(health.signals().isEmpty());
    }

    @Test
    void whenEvaluating_givenErrorRateDoubledAndOnePointHigher_shouldBeDegraded() {
        final ServiceHealth health = health(metrics(1000, 20, 100.0), metrics(1000, 10, 100.0));

        assertTrue(health.isDegraded());
        assertEquals(List.of("error rate 1.00% -> 2.00%"), health.signals());
    }

    @Test
    void whenEvaluating_givenErrorRateJustBelowDouble_shouldNotBeDegraded() {
        final ServiceHealth health = health(metrics(1000, 19, 100.0), metrics(1000, 10, 100.0));

        assertFalse(health.isDegraded());
    }

    @Test
    void whenEvaluating_givenErrorRateTripledButLessThanOnePoint_shouldNotBeDegraded() {
        final ServiceHealth health = health(metrics(1000, 9, 100.0), metrics(1000, 1, 100.0));

        assertFalse(health.isDegraded());
    }

    @Test
    void whenEvaluating_givenZeroBaselineErrorsAndOnePointIncrease_shouldBeDegraded() {
        final ServiceHealth health = health(metrics(1000, 10, 100.0), metrics(1000, 0, 100.0));

        assertTrue(health.isDegraded());
    }

    @Test
    void whenEvaluating_givenP95AtOnePointFiveTimes_shouldBeDegraded() {
        final ServiceHealth health = health(metrics(1000, 0, 300.0), metrics(1000, 0, 200.0));

        assertTrue(health.isDegraded());
        assertEquals(List.of("p95 latency 200.0ms -> 300.0ms"), health.signals());
    }

    @Test
    void whenEvaluating_givenP95JustBelowThreshold_shouldNotBeDegraded() {
        final ServiceHealth health = health(metrics(1000, 0, 299.0), metrics(1000, 0, 200.0));

        assertFalse(health.isDegraded());
    }

    @Test
    void whenEvaluating_givenMissingBaselineP95_shouldIgnoreLatencyRule() {
        final ServiceHealth health = health(metrics(1000, 0, 900.0), metrics(1000, 0, null));

        assertFalse(health.isDegraded());
    }

    @Test
    void whenEvaluating_givenHitsDroppedByHalf_shouldBeDegraded() {
        final ServiceHealth health = health(metrics(500, 0, 100.0), metrics(1000, 0, 100.0));

        assertTrue(health.isDegraded());
        assertEquals(List.of("hits 1000 -> 500"), health.signals());
    }

    @Test
    void whenEvaluating_givenHitsDroppedJustUnderHalf_shouldNotBeDegraded() {
        final ServiceHealth health = health(metrics(501, 0, 100.0), metrics(1000, 0, 100.0));

        assertFalse(health.isDegraded());
    }

    @Test
    void whenEvaluating_givenSeveralRulesMatch_shouldReportAllSignals() {
        final ServiceHealth health = health(metrics(400, 40, 900.0), metrics(1000, 0, 100.0));

        assertEquals(3, health.signals().size());
    }

    @Test
    void whenEvaluating_givenBaselineWith99Hits_shouldSkipRulesAndAddNote() {
        final ServiceHealth health = health(metrics(99, 99, 900.0), metrics(99, 0, 100.0));

        assertFalse(health.hasSufficientTraffic());
        assertFalse(health.isDegraded());
        assertTrue(health.signals().isEmpty());
        assertTrue(health.notes().contains("insufficient traffic in baseline window (99 hits < 100)"));
    }

    @Test
    void whenEvaluating_givenBaselineWith100Hits_shouldEvaluateRules() {
        final ServiceHealth health = health(metrics(100, 50, 100.0), metrics(100, 0, 100.0));

        assertTrue(health.hasSufficientTraffic());
        assertTrue(health.isDegraded());
    }

    @Test
    void whenGettingNotes_givenNoTrafficInEitherWindow_shouldExplainMissingMetrics() {
        final ServiceHealth health = health(ApmMetrics.empty(), ApmMetrics.empty());

        assertTrue(health.notes().contains(
                "no trace metrics found for operation servlet.request in either window"));
    }

    @Test
    void whenGettingNotes_givenNoCurrentLatency_shouldExplainMissingLatency() {
        final ServiceHealth health = health(new ApmMetrics(1000, 0, null, null, null), metrics(1000, 0, 100.0));

        assertTrue(health.notes().contains(
                "latency distribution metric trace.servlet.request returned no data"));
    }

    @Test
    void whenGettingNotes_givenNoBaselineLatency_shouldExplainMissingBaselineLatency() {
        final ServiceHealth health = health(metrics(1000, 0, 100.0), new ApmMetrics(1000, 0, null, null, null));

        assertTrue(health.notes().contains(
                "latency distribution metric trace.servlet.request returned no data for the baseline window"));
    }

    @Test
    void whenComparingErrorRate_shouldUsePercentages() {
        final ServiceHealth health = health(metrics(1000, 20, 100.0), metrics(1000, 10, 100.0));

        assertEquals(2.0, health.errorRate().current(), 0.0001);
        assertEquals(1.0, health.errorRate().baseline(), 0.0001);
        assertEquals(100.0, health.errorRate().deltaPct(), 0.0001);
    }
}
