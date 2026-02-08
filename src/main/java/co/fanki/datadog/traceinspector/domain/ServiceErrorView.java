package co.fanki.datadog.traceinspector.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Error information organized by service.
 *
 * <p>This record provides a service-centric view of errors within a trace,
 * aggregating all error spans and logs for a specific service. This is useful
 * for understanding what went wrong in each service during a failed request.</p>
 *
 * @param serviceName the service name
 * @param errorSpans list of error spans from this service
 * @param relatedLogs log entries related to the error spans
 * @param primaryError the main error that caused the failure
 * @param timestamp when the first error occurred
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public record ServiceErrorView(
        String serviceName,
        List<SpanDetail> errorSpans,
        List<LogEntry> relatedLogs,
        String primaryError,
        Instant timestamp
) {

    /**
     * Creates a ServiceErrorView with validated parameters.
     *
     * @throws IllegalArgumentException if validation fails
     */
    public ServiceErrorView {
        Objects.requireNonNull(serviceName, "serviceName must not be null");
        Objects.requireNonNull(errorSpans, "errorSpans must not be null");

        if (serviceName.isBlank()) {
            throw new IllegalArgumentException("serviceName must not be blank");
        }

        errorSpans = List.copyOf(errorSpans);
        relatedLogs = relatedLogs != null ? List.copyOf(relatedLogs) : List.of();
        primaryError = primaryError != null ? primaryError : "";
        timestamp = timestamp != null ? timestamp : Instant.now();
    }

    /**
     * Represents a log entry related to an error.
     *
     * @param timestamp when the log was recorded
     * @param level the log level (ERROR, WARN, etc.)
     * @param message the log message
     * @param attributes additional log attributes
     */
    public record LogEntry(
            Instant timestamp,
            String level,
            String message,
            java.util.Map<String, String> attributes
    ) {
        public LogEntry {
            Objects.requireNonNull(timestamp, "timestamp must not be null");
            level = level != null ? level : "INFO";
            message = message != null ? message : "";
            attributes = attributes != null
                    ? java.util.Map.copyOf(attributes)
                    : java.util.Map.of();
        }
    }

    /**
     * Returns true if this service has any logs.
     *
     * @return true if there are related logs
     */
    public boolean hasLogs() {
        return !relatedLogs.isEmpty();
    }

    /**
     * Returns the count of error spans.
     *
     * @return number of error spans
     */
    public int errorCount() {
        return errorSpans.size();
    }

    /**
     * Extracts all unique error types from the error spans.
     *
     * @return list of unique error types
     */
    public List<String> uniqueErrorTypes() {
        return errorSpans.stream()
                .map(SpanDetail::errorType)
                .filter(type -> !type.isBlank())
                .distinct()
                .toList();
    }
}
