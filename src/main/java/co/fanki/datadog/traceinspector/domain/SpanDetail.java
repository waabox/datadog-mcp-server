package co.fanki.datadog.traceinspector.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Detailed information about a single span within a trace.
 *
 * <p>A span represents a unit of work within a distributed trace. This record
 * captures all relevant span information including timing, error state, and
 * associated metadata.</p>
 *
 * @param spanId the unique span identifier
 * @param parentSpanId the parent span identifier, or null for root spans
 * @param service the service name that generated this span
 * @param operationName the operation or method name
 * @param resourceName the resource being accessed
 * @param startTime when the span started
 * @param duration the span duration in nanoseconds
 * @param isError whether this span represents an error
 * @param errorMessage the error message if this is an error span
 * @param errorType the error type or exception class
 * @param errorStack the stack trace if available
 * @param tags additional span tags and metadata
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public record SpanDetail(
        String spanId,
        String parentSpanId,
        String service,
        String operationName,
        String resourceName,
        Instant startTime,
        long duration,
        boolean isError,
        String errorMessage,
        String errorType,
        String errorStack,
        Map<String, String> tags
) {

    /**
     * Creates a SpanDetail with validated parameters.
     *
     * @throws IllegalArgumentException if validation fails
     */
    public SpanDetail {
        Objects.requireNonNull(spanId, "spanId must not be null");
        Objects.requireNonNull(service, "service must not be null");
        Objects.requireNonNull(startTime, "startTime must not be null");

        if (spanId.isBlank()) {
            throw new IllegalArgumentException("spanId must not be blank");
        }
        if (service.isBlank()) {
            throw new IllegalArgumentException("service must not be blank");
        }
        if (duration < 0) {
            throw new IllegalArgumentException("duration must be non-negative");
        }

        operationName = operationName != null ? operationName : "";
        resourceName = resourceName != null ? resourceName : "";
        errorMessage = errorMessage != null ? errorMessage : "";
        errorType = errorType != null ? errorType : "";
        errorStack = errorStack != null ? errorStack : "";
        tags = tags != null ? Map.copyOf(tags) : Map.of();
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

    /**
     * Checks if this span is a root span (has no parent).
     *
     * @return true if this is a root span
     */
    public boolean isRoot() {
        return parentSpanId == null || parentSpanId.isBlank()
                || "0".equals(parentSpanId);
    }

    /**
     * Returns a concise summary of the error if present.
     *
     * @return error summary or empty string if not an error
     */
    public String errorSummary() {
        if (!isError) {
            return "";
        }
        if (!errorType.isBlank() && !errorMessage.isBlank()) {
            return "%s: %s".formatted(errorType, errorMessage);
        }
        if (!errorMessage.isBlank()) {
            return errorMessage;
        }
        if (!errorType.isBlank()) {
            return errorType;
        }
        return "Unknown error";
    }
}
