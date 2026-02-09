package co.fanki.datadog.traceinspector.datadog;

import com.datadog.api.client.ApiException;

import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Executes operations with retry logic and exponential backoff.
 *
 * <p>This class handles transient failures by retrying operations
 * based on the configured retry policy. It uses exponential backoff
 * to avoid overwhelming the target service.</p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public final class RetryExecutor {

    private final RetryConfig config;

    /**
     * Creates a new RetryExecutor with the given configuration.
     *
     * @param config the retry configuration
     */
    public RetryExecutor(final RetryConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    /**
     * Creates a RetryExecutor with default configuration.
     *
     * @return a new RetryExecutor with default settings
     */
    public static RetryExecutor withDefaults() {
        return new RetryExecutor(RetryConfig.defaults());
    }

    /**
     * Executes an operation with retry logic.
     *
     * <p>The operation is attempted up to maxAttempts times. If it fails
     * with a retryable status code, the executor waits with exponential
     * backoff before retrying.</p>
     *
     * @param operation the operation to execute
     * @param operationName a descriptive name for logging/errors
     * @param <T> the return type of the operation
     *
     * @return the result of the operation
     *
     * @throws DatadogApiException if all retry attempts fail
     */
    public <T> T execute(
            final Callable<T> operation,
            final String operationName
    ) {
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(operationName, "operationName must not be null");

        ApiException lastException = null;
        int attempt = 0;

        while (attempt < config.maxAttempts()) {
            try {
                return operation.call();
            } catch (final ApiException e) {
                lastException = e;

                if (!shouldRetry(e, attempt)) {
                    break;
                }

                final long delay = config.calculateDelay(attempt);
                logRetry(operationName, attempt + 1, config.maxAttempts(), e.getCode(), delay);
                sleep(delay);

                attempt++;
            } catch (final Exception e) {
                throw new DatadogApiException(
                        "Unexpected error during " + operationName + ": " + e.getMessage(),
                        0
                );
            }
        }

        throw buildFinalException(operationName, lastException, attempt);
    }

    /**
     * Determines if an operation should be retried.
     *
     * @param exception the exception that occurred
     * @param attempt the current attempt number (0-based)
     *
     * @return true if the operation should be retried
     */
    private boolean shouldRetry(final ApiException exception, final int attempt) {
        if (attempt + 1 >= config.maxAttempts()) {
            return false;
        }
        return config.isRetryable(exception.getCode());
    }

    /**
     * Logs a retry attempt.
     *
     * @param operationName the operation being retried
     * @param currentAttempt the current attempt number (1-based for display)
     * @param maxAttempts the maximum number of attempts
     * @param statusCode the HTTP status code that triggered the retry
     * @param delayMs the delay before the next attempt
     */
    private void logRetry(
            final String operationName,
            final int currentAttempt,
            final int maxAttempts,
            final int statusCode,
            final long delayMs
    ) {
        System.err.printf(
                "[RETRY] %s failed with status %d. Attempt %d/%d. Retrying in %dms...%n",
                operationName,
                statusCode,
                currentAttempt,
                maxAttempts,
                delayMs
        );
    }

    /**
     * Sleeps for the specified duration.
     *
     * @param millis the duration in milliseconds
     */
    private void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatadogApiException("Retry interrupted", 0);
        }
    }

    /**
     * Builds the final exception after all retries have been exhausted.
     *
     * @param operationName the operation that failed
     * @param lastException the last exception that occurred
     * @param attempts the number of attempts made
     *
     * @return the exception to throw
     */
    private DatadogApiException buildFinalException(
            final String operationName,
            final ApiException lastException,
            final int attempts
    ) {
        final String message;
        final int code;

        if (lastException != null) {
            message = String.format(
                    "Failed to %s after %d attempt(s): %s",
                    operationName,
                    attempts + 1,
                    lastException.getResponseBody()
            );
            code = lastException.getCode();
        } else {
            message = "Failed to " + operationName + ": unknown error";
            code = 0;
        }

        return new DatadogApiException(message, code);
    }
}
