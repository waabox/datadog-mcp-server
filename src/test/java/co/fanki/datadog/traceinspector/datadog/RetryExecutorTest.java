package co.fanki.datadog.traceinspector.datadog;

import com.datadog.api.client.ApiException;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for RetryExecutor.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class RetryExecutorTest {

    @Test
    void whenExecuting_givenSuccessfulOperation_shouldReturnResult() {
        final RetryExecutor executor = new RetryExecutor(RetryConfig.defaults());

        final String result = executor.execute(() -> "success", "test operation");

        assertEquals("success", result);
    }

    @Test
    void whenExecuting_givenTransientFailure_shouldRetryAndSucceed() {
        final RetryConfig config = new RetryConfig(3, 10, 100, 2.0, Set.of(500));
        final RetryExecutor executor = new RetryExecutor(config);
        final AtomicInteger attempts = new AtomicInteger(0);

        final String result = executor.execute(() -> {
            if (attempts.incrementAndGet() < 2) {
                throw new ApiException(500, "Server error");
            }
            return "success";
        }, "test operation");

        assertEquals("success", result);
        assertEquals(2, attempts.get());
    }

    @Test
    void whenExecuting_givenPersistentFailure_shouldExhaustRetriesAndThrow() {
        final RetryConfig config = new RetryConfig(3, 10, 100, 2.0, Set.of(500));
        final RetryExecutor executor = new RetryExecutor(config);
        final AtomicInteger attempts = new AtomicInteger(0);

        final DatadogApiException exception = assertThrows(DatadogApiException.class,
                () -> executor.execute(() -> {
                    attempts.incrementAndGet();
                    throw new ApiException(500, "Server error");
                }, "test operation"));

        assertEquals(3, attempts.get());
        assertEquals(500, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("after 3 attempt(s)"));
    }

    @Test
    void whenExecuting_givenNonRetryableError_shouldNotRetry() {
        final RetryConfig config = new RetryConfig(3, 10, 100, 2.0, Set.of(500, 503));
        final RetryExecutor executor = new RetryExecutor(config);
        final AtomicInteger attempts = new AtomicInteger(0);

        final DatadogApiException exception = assertThrows(DatadogApiException.class,
                () -> executor.execute(() -> {
                    attempts.incrementAndGet();
                    throw new ApiException(400, "Bad request");
                }, "test operation"));

        assertEquals(1, attempts.get());
        assertEquals(400, exception.getStatusCode());
    }

    @Test
    void whenExecuting_givenRateLimitError_shouldRetry() {
        final RetryConfig config = new RetryConfig(3, 10, 100, 2.0, Set.of(429));
        final RetryExecutor executor = new RetryExecutor(config);
        final AtomicInteger attempts = new AtomicInteger(0);

        final String result = executor.execute(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new ApiException(429, "Rate limited");
            }
            return "success";
        }, "test operation");

        assertEquals("success", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void whenExecuting_givenNoRetryConfig_shouldNotRetry() {
        final RetryExecutor executor = new RetryExecutor(RetryConfig.noRetry());
        final AtomicInteger attempts = new AtomicInteger(0);

        final DatadogApiException exception = assertThrows(DatadogApiException.class,
                () -> executor.execute(() -> {
                    attempts.incrementAndGet();
                    throw new ApiException(500, "Server error");
                }, "test operation"));

        assertEquals(1, attempts.get());
    }

    @Test
    void whenCreatingWithDefaults_shouldUseDefaultConfig() {
        final RetryExecutor executor = RetryExecutor.withDefaults();

        final String result = executor.execute(() -> "success", "test operation");

        assertEquals("success", result);
    }

    @Test
    void whenExecuting_givenNullOperation_shouldThrowException() {
        final RetryExecutor executor = RetryExecutor.withDefaults();

        assertThrows(NullPointerException.class,
                () -> executor.execute(null, "test operation"));
    }

    @Test
    void whenExecuting_givenNullOperationName_shouldThrowException() {
        final RetryExecutor executor = RetryExecutor.withDefaults();

        assertThrows(NullPointerException.class,
                () -> executor.execute(() -> "success", null));
    }
}
