package co.fanki.datadog.traceinspector.datadog;

/**
 * Exception thrown when a Datadog API call fails.
 *
 * <p>This exception wraps HTTP errors, parsing failures, and other API-related
 * issues that may occur when communicating with Datadog.</p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public class DatadogApiException extends RuntimeException {

    private final int statusCode;

    /**
     * Creates a new DatadogApiException with a message.
     *
     * @param message the error message
     */
    public DatadogApiException(final String message) {
        super(message);
        this.statusCode = -1;
    }

    /**
     * Creates a new DatadogApiException with a message and cause.
     *
     * @param message the error message
     * @param cause the underlying cause
     */
    public DatadogApiException(final String message, final Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    /**
     * Creates a new DatadogApiException with HTTP status code.
     *
     * @param message the error message
     * @param statusCode the HTTP status code
     */
    public DatadogApiException(final String message, final int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    /**
     * Returns the HTTP status code if available.
     *
     * @return the status code or -1 if not applicable
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Checks if this is an authentication error.
     *
     * @return true if the status code indicates auth failure
     */
    public boolean isAuthenticationError() {
        return statusCode == 401 || statusCode == 403;
    }

    /**
     * Checks if this is a rate limit error.
     *
     * @return true if rate limited
     */
    public boolean isRateLimitError() {
        return statusCode == 429;
    }
}
