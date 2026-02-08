package co.fanki.datadog.traceinspector.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Complete trace information including all spans.
 *
 * <p>This record represents a full distributed trace with all its spans,
 * providing methods to analyze the trace structure, identify error paths,
 * and extract involved services.</p>
 *
 * @param traceId the unique trace identifier
 * @param service the primary service name
 * @param env the environment
 * @param resourceName the main resource name
 * @param startTime when the trace started
 * @param duration the total trace duration in nanoseconds
 * @param spans all spans in this trace
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public record TraceDetail(
        String traceId,
        String service,
        String env,
        String resourceName,
        Instant startTime,
        long duration,
        List<SpanDetail> spans
) {

    /**
     * Creates a TraceDetail with validated parameters.
     *
     * @throws IllegalArgumentException if validation fails
     */
    public TraceDetail {
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(service, "service must not be null");
        Objects.requireNonNull(startTime, "startTime must not be null");
        Objects.requireNonNull(spans, "spans must not be null");

        if (traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        if (service.isBlank()) {
            throw new IllegalArgumentException("service must not be blank");
        }
        if (duration < 0) {
            throw new IllegalArgumentException("duration must be non-negative");
        }

        env = env != null ? env : "";
        resourceName = resourceName != null ? resourceName : "";
        spans = List.copyOf(spans);
    }

    /**
     * Returns the set of unique services involved in this trace.
     *
     * <p>Extracts all distinct service names from the spans in this trace,
     * useful for understanding the service topology involved in a request.</p>
     *
     * @return an unmodifiable set of service names
     */
    public Set<String> involvedServices() {
        return spans.stream()
                .map(SpanDetail::service)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Returns all spans that have errors.
     *
     * @return a list of error spans
     */
    public List<SpanDetail> errorSpans() {
        return spans.stream()
                .filter(SpanDetail::isError)
                .toList();
    }

    /**
     * Returns the root span if present.
     *
     * @return the root span or null if not found
     */
    public SpanDetail rootSpan() {
        return spans.stream()
                .filter(SpanDetail::isRoot)
                .findFirst()
                .orElse(null);
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
     * Checks if this trace contains any errors.
     *
     * @return true if any span has an error
     */
    public boolean hasErrors() {
        return spans.stream().anyMatch(SpanDetail::isError);
    }

    /**
     * Returns the count of spans in this trace.
     *
     * @return the number of spans
     */
    public int spanCount() {
        return spans.size();
    }
}
