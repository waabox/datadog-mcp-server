package co.fanki.datadog.traceinspector.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for ApmOperation.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class ApmOperationTest {

    @Test
    void whenCreatingProvided_givenName_shouldHaveProvidedSource() {
        final ApmOperation operation = ApmOperation.provided("servlet.request");

        assertEquals("servlet.request", operation.name());
        assertEquals(ApmOperation.Source.PROVIDED, operation.source());
        assertEquals("provided", operation.sourceLabel());
    }

    @Test
    void whenCreatingDetected_givenName_shouldHaveDetectedLabel() {
        assertEquals("detected", ApmOperation.detected("next.request").sourceLabel());
    }

    @Test
    void whenCreating_givenBlankName_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> ApmOperation.provided("  "));
    }

    @Test
    void whenCreating_givenNullName_shouldThrow() {
        assertThrows(NullPointerException.class, () -> ApmOperation.detected(null));
    }
}
