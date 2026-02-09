package co.fanki.datadog.traceinspector.datadog;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for RetryConfig.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class RetryConfigTest {

    @Test
    void whenCreatingDefaults_shouldHaveExpectedValues() {
        final RetryConfig config = RetryConfig.defaults();

        assertEquals(3, config.maxAttempts());
        assertEquals(500L, config.initialDelayMs());
        assertEquals(5000L, config.maxDelayMs());
        assertEquals(2.0, config.multiplier());
        assertTrue(config.isRetryable(429));
        assertTrue(config.isRetryable(500));
        assertTrue(config.isRetryable(502));
        assertTrue(config.isRetryable(503));
        assertTrue(config.isRetryable(504));
    }

    @Test
    void whenCreatingNoRetry_shouldHaveSingleAttempt() {
        final RetryConfig config = RetryConfig.noRetry();

        assertEquals(1, config.maxAttempts());
        assertFalse(config.isRetryable(429));
        assertFalse(config.isRetryable(500));
    }

    @Test
    void whenCheckingRetryable_givenRetryableCode_shouldReturnTrue() {
        final RetryConfig config = new RetryConfig(3, 100, 1000, 2.0, Set.of(429, 500));

        assertTrue(config.isRetryable(429));
        assertTrue(config.isRetryable(500));
    }

    @Test
    void whenCheckingRetryable_givenNonRetryableCode_shouldReturnFalse() {
        final RetryConfig config = new RetryConfig(3, 100, 1000, 2.0, Set.of(429, 500));

        assertFalse(config.isRetryable(400));
        assertFalse(config.isRetryable(404));
        assertFalse(config.isRetryable(401));
    }

    @Test
    void whenCalculatingDelay_givenFirstAttempt_shouldReturnInitialDelay() {
        final RetryConfig config = new RetryConfig(3, 100, 1000, 2.0, Set.of());

        assertEquals(100L, config.calculateDelay(0));
    }

    @Test
    void whenCalculatingDelay_givenSecondAttempt_shouldApplyMultiplier() {
        final RetryConfig config = new RetryConfig(3, 100, 1000, 2.0, Set.of());

        assertEquals(200L, config.calculateDelay(1));
    }

    @Test
    void whenCalculatingDelay_givenThirdAttempt_shouldApplyMultiplierTwice() {
        final RetryConfig config = new RetryConfig(3, 100, 1000, 2.0, Set.of());

        assertEquals(400L, config.calculateDelay(2));
    }

    @Test
    void whenCalculatingDelay_givenExceedsMax_shouldCapAtMaxDelay() {
        final RetryConfig config = new RetryConfig(5, 100, 500, 2.0, Set.of());

        // Attempt 3 would be 100 * 2^3 = 800, but max is 500
        assertEquals(500L, config.calculateDelay(3));
    }

    @Test
    void whenCreating_givenInvalidMaxAttempts_shouldThrowException() {
        assertThrows(IllegalArgumentException.class,
                () -> new RetryConfig(0, 100, 1000, 2.0, Set.of()));
    }

    @Test
    void whenCreating_givenNegativeInitialDelay_shouldThrowException() {
        assertThrows(IllegalArgumentException.class,
                () -> new RetryConfig(3, -1, 1000, 2.0, Set.of()));
    }

    @Test
    void whenCreating_givenMaxDelayLessThanInitial_shouldThrowException() {
        assertThrows(IllegalArgumentException.class,
                () -> new RetryConfig(3, 1000, 100, 2.0, Set.of()));
    }

    @Test
    void whenCreating_givenMultiplierLessThanOne_shouldThrowException() {
        assertThrows(IllegalArgumentException.class,
                () -> new RetryConfig(3, 100, 1000, 0.5, Set.of()));
    }

    @Test
    void whenCreating_givenNullRetryableCodes_shouldUseDefaults() {
        final RetryConfig config = new RetryConfig(3, 100, 1000, 2.0, null);

        assertTrue(config.isRetryable(429));
        assertTrue(config.isRetryable(500));
    }
}
