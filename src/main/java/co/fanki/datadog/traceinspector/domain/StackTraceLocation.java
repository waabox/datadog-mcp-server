package co.fanki.datadog.traceinspector.domain;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a parsed location from a stack trace.
 *
 * <p>This record extracts the class name, method name, file name, and line number
 * from a stack trace line, enabling Claude to navigate directly to the error location
 * in the codebase.</p>
 *
 * @param className the fully qualified class name
 * @param methodName the method name where the error occurred
 * @param fileName the source file name
 * @param lineNumber the line number in the source file
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public record StackTraceLocation(
        String className,
        String methodName,
        String fileName,
        int lineNumber
) {

    /**
     * Pattern to match Java stack trace lines.
     * Example: "at com.example.OrderService.validateStock(OrderService.java:142)"
     */
    private static final Pattern STACK_TRACE_PATTERN = Pattern.compile(
            "\\s*at\\s+([\\w.$]+)\\.([\\w$]+)\\(([\\w.]+):(\\d+)\\)"
    );

    /**
     * Creates a StackTraceLocation with validated parameters.
     */
    public StackTraceLocation {
        className = className != null ? className : "";
        methodName = methodName != null ? methodName : "";
        fileName = fileName != null ? fileName : "";
        if (lineNumber < 0) {
            lineNumber = 0;
        }
    }

    /**
     * Parses a stack trace line to extract location information.
     *
     * @param stackTraceLine a single line from a stack trace
     * @return the parsed location, or null if the line cannot be parsed
     */
    public static StackTraceLocation parse(final String stackTraceLine) {
        if (stackTraceLine == null || stackTraceLine.isBlank()) {
            return null;
        }

        final Matcher matcher = STACK_TRACE_PATTERN.matcher(stackTraceLine);
        if (matcher.find()) {
            return new StackTraceLocation(
                    matcher.group(1),
                    matcher.group(2),
                    matcher.group(3),
                    Integer.parseInt(matcher.group(4))
            );
        }
        return null;
    }

    /**
     * Parses the first valid location from a full stack trace.
     *
     * @param stackTrace the full stack trace string
     * @return the first parsed location, or null if none found
     */
    public static StackTraceLocation parseFirst(final String stackTrace) {
        if (stackTrace == null || stackTrace.isBlank()) {
            return null;
        }

        final String[] lines = stackTrace.split("\n");
        for (final String line : lines) {
            final StackTraceLocation location = parse(line);
            if (location != null) {
                return location;
            }
        }
        return null;
    }

    /**
     * Returns whether this location has valid information.
     *
     * @return true if the location has a file name and line number
     */
    public boolean isValid() {
        return !fileName.isBlank() && lineNumber > 0;
    }

    /**
     * Returns a formatted string for navigation (e.g., "OrderService.java:142").
     *
     * @return the formatted location string
     */
    public String toNavigationString() {
        if (!isValid()) {
            return "";
        }
        return "%s:%d".formatted(fileName, lineNumber);
    }

    /**
     * Returns the simple class name (without package).
     *
     * @return the simple class name
     */
    public String simpleClassName() {
        if (className.isBlank()) {
            return "";
        }
        final int lastDot = className.lastIndexOf('.');
        return lastDot >= 0 ? className.substring(lastDot + 1) : className;
    }
}
