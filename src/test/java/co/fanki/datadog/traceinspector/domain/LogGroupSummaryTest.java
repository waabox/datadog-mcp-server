package co.fanki.datadog.traceinspector.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link LogGroupSummary}.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class LogGroupSummaryTest {

    @Test
    void extractPattern_whenMessageContainsUuid_shouldReplaceWithPlaceholder() {
        String message = "Error processing order 550e8400-e29b-41d4-a716-446655440000";
        String pattern = LogGroupSummary.extractPattern(message);
        assertEquals("Error processing order <UUID>", pattern);
    }

    @Test
    void extractPattern_whenMessageContainsNumericId_shouldReplaceWithPlaceholder() {
        String message = "User 1234567 not found";
        String pattern = LogGroupSummary.extractPattern(message);
        assertEquals("User <ID> not found", pattern);
    }

    @Test
    void extractPattern_whenMessageContainsTimestamp_shouldReplaceWithPlaceholder() {
        String message = "Request at 2026-02-08T17:08:53.851Z failed";
        String pattern = LogGroupSummary.extractPattern(message);
        assertEquals("Request at <TS> failed", pattern);
    }

    @Test
    void extractPattern_whenMessageContainsIpAddress_shouldReplaceWithPlaceholder() {
        String message = "Connection from 192.168.1.100 refused";
        String pattern = LogGroupSummary.extractPattern(message);
        assertEquals("Connection from <IP> refused", pattern);
    }

    @Test
    void extractPattern_whenMessageContainsHexString_shouldReplaceWithPlaceholder() {
        String message = "Trace ID: 6988c3250000000030be7e29fce205a0";
        String pattern = LogGroupSummary.extractPattern(message);
        assertEquals("Trace ID: <HEX>", pattern);
    }

    @Test
    void extractPattern_whenMessageIsLong_shouldTruncate() {
        // Use 'X' which is not a hex character to avoid hex replacement
        String message = "This is a very long message that should be truncated ".repeat(5);
        String pattern = LogGroupSummary.extractPattern(message);
        assertEquals(80, pattern.length()); // 77 + "..."
        assertTrue(pattern.endsWith("..."));
    }

    @Test
    void extractPattern_whenMessageIsNull_shouldReturnEmpty() {
        String pattern = LogGroupSummary.extractPattern(null);
        assertEquals("[empty]", pattern);
    }

    @Test
    void extractPattern_whenMessageIsBlank_shouldReturnEmpty() {
        String pattern = LogGroupSummary.extractPattern("   ");
        assertEquals("[empty]", pattern);
    }

    @Test
    void extractPattern_whenRealLogMessage_shouldNormalizeCorrectly() {
        String message = "El cart para release es: fanID 851209,country MEX, "
                + "whitelabel fanki, operatorId 851209, asisted false";
        String pattern = LogGroupSummary.extractPattern(message);
        // Message is 94 chars after ID replacement, so it gets truncated to 80
        assertTrue(pattern.startsWith("El cart para release es: fanID <ID>,country MEX"));
        assertTrue(pattern.contains("<ID>"));
    }
}
