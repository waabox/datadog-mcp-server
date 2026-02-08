package co.fanki.datadog.traceinspector.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Summary of a group of similar log entries.
 *
 * <p>This record aggregates multiple log entries with similar patterns
 * to reduce response size when summarizing logs.</p>
 *
 * @param pattern the common pattern for this group of logs
 * @param level the log level
 * @param service the service name
 * @param count the number of logs in this group
 * @param firstOccurrence the timestamp of the first log in the group
 * @param lastOccurrence the timestamp of the last log in the group
 * @param sampleMessage a sample message from the group
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public record LogGroupSummary(
        String pattern,
        String level,
        String service,
        int count,
        Instant firstOccurrence,
        Instant lastOccurrence,
        String sampleMessage
) {

    private static final int MAX_PATTERN_LENGTH = 80;

    /**
     * Creates a LogGroupSummary with validated parameters.
     *
     * @param pattern the pattern, must not be null
     * @param level the log level, must not be null
     * @param service the service name, must not be null
     * @param count the count, must be positive
     * @param firstOccurrence the first occurrence, must not be null
     * @param lastOccurrence the last occurrence, must not be null
     * @param sampleMessage a sample message, must not be null
     */
    public LogGroupSummary {
        Objects.requireNonNull(pattern, "pattern must not be null");
        Objects.requireNonNull(level, "level must not be null");
        Objects.requireNonNull(service, "service must not be null");
        Objects.requireNonNull(firstOccurrence, "firstOccurrence must not be null");
        Objects.requireNonNull(lastOccurrence, "lastOccurrence must not be null");
        Objects.requireNonNull(sampleMessage, "sampleMessage must not be null");

        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
    }

    /**
     * Extracts a pattern from a log message for grouping purposes.
     *
     * <p>The pattern extraction removes variable parts like IDs, timestamps,
     * and numbers to group similar messages together.</p>
     *
     * @param message the log message
     *
     * @return the extracted pattern
     */
    public static String extractPattern(final String message) {
        if (message == null || message.isBlank()) {
            return "[empty]";
        }

        String pattern = message;

        // Order matters: more specific patterns first

        // Remove timestamps in various formats (before numeric IDs)
        pattern = pattern.replaceAll(
                "\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}[.\\d]*Z?",
                "<TS>"
        );

        // Remove UUIDs
        pattern = pattern.replaceAll(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
                "<UUID>"
        );

        // Remove IP addresses (before numeric IDs)
        pattern = pattern.replaceAll(
                "\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b",
                "<IP>"
        );

        // Remove hex strings (8+ chars, before numeric IDs)
        pattern = pattern.replaceAll("\\b[0-9a-fA-F]{8,}\\b", "<HEX>");

        // Remove numeric IDs (sequences of 4+ digits)
        pattern = pattern.replaceAll("\\b\\d{4,}\\b", "<ID>");

        // Truncate to max length
        if (pattern.length() > MAX_PATTERN_LENGTH) {
            pattern = pattern.substring(0, MAX_PATTERN_LENGTH - 3) + "...";
        }

        return pattern.trim();
    }

    /**
     * Returns a formatted time range for this group.
     *
     * @return the time range as a string
     */
    public String timeRange() {
        return firstOccurrence + " - " + lastOccurrence;
    }
}
