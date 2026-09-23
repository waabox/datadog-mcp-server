package co.fanki.datadog.traceinspector.domain;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * How to rank a service's resources (BR-16 to BR-20).
 *
 * <p>Ranking is always descending. Ties are broken by hits, descending. Resources with null
 * latency go last when ranking by latency. When ranking by error rate, resources with fewer than
 * {@link #MIN_HITS_FOR_ERROR_RATE} hits are excluded.</p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public enum ResourceSortCriteria {

    /** By error count. */
    ERRORS("errors"),
    /** By error rate. */
    ERROR_RATE("errorRate"),
    /** By p95 latency. */
    LATENCY_P95("latencyP95"),
    /** By hit count. */
    HITS("hits");

    /** Minimum hits for a resource to be ranked by error rate (BR-17). */
    public static final long MIN_HITS_FOR_ERROR_RATE = 20L;

    /** Default number of resources returned (BR-20). */
    public static final int DEFAULT_LIMIT = 10;

    /** Maximum number of resources returned (BR-20). */
    public static final int MAX_LIMIT = 50;

    private final String key;

    ResourceSortCriteria(final String key) {
        this.key = key;
    }

    /**
     * Returns the key used in tool input.
     *
     * @return the key, such as {@code errorRate}
     */
    public String key() {
        return key;
    }

    /**
     * Parses a tool input key.
     *
     * @param key the key, must not be null
     *
     * @return the matching criteria
     *
     * @throws IllegalArgumentException if the key is unknown
     */
    public static ResourceSortCriteria fromKey(final String key) {
        Objects.requireNonNull(key, "key must not be null");
        for (final ResourceSortCriteria criteria : values()) {
            if (criteria.key.equals(key)) {
                return criteria;
            }
        }
        final String expected = Arrays.stream(values())
                .map(ResourceSortCriteria::key)
                .collect(Collectors.joining(", "));
        throw new IllegalArgumentException("Unknown sortBy: " + key + ". Expected one of: " + expected);
    }

    /**
     * Validates the number of resources to return.
     *
     * @param limit the requested limit
     *
     * @throws IllegalArgumentException if limit is not between 1 and {@link #MAX_LIMIT}
     */
    public static void requireValidLimit(final int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
    }

    /**
     * Ranks resources by this criteria.
     *
     * @param resources the resources to rank, must not be null
     * @param limit the maximum number of resources to return
     *
     * @return the ranked resources, at most {@code limit}
     *
     * @throws IllegalArgumentException if limit is out of range
     */
    public List<ResourceStats> rank(final List<ResourceStats> resources, final int limit) {
        Objects.requireNonNull(resources, "resources must not be null");
        requireValidLimit(limit);
        return resources.stream()
                .filter(this::isEligible)
                .sorted(comparator())
                .limit(limit)
                .toList();
    }

    private boolean isEligible(final ResourceStats stats) {
        return this != ERROR_RATE || stats.metrics().hits() >= MIN_HITS_FOR_ERROR_RATE;
    }

    private Comparator<ResourceStats> comparator() {
        final Comparator<ResourceStats> byHitsDesc =
                Comparator.comparingLong((ResourceStats r) -> r.metrics().hits()).reversed();
        final Comparator<ResourceStats> primary = switch (this) {
            case ERRORS -> Comparator.comparingLong((ResourceStats r) -> r.metrics().errors()).reversed();
            case ERROR_RATE -> Comparator.comparingDouble((ResourceStats r) -> r.metrics().errorRate()).reversed();
            case LATENCY_P95 -> Comparator.comparing(
                    (ResourceStats r) -> r.metrics().latencyP95(), Comparator.nullsLast(Comparator.reverseOrder()));
            case HITS -> byHitsDesc;
        };
        return primary.thenComparing(byHitsDesc);
    }
}
