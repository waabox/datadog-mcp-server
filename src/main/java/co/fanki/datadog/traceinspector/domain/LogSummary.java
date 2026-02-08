package co.fanki.datadog.traceinspector.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Summary of a log entry from Datadog.
 *
 * <p>This record provides a simplified view of a log entry suitable for
 * display in search results.</p>
 *
 * @param timestamp when the log was generated
 * @param level the log level (ERROR, WARN, INFO, DEBUG)
 * @param service the service that generated the log
 * @param message the log message
 * @param host the host where the log was generated
 * @param traceId the associated trace ID, if any
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public record LogSummary(
        Instant timestamp,
        String level,
        String service,
        String message,
        String host,
        String traceId
) {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ISO_INSTANT;

    /**
     * Creates a LogSummary with validated parameters.
     *
     * @param timestamp when the log was generated, must not be null
     * @param level the log level, must not be null
     * @param service the service name, must not be null
     * @param message the log message, must not be null
     * @param host the host, must not be null
     * @param traceId the trace ID, may be null or empty
     */
    public LogSummary {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(level, "level must not be null");
        Objects.requireNonNull(service, "service must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(host, "host must not be null");
    }

    /**
     * Returns the timestamp formatted as ISO-8601 string.
     *
     * @return the formatted timestamp
     */
    public String formattedTimestamp() {
        return FORMATTER.format(timestamp.atOffset(ZoneOffset.UTC));
    }

    /**
     * Returns whether this log has an associated trace.
     *
     * @return true if traceId is present and not empty
     */
    public boolean hasTrace() {
        return traceId != null && !traceId.isBlank();
    }

    /**
     * Returns a truncated version of the message for display.
     *
     * @param maxLength the maximum length
     *
     * @return the truncated message with ellipsis if needed
     */
    public String truncatedMessage(final int maxLength) {
        if (message.length() <= maxLength) {
            return message;
        }
        return message.substring(0, maxLength - 3) + "...";
    }
}
