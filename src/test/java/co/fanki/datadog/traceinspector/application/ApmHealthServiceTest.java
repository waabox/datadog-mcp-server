package co.fanki.datadog.traceinspector.application;

import co.fanki.datadog.traceinspector.datadog.FakeApmMetricsClient;
import co.fanki.datadog.traceinspector.domain.ApmMetrics;
import co.fanki.datadog.traceinspector.domain.ApmOperation;
import co.fanki.datadog.traceinspector.domain.ResourceSortCriteria;
import co.fanki.datadog.traceinspector.domain.ResourceStats;
import co.fanki.datadog.traceinspector.domain.ServiceHealth;
import co.fanki.datadog.traceinspector.domain.TimeWindow;
import co.fanki.datadog.traceinspector.domain.TopResources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for ApmHealthService.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class ApmHealthServiceTest {

    private static final TimeWindow WINDOW = new TimeWindow(
            Instant.parse("2026-09-23T13:00:00Z"),
            Instant.parse("2026-09-23T14:00:00Z")
    );

    private FakeApmMetricsClient client;
    private ApmHealthService service;

    @BeforeEach
    void setUp() {
        client = new FakeApmMetricsClient();
        service = new ApmHealthService(client);
    }

    @Test
    void whenGettingHealth_givenProvidedOperation_shouldNotDetect() {
        client.detectedOperation("web.request");

        final ServiceHealth health = service.serviceHealth("payments", "prod", WINDOW, "servlet.request");

        assertEquals(ApmOperation.provided("servlet.request"), health.operation());
        assertEquals(0, client.detectCalls());
        assertEquals("servlet.request", client.lastOperation());
    }

    @Test
    void whenGettingHealth_givenNoOperation_shouldUseDetectedOperation() {
        client.detectedOperation("next.request");

        final ServiceHealth health = service.serviceHealth("portal", "prod", WINDOW, null);

        assertEquals(ApmOperation.detected("next.request"), health.operation());
        assertEquals(1, client.detectCalls());
    }

    @Test
    void whenGettingHealth_givenBlankOperation_shouldDetect() {
        client.detectedOperation("servlet.request");

        final ServiceHealth health = service.serviceHealth("payments", "prod", WINDOW, "  ");

        assertEquals(ApmOperation.Source.DETECTED, health.operation().source());
    }

    @Test
    void whenGettingHealth_givenNoOperation_shouldDetectOverCurrentAndBaselineWindows() {
        client.detectedOperation("servlet.request");

        service.serviceHealth("payments", "prod", WINDOW, null);

        assertEquals(new TimeWindow(WINDOW.previous().from(), WINDOW.to()), client.lastDetectionWindow());
    }

    @Test
    void whenGettingTopResources_givenNoOperation_shouldDetectOverCurrentWindowOnly() {
        client.detectedOperation("servlet.request");

        service.topResources("payments", "prod", WINDOW, null, ResourceSortCriteria.ERRORS,
                ResourceSortCriteria.DEFAULT_LIMIT);

        assertEquals(WINDOW, client.lastDetectionWindow());
    }

    @Test
    void whenGettingHealth_givenNothingDetected_shouldThrowWithHint() {
        final IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.serviceHealth("payments", "prod", WINDOW, null));

        assertEquals("No entry spans found for service payments in env prod; pass 'operation' explicitly",
                e.getMessage());
    }

    @Test
    void whenGettingHealth_shouldQueryCurrentAndPreviousWindows() {
        final ApmMetrics current = new ApmMetrics(1000, 20, null, null, null);
        final ApmMetrics baseline = new ApmMetrics(1000, 10, null, null, null);
        client.metricsFor(WINDOW, current);
        client.metricsFor(WINDOW.previous(), baseline);

        final ServiceHealth health = service.serviceHealth("payments", "prod", WINDOW, "servlet.request");

        assertEquals(List.of(WINDOW, WINDOW.previous()), client.queriedWindows());
        assertEquals(current, health.current());
        assertEquals(baseline, health.baseline());
    }

    @Test
    void whenGettingTopResources_shouldRankAndLimit() {
        client.resources(List.of(
                new ResourceStats("a", new ApmMetrics(10, 1, null, null, null)),
                new ResourceStats("b", new ApmMetrics(10, 5, null, null, null)),
                new ResourceStats("c", new ApmMetrics(10, 3, null, null, null))));

        final TopResources top = service.topResources(
                "payments", "prod", WINDOW, "servlet.request", ResourceSortCriteria.ERRORS, 2);

        assertEquals(List.of("b", "c"), top.resources().stream().map(ResourceStats::resource).toList());
        assertEquals(ResourceSortCriteria.ERRORS, top.sortBy());
    }

    @Test
    void whenGettingTopResources_givenInvalidLimit_shouldFailBeforeCallingDatadog() {
        client.detectedOperation("servlet.request");

        assertThrows(IllegalArgumentException.class,
                () -> service.topResources("payments", "prod", WINDOW, null, ResourceSortCriteria.ERRORS, 0));
        assertEquals(0, client.detectCalls());
        assertTrue(client.queriedWindows().isEmpty());
    }

    @Test
    void whenGettingHealth_givenBlankService_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> service.serviceHealth(" ", "prod", WINDOW, "servlet.request"));
    }
}
