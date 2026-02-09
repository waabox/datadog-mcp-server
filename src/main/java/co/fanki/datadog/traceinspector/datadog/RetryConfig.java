package co.fanki.datadog.traceinspector.datadog;

import java.util.Set;

/**
 * Configuration for retry behavior with exponential backoff.
 *
 * <p>This record defines the parameters for retry attempts including
 * delays, maximum attempts, and which HTTP status codes should trigger
 * a retry.</p>
 *
 * @param maxAttempts maximum number of attempts (including the initial one)
 * @param initialDelayMs initial delay in milliseconds before first retry
 * @param maxDelayMs maximum delay in milliseconds (caps the exponential growth)
 * @param multiplier multiplier for exponential backoff (e.g., 2.0 doubles delay each retry)
 * @param retryableStatusCodes HTTP status codes that should trigger a retry
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public record RetryConfig(
        int maxAttempts,
        long initialDelayMs,
        long maxDelayMs,
        double multiplier,
        Set<Integer> retryableStatusCodes
) {

    /** Default retryable status codes: 429 (rate limit), 500, 502, 503, 504. */
    private static final Set<Integer> DEFAULT_RETRYABLE_CODES = Set.of(
            429, 500, 502, 503, 504
    );

    /**
     * Creates a RetryConfig with validated parameters.
     *
     * @throws IllegalArgumentException if parameters are invalid
     */
    public RetryConfig {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        if (initialDelayMs < 0) {
            throw new IllegalArgumentException("initialDelayMs must be non-negative");
        }
        if (maxDelayMs < initialDelayMs) {
            throw new IllegalArgumentException("maxDelayMs must be >= initialDelayMs");
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be >= 1.0");
        }
        if (retryableStatusCodes == null) {
            retryableStatusCodes = DEFAULT_RETRYABLE_CODES;
        } else {
            retryableStatusCodes = Set.copyOf(retryableStatusCodes);
        }
    }

    /**
     * Creates a default retry configuration.
     *
     * <p>Default values:
     * <ul>
     *   <li>maxAttempts: 3</li>
     *   <li>initialDelayMs: 500ms</li>
     *   <li>maxDelayMs: 5000ms</li>
     *   <li>multiplier: 2.0</li>
     *   <li>retryableStatusCodes: 429, 500, 502, 503, 504</li>
     * </ul>
     * </p>
     *
     * @return the default configuration
     */
    public static RetryConfig defaults() {
        return new RetryConfig(3, 500L, 5000L, 2.0, DEFAULT_RETRYABLE_CODES);
    }

    /**
     * Creates a configuration with no retries (single attempt).
     *
     * @return a configuration that disables retries
     */
    public static RetryConfig noRetry() {
        return new RetryConfig(1, 0L, 0L, 1.0, Set.of());
    }

    /**
     * Checks if the given status code should trigger a retry.
     *
     * @param statusCode the HTTP status code
     *
     * @return true if the status code is retryable
     */
    public boolean isRetryable(final int statusCode) {
        return retryableStatusCodes.contains(statusCode);
    }

    /**
     * Calculates the delay for a given attempt number.
     *
     * <p>Uses exponential backoff: delay = initialDelay * (multiplier ^ attempt),
     * capped at maxDelay.</p>
     *
     * @param attempt the attempt number (0-based, so first retry is attempt 1)
     *
     * @return the delay in milliseconds
     */
    public long calculateDelay(final int attempt) {
        if (attempt <= 0) {
            return initialDelayMs;
        }
        final double delay = initialDelayMs * Math.pow(multiplier, attempt);
        return Math.min((long) delay, maxDelayMs);
    }
}
