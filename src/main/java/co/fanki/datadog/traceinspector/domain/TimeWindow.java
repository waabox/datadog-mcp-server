package co.fanki.datadog.traceinspector.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A half-open time range {@code [from, to)} used to query APM metrics.
 *
 * @param from the inclusive start of the window
 * @param to the exclusive end of the window
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public record TimeWindow(Instant from, Instant to) {

    /**
     * Creates a new TimeWindow.
     *
     * @param from the start, must not be null
     * @param to the end, must not be null and must be after from
     *
     * @throws IllegalArgumentException if from is not before to
     */
    public TimeWindow {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
    }

    /**
     * Returns the length of this window.
     *
     * @return the duration between from and to
     */
    public Duration length() {
        return Duration.between(from, to);
    }

    /**
     * Returns the window of the same length that ends where this one starts.
     *
     * <p>Used as the baseline when comparing APM metrics (BR-6).</p>
     *
     * @return the previous window {@code [from - length, from)}
     */
    public TimeWindow previous() {
        return new TimeWindow(from.minus(length()), from);
    }
}
