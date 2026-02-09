package co.fanki.datadog.traceinspector.domain;

import java.util.Objects;

/**
 * Represents a single step in the trace execution flow.
 *
 * <p>Each step corresponds to a span in the trace, providing a chronological
 * view of what happened during the request processing.</p>
 *
 * @param order the execution order (1-based)
 * @param spanId the unique span identifier
 * @param parentSpanId the parent span identifier, or null for root
 * @param service the service that executed this step
 * @param operation the operation name or description
 * @param type the step type (http, db, internal, external, cache, queue)
 * @param detail additional details (e.g., SQL query, HTTP URL)
 * @param durationMs the step duration in milliseconds
 * @param isError whether this step resulted in an error
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public record ExecutionStep(
        int order,
        String spanId,
        String parentSpanId,
        String service,
        String operation,
        String type,
        String detail,
        long durationMs,
        boolean isError
) {

    /** Step type for HTTP requests/responses. */
    public static final String TYPE_HTTP = "http";

    /** Step type for database operations. */
    public static final String TYPE_DB = "db";

    /** Step type for internal method calls. */
    public static final String TYPE_INTERNAL = "internal";

    /** Step type for external service calls. */
    public static final String TYPE_EXTERNAL = "external";

    /** Step type for cache operations. */
    public static final String TYPE_CACHE = "cache";

    /** Step type for message queue operations. */
    public static final String TYPE_QUEUE = "queue";

    /**
     * Creates an ExecutionStep with validated parameters.
     */
    public ExecutionStep {
        if (order < 1) {
            order = 1;
        }
        spanId = spanId != null ? spanId : "";
        parentSpanId = parentSpanId != null ? parentSpanId : "";
        service = service != null ? service : "";
        operation = operation != null ? operation : "";
        type = type != null ? type : TYPE_INTERNAL;
        detail = detail != null ? detail : "";
        if (durationMs < 0) {
            durationMs = 0;
        }
    }

    /**
     * Returns whether this is a root step (no parent).
     *
     * @return true if this is a root step
     */
    public boolean isRoot() {
        return parentSpanId.isBlank() || "0".equals(parentSpanId);
    }

    /**
     * Returns a formatted duration string.
     *
     * @return duration formatted as "Xms" or "X.XXs"
     */
    public String formattedDuration() {
        if (durationMs < 1000) {
            return "%dms".formatted(durationMs);
        }
        return "%.2fs".formatted(durationMs / 1000.0);
    }

    /**
     * Returns a summary string for display.
     *
     * @return a formatted summary of this step
     */
    public String toSummary() {
        final String errorMarker = isError ? " [ERROR]" : "";
        return "%d. %s → %s (%s)%s".formatted(
                order, service, operation, formattedDuration(), errorMarker
        );
    }
}
