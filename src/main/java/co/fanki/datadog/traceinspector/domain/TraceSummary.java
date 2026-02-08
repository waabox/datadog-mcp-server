package co.fanki.datadog.traceinspector.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Lightweight representation of a trace for listing purposes.
 *
 * <p>This record contains essential trace information without the full
 * span tree, suitable for displaying trace lists and enabling selection
 * for detailed inspection.</p>
 *
 * @param traceId the unique trace identifier
 * @param service the service name where the trace originated
 * @param resourceName the resource or endpoint name
 * @param errorMessage a brief error message if available
 * @param timestamp when the trace occurred
 * @param duration the trace duration in nanoseconds
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public record TraceSummary(
        String traceId,
        String service,
        String resourceName,
        String errorMessage,
        Instant timestamp,
        long duration
) {

    /**
     * Creates a TraceSummary with validated parameters.
     *
     * @param traceId the trace identifier, must not be null or blank
     * @param service the service name, must not be null or blank
     * @param resourceName the resource name, must not be null
     * @param errorMessage the error message, may be null
     * @param timestamp the occurrence time, must not be null
     * @param duration the duration in nanoseconds, must be non-negative
     *
     * @throws IllegalArgumentException if validation fails
     */
    public TraceSummary {
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(service, "service must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");

        if (traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        if (service.isBlank()) {
            throw new IllegalArgumentException("service must not be blank");
        }
        if (duration < 0) {
            throw new IllegalArgumentException("duration must be non-negative");
        }

        resourceName = resourceName != null ? resourceName : "";
        errorMessage = errorMessage != null ? errorMessage : "";
    }

    /**
     * Returns the duration formatted as a human-readable string.
     *
     * @return duration formatted as ms or s
     */
    public String formattedDuration() {
        final double ms = duration / 1_000_000.0;
        if (ms < 1000) {
            return "%.2fms".formatted(ms);
        }
        return "%.2fs".formatted(ms / 1000);
    }
}
