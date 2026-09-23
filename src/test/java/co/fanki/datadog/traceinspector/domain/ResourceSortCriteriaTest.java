package co.fanki.datadog.traceinspector.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for ResourceSortCriteria ranking.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class ResourceSortCriteriaTest {

    private static ResourceStats stats(final String name, final long hits, final long errors, final Double p95) {
        return new ResourceStats(name, new ApmMetrics(hits, errors, null, p95, null));
    }

    private static List<String> names(final List<ResourceStats> ranked) {
        return ranked.stream().map(ResourceStats::resource).toList();
    }

    @Test
    void whenParsingKey_givenErrorRate_shouldReturnErrorRate() {
        assertEquals(ResourceSortCriteria.ERROR_RATE, ResourceSortCriteria.fromKey("errorRate"));
    }

    @Test
    void whenParsingKey_givenUnknownKey_shouldThrowWithExpectedValues() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ResourceSortCriteria.fromKey("bogus"));

        assertTrue(e.getMessage().contains("errors, errorRate, latencyP95, hits"));
    }

    @Test
    void whenRankingByErrors_givenTie_shouldBreakTieByHits() {
        final List<ResourceStats> input = List.of(
                stats("a", 100, 5, null), stats("c", 50, 10, null), stats("b", 200, 10, null));

        assertEquals(List.of("b", "c", "a"), names(ResourceSortCriteria.ERRORS.rank(input, 10)));
    }

    @Test
    void whenRankingByErrorRate_givenLowTrafficResource_shouldExcludeIt() {
        final List<ResourceStats> input = List.of(
                stats("tiny", 1, 1, null), stats("mid", 20, 1, null), stats("big", 100, 10, null));

        assertEquals(List.of("big", "mid"), names(ResourceSortCriteria.ERROR_RATE.rank(input, 10)));
    }

    @Test
    void whenRankingByLatency_givenNullLatency_shouldPlaceItLast() {
        final List<ResourceStats> input = List.of(
                stats("x", 1000, 0, null), stats("y", 10, 0, 500.0), stats("z", 10, 0, 900.0));

        assertEquals(List.of("z", "y", "x"), names(ResourceSortCriteria.LATENCY_P95.rank(input, 10)));
    }

    @Test
    void whenRankingByHits_shouldOrderDescending() {
        final List<ResourceStats> input = List.of(
                stats("low", 10, 0, null), stats("high", 1000, 0, null), stats("mid", 100, 0, null));

        assertEquals(List.of("high", "mid", "low"), names(ResourceSortCriteria.HITS.rank(input, 10)));
    }

    @Test
    void whenRanking_givenLimitSmallerThanInput_shouldTruncate() {
        final List<ResourceStats> input = List.of(
                stats("a", 3, 0, null), stats("b", 2, 0, null), stats("c", 1, 0, null));

        assertEquals(List.of("a", "b"), names(ResourceSortCriteria.HITS.rank(input, 2)));
    }

    @Test
    void whenValidatingLimit_givenZero_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> ResourceSortCriteria.requireValidLimit(0));
    }

    @Test
    void whenValidatingLimit_given51_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> ResourceSortCriteria.requireValidLimit(51));
    }

    @Test
    void whenCreatingStats_givenBlankResource_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> stats(" ", 1, 0, null));
    }
}
