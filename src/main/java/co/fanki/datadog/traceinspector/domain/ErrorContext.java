package co.fanki.datadog.traceinspector.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Represents the context of an error within a trace.
 *
 * <p>This record captures all relevant information about where and why
 * an error occurred, enabling Claude to navigate to the exact location
 * in the codebase and understand the error context.</p>
 *
 * @param service the service where the error occurred
 * @param operation the operation or method that failed
 * @param exceptionType the exception class name
 * @param message the error message
 * @param stackTrace the full stack trace
 * @param location the parsed source code location
 * @param spanTags relevant tags from the error span
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public record ErrorContext(
        String service,
        String operation,
        String exceptionType,
        String message,
        String stackTrace,
        StackTraceLocation location,
        Map<String, String> spanTags
) {

    /**
     * Creates an ErrorContext with validated parameters.
     */
    public ErrorContext {
        service = service != null ? service : "";
        operation = operation != null ? operation : "";
        exceptionType = exceptionType != null ? exceptionType : "";
        message = message != null ? message : "";
        stackTrace = stackTrace != null ? stackTrace : "";
        spanTags = spanTags != null ? Map.copyOf(spanTags) : Map.of();
    }

    /**
     * Creates an empty error context.
     *
     * @return an empty ErrorContext
     */
    public static ErrorContext empty() {
        return new ErrorContext("", "", "", "", "", null, Map.of());
    }

    /**
     * Returns whether this context has error information.
     *
     * @return true if there is error information
     */
    public boolean hasError() {
        return !exceptionType.isBlank() || !message.isBlank();
    }

    /**
     * Returns whether this context has a valid source location.
     *
     * @return true if the location is valid
     */
    public boolean hasLocation() {
        return location != null && location.isValid();
    }

    /**
     * Returns a formatted error summary.
     *
     * @return the error summary (e.g., "InsufficientStockException: Not enough stock")
     */
    public String errorSummary() {
        if (!exceptionType.isBlank() && !message.isBlank()) {
            return "%s: %s".formatted(exceptionType, message);
        }
        if (!message.isBlank()) {
            return message;
        }
        if (!exceptionType.isBlank()) {
            return exceptionType;
        }
        return "Unknown error";
    }

    /**
     * Returns the simple exception type name (without package).
     *
     * @return the simple exception type name
     */
    public String simpleExceptionType() {
        if (exceptionType.isBlank()) {
            return "";
        }
        final int lastDot = exceptionType.lastIndexOf('.');
        return lastDot >= 0 ? exceptionType.substring(lastDot + 1) : exceptionType;
    }
}
