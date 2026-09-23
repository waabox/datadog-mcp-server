package co.fanki.datadog.traceinspector.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for TopResources.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class TopResourcesTest {

    private static final TimeWindow WINDOW = new TimeWindow(
            Instant.parse("2026-09-23T13:00:00Z"),
            Instant.parse("2026-09-23T14:00:00Z")
    );

    @Test
    void whenGettingNotes_givenNoResources_shouldExplainMissingMetrics() {
        final TopResources top = new TopResources("payments", "prod", ApmOperation.detected("servlet.request"),
                WINDOW, ResourceSortCriteria.ERRORS, List.of());

        assertEquals(List.of("no trace metrics found for operation servlet.request"), top.notes());
    }

    @Test
    void whenGettingNotes_givenResources_shouldBeEmpty() {
        final ResourceStats stats = new ResourceStats("GET /a", new ApmMetrics(1, 0, null, null, null));
        final TopResources top = new TopResources("payments", "prod", ApmOperation.detected("servlet.request"),
                WINDOW, ResourceSortCriteria.ERRORS, List.of(stats));

        assertTrue(top.notes().isEmpty());
    }
}
