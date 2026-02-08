package co.fanki.datadog.traceinspector.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Complete diagnostic output for an error trace.
 *
 * <p>This record aggregates all diagnostic information about an error trace,
 * including the trace details, service-level error views, and the generated
 * actionable workflow markdown.</p>
 *
 * @param traceDetail the complete trace information
 * @param serviceErrors error information organized by service
 * @param workflow the generated markdown workflow for debugging
 * @param generatedAt when this diagnostic was generated
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public record DiagnosticResult(
        TraceDetail traceDetail,
        List<ServiceErrorView> serviceErrors,
        String workflow,
        Instant generatedAt
) {

    /**
     * Creates a DiagnosticResult with validated parameters.
     *
     * @throws IllegalArgumentException if validation fails
     */
    public DiagnosticResult {
        Objects.requireNonNull(traceDetail, "traceDetail must not be null");
        Objects.requireNonNull(serviceErrors, "serviceErrors must not be null");
        Objects.requireNonNull(workflow, "workflow must not be null");

        serviceErrors = List.copyOf(serviceErrors);
        generatedAt = generatedAt != null ? generatedAt : Instant.now();
    }

    /**
     * Convenience factory method.
     *
     * @param traceDetail the trace details
     * @param serviceErrors the service error views
     * @param workflow the generated workflow markdown
     *
     * @return a new DiagnosticResult with current timestamp
     */
    public static DiagnosticResult of(
            final TraceDetail traceDetail,
            final List<ServiceErrorView> serviceErrors,
            final String workflow
    ) {
        return new DiagnosticResult(traceDetail, serviceErrors, workflow, Instant.now());
    }

    /**
     * Returns the trace ID for easy access.
     *
     * @return the trace ID
     */
    public String traceId() {
        return traceDetail.traceId();
    }

    /**
     * Returns the primary service name.
     *
     * @return the service name
     */
    public String service() {
        return traceDetail.service();
    }

    /**
     * Returns all services involved in the error.
     *
     * @return set of service names
     */
    public Set<String> involvedServices() {
        return traceDetail.involvedServices();
    }

    /**
     * Returns the total number of error spans across all services.
     *
     * @return total error count
     */
    public int totalErrorCount() {
        return serviceErrors.stream()
                .mapToInt(ServiceErrorView::errorCount)
                .sum();
    }

    /**
     * Checks if there are multiple services involved in the error.
     *
     * @return true if more than one service has errors
     */
    public boolean isDistributedError() {
        return serviceErrors.size() > 1;
    }
}
