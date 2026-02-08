package co.fanki.datadog.traceinspector.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for TraceDetail domain record.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class TraceDetailTest {

    @Test
    void whenCreatingTraceDetail_givenValidParams_shouldCreateSuccessfully() {
        final Instant now = Instant.now();
        final SpanDetail span = createSpanDetail("span-1", "service-a", false);

        final TraceDetail trace = new TraceDetail(
                "trace-123",
                "service-a",
                "prod",
                "GET /api/users",
                now,
                100_000_000L,
                List.of(span)
        );

        assertEquals("trace-123", trace.traceId());
        assertEquals("service-a", trace.service());
        assertEquals("prod", trace.env());
        assertEquals("GET /api/users", trace.resourceName());
        assertEquals(now, trace.startTime());
        assertEquals(100_000_000L, trace.duration());
        assertEquals(1, trace.spans().size());
    }

    @Test
    void whenGettingInvolvedServices_givenMultipleServices_shouldReturnUniqueSet() {
        final SpanDetail span1 = createSpanDetail("span-1", "service-a", false);
        final SpanDetail span2 = createSpanDetail("span-2", "service-b", false);
        final SpanDetail span3 = createSpanDetail("span-3", "service-a", false);
        final SpanDetail span4 = createSpanDetail("span-4", "service-c", false);

        final TraceDetail trace = new TraceDetail(
                "trace-123",
                "service-a",
                "prod",
                "GET /api",
                Instant.now(),
                100_000_000L,
                List.of(span1, span2, span3, span4)
        );

        final Set<String> services = trace.involvedServices();

        assertEquals(3, services.size());
        assertTrue(services.contains("service-a"));
        assertTrue(services.contains("service-b"));
        assertTrue(services.contains("service-c"));
    }

    @Test
    void whenGettingInvolvedServices_givenSingleService_shouldReturnSingleElement() {
        final SpanDetail span1 = createSpanDetail("span-1", "only-service", false);
        final SpanDetail span2 = createSpanDetail("span-2", "only-service", true);

        final TraceDetail trace = new TraceDetail(
                "trace-456",
                "only-service",
                "staging",
                "POST /api",
                Instant.now(),
                50_000_000L,
                List.of(span1, span2)
        );

        final Set<String> services = trace.involvedServices();

        assertEquals(1, services.size());
        assertTrue(services.contains("only-service"));
    }

    @Test
    void whenGettingErrorSpans_givenMixedSpans_shouldReturnOnlyErrors() {
        final SpanDetail okSpan1 = createSpanDetail("span-1", "service-a", false);
        final SpanDetail errorSpan = createSpanDetail("span-2", "service-a", true);
        final SpanDetail okSpan2 = createSpanDetail("span-3", "service-b", false);

        final TraceDetail trace = new TraceDetail(
                "trace-789",
                "service-a",
                "prod",
                "GET /api",
                Instant.now(),
                100_000_000L,
                List.of(okSpan1, errorSpan, okSpan2)
        );

        final List<SpanDetail> errors = trace.errorSpans();

        assertEquals(1, errors.size());
        assertEquals("span-2", errors.get(0).spanId());
        assertTrue(errors.get(0).isError());
    }

    @Test
    void whenGettingRootSpan_givenRootExists_shouldReturnRoot() {
        final SpanDetail rootSpan = createRootSpan("root-span", "service-a");
        final SpanDetail childSpan = createSpanDetail("child-span", "service-a", false);

        final TraceDetail trace = new TraceDetail(
                "trace-abc",
                "service-a",
                "prod",
                "GET /api",
                Instant.now(),
                100_000_000L,
                List.of(childSpan, rootSpan)
        );

        final SpanDetail root = trace.rootSpan();

        assertNotNull(root);
        assertEquals("root-span", root.spanId());
        assertTrue(root.isRoot());
    }

    @Test
    void whenGettingRootSpan_givenNoRoot_shouldReturnNull() {
        final SpanDetail span1 = createSpanDetail("span-1", "service-a", false);
        final SpanDetail span2 = createSpanDetail("span-2", "service-a", false);

        final TraceDetail trace = new TraceDetail(
                "trace-xyz",
                "service-a",
                "prod",
                "GET /api",
                Instant.now(),
                100_000_000L,
                List.of(span1, span2)
        );

        final SpanDetail root = trace.rootSpan();

        assertNull(root);
    }

    @Test
    void whenCheckingHasErrors_givenErrors_shouldReturnTrue() {
        final SpanDetail errorSpan = createSpanDetail("span-1", "service-a", true);

        final TraceDetail trace = new TraceDetail(
                "trace-err",
                "service-a",
                "prod",
                "GET /api",
                Instant.now(),
                100_000_000L,
                List.of(errorSpan)
        );

        assertTrue(trace.hasErrors());
    }

    @Test
    void whenCheckingHasErrors_givenNoErrors_shouldReturnFalse() {
        final SpanDetail okSpan = createSpanDetail("span-1", "service-a", false);

        final TraceDetail trace = new TraceDetail(
                "trace-ok",
                "service-a",
                "prod",
                "GET /api",
                Instant.now(),
                100_000_000L,
                List.of(okSpan)
        );

        assertFalse(trace.hasErrors());
    }

    @Test
    void whenFormattingDuration_givenMilliseconds_shouldFormatCorrectly() {
        final TraceDetail trace = new TraceDetail(
                "trace-ms",
                "service-a",
                "prod",
                "GET /api",
                Instant.now(),
                150_000_000L,
                List.of()
        );

        assertEquals("150.00ms", trace.formattedDuration());
    }

    @Test
    void whenFormattingDuration_givenSeconds_shouldFormatCorrectly() {
        final TraceDetail trace = new TraceDetail(
                "trace-s",
                "service-a",
                "prod",
                "GET /api",
                Instant.now(),
                2_500_000_000L,
                List.of()
        );

        assertEquals("2.50s", trace.formattedDuration());
    }

    private SpanDetail createSpanDetail(
            final String spanId,
            final String service,
            final boolean isError
    ) {
        return new SpanDetail(
                spanId,
                "parent-123",
                service,
                "operation",
                "resource",
                Instant.now(),
                10_000_000L,
                isError,
                isError ? "Error occurred" : "",
                isError ? "RuntimeException" : "",
                "",
                Map.of()
        );
    }

    private SpanDetail createRootSpan(final String spanId, final String service) {
        return new SpanDetail(
                spanId,
                null,
                service,
                "http.request",
                "GET /api",
                Instant.now(),
                100_000_000L,
                false,
                "",
                "",
                "",
                Map.of()
        );
    }
}
