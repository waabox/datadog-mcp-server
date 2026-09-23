package co.fanki.datadog.traceinspector.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Health of one service in a time window compared with the previous window of the same length.
 *
 * <p>Holds the degraded rules from the APM service health use case. The rules are only evaluated
 * when the baseline window has at least {@link #MIN_BASELINE_HITS} hits (BR-11a). The service is
 * degraded when any of these holds:</p>
 * <ul>
 *   <li>BR-12: error rate at least doubled and rose by at least 1 percentage point.</li>
 *   <li>BR-13: p95 latency is at least 1.5 times the baseline.</li>
 *   <li>BR-14: hits dropped to half the baseline or less.</li>
 * </ul>
 *
 * @param service the service name
 * @param env the environment
 * @param operation the APM operation the metrics belong to
 * @param window the current window
 * @param current the metrics in the current window
 * @param baseline the metrics in the previous window
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public record ServiceHealth(
        String service,
        String env,
        ApmOperation operation,
        TimeWindow window,
        ApmMetrics current,
        ApmMetrics baseline
) {

    /** Minimum hits in the baseline window before the degraded rules are evaluated. */
    public static final long MIN_BASELINE_HITS = 100L;

    /** The current error rate must be at least this many times the baseline. */
    public static final double ERROR_RATE_MULTIPLIER = 2.0;

    /** The current error rate must exceed the baseline by at least this many percentage points. */
    public static final double ERROR_RATE_MIN_INCREASE_POINTS = 1.0;

    /** The current p95 must be at least this many times the baseline p95. */
    public static final double LATENCY_P95_MULTIPLIER = 1.5;

    /** Current hits at or below this fraction of the baseline count as a traffic drop. */
    public static final double TRAFFIC_DROP_RATIO = 0.5;

    /**
     * Creates a new ServiceHealth.
     *
     * @throws NullPointerException if any argument is null
     */
    public ServiceHealth {
        Objects.requireNonNull(service, "service must not be null");
        Objects.requireNonNull(env, "env must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(window, "window must not be null");
        Objects.requireNonNull(current, "current must not be null");
        Objects.requireNonNull(baseline, "baseline must not be null");
    }

    /**
     * Returns the baseline window, which is the previous window of the same length.
     *
     * @return the baseline window
     */
    public TimeWindow baselineWindow() {
        return window.previous();
    }

    /**
     * Compares hits.
     *
     * @return current vs baseline hits
     */
    public MetricComparison hits() {
        return new MetricComparison((double) current.hits(), (double) baseline.hits());
    }

    /**
     * Compares errors.
     *
     * @return current vs baseline errors
     */
    public MetricComparison errors() {
        return new MetricComparison((double) current.errors(), (double) baseline.errors());
    }

    /**
     * Compares the error rate, in percent.
     *
     * @return current vs baseline error rate
     */
    public MetricComparison errorRate() {
        return new MetricComparison(current.errorRate(), baseline.errorRate());
    }

    /**
     * Compares p50 latency, in milliseconds.
     *
     * @return current vs baseline p50
     */
    public MetricComparison latencyP50() {
        return new MetricComparison(current.latencyP50(), baseline.latencyP50());
    }

    /**
     * Compares p95 latency, in milliseconds.
     *
     * @return current vs baseline p95
     */
    public MetricComparison latencyP95() {
        return new MetricComparison(current.latencyP95(), baseline.latencyP95());
    }

    /**
     * Compares p99 latency, in milliseconds.
     *
     * @return current vs baseline p99
     */
    public MetricComparison latencyP99() {
        return new MetricComparison(current.latencyP99(), baseline.latencyP99());
    }

    /**
     * Tells whether the baseline has enough traffic to evaluate the degraded rules (BR-11a).
     *
     * @return true if the baseline has at least {@link #MIN_BASELINE_HITS} hits
     */
    public boolean hasSufficientTraffic() {
        return baseline.hits() >= MIN_BASELINE_HITS;
    }

    /**
     * Returns one human-readable entry per degraded rule that holds (BR-12, BR-13, BR-14).
     *
     * @return the signals, empty when not degraded or when traffic is insufficient
     */
    public List<String> signals() {
        if (!hasSufficientTraffic()) {
            return List.of();
        }

        final List<String> signals = new ArrayList<>();

        final double currentRate = current.errorRate();
        final double baselineRate = baseline.errorRate();
        if (currentRate >= ERROR_RATE_MULTIPLIER * baselineRate
                && currentRate - baselineRate >= ERROR_RATE_MIN_INCREASE_POINTS) {
            signals.add(String.format(Locale.ROOT, "error rate %.2f%% -> %.2f%%", baselineRate, currentRate));
        }

        final Double currentP95 = current.latencyP95();
        final Double baselineP95 = baseline.latencyP95();
        if (currentP95 != null && baselineP95 != null && baselineP95 > 0
                && currentP95 >= LATENCY_P95_MULTIPLIER * baselineP95) {
            signals.add(String.format(Locale.ROOT, "p95 latency %.1fms -> %.1fms", baselineP95, currentP95));
        }

        if (current.hits() <= TRAFFIC_DROP_RATIO * baseline.hits()) {
            signals.add(String.format(Locale.ROOT, "hits %d -> %d", baseline.hits(), current.hits()));
        }

        return List.copyOf(signals);
    }

    /**
     * Tells whether the service is degraded.
     *
     * @return true if at least one degraded rule holds
     */
    public boolean isDegraded() {
        return !signals().isEmpty();
    }

    /**
     * Returns explanations about missing or insufficient data (BR-10, BR-11a, BR-21).
     *
     * @return the notes, possibly empty
     */
    public List<String> notes() {
        final List<String> notes = new ArrayList<>();
        if (current.hits() == 0 && baseline.hits() == 0) {
            notes.add("no trace metrics found for operation " + operation.name() + " in either window");
        }
        if (!hasSufficientTraffic()) {
            notes.add(String.format(Locale.ROOT, "insufficient traffic in baseline window (%d hits < %d)",
                    baseline.hits(), MIN_BASELINE_HITS));
        }
        if (!current.hasLatency()) {
            notes.add("latency distribution metric trace." + operation.name() + " returned no data");
        }
        if (current.hasLatency() && !baseline.hasLatency()) {
            notes.add("latency distribution metric trace." + operation.name()
                    + " returned no data for the baseline window");
        }
        return List.copyOf(notes);
    }
}
