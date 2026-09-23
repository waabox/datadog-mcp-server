package co.fanki.datadog.traceinspector.domain;

/**
 * APM trace metrics for one service (or one resource) over one time window.
 *
 * @param hits the number of requests, never negative
 * @param errors the number of errored requests, never negative
 * @param latencyP50 the p50 latency in milliseconds, or null when not available
 * @param latencyP95 the p95 latency in milliseconds, or null when not available
 * @param latencyP99 the p99 latency in milliseconds, or null when not available
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public record ApmMetrics(long hits, long errors, Double latencyP50, Double latencyP95, Double latencyP99) {

    /**
     * Creates new ApmMetrics.
     *
     * @throws IllegalArgumentException if hits or errors are negative
     */
    public ApmMetrics {
        if (hits < 0) {
            throw new IllegalArgumentException("hits must not be negative");
        }
        if (errors < 0) {
            throw new IllegalArgumentException("errors must not be negative");
        }
    }

    /**
     * Returns metrics with no traffic and no latency data.
     *
     * @return empty metrics
     */
    public static ApmMetrics empty() {
        return new ApmMetrics(0L, 0L, null, null, null);
    }

    /**
     * Returns the error rate as a percentage (BR-9).
     *
     * @return {@code errors / hits * 100}, or 0 when there are no hits
     */
    public double errorRate() {
        if (hits == 0) {
            return 0.0;
        }
        return errors * 100.0 / hits;
    }

    /**
     * Tells whether any latency percentile is available.
     *
     * @return true if at least one of p50, p95 or p99 is not null
     */
    public boolean hasLatency() {
        return latencyP50 != null || latencyP95 != null || latencyP99 != null;
    }
}
