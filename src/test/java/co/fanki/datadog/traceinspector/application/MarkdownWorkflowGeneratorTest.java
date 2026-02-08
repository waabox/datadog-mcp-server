package co.fanki.datadog.traceinspector.application;

import co.fanki.datadog.traceinspector.domain.DiagnosticResult;
import co.fanki.datadog.traceinspector.domain.ServiceErrorView;
import co.fanki.datadog.traceinspector.domain.SpanDetail;
import co.fanki.datadog.traceinspector.domain.TraceDetail;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for MarkdownWorkflowGenerator.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class MarkdownWorkflowGeneratorTest {

    private final MarkdownWorkflowGenerator generator = new MarkdownWorkflowGenerator();

    @Test
    void whenGenerating_givenValidResult_shouldContainHeader() {
        final DiagnosticResult result = createDiagnosticResult();

        final String markdown = generator.generate(result);

        assertNotNull(markdown);
        assertTrue(markdown.contains("# Error Trace Diagnostic Report"));
        assertTrue(markdown.contains("**Trace ID:** `trace-001`"));
        assertTrue(markdown.contains("**Service:** test-service"));
    }

    @Test
    void whenGenerating_givenValidResult_shouldContainSummary() {
        final DiagnosticResult result = createDiagnosticResult();

        final String markdown = generator.generate(result);

        assertTrue(markdown.contains("## Summary"));
        assertTrue(markdown.contains("**Duration:**"));
        assertTrue(markdown.contains("**Total Spans:**"));
        assertTrue(markdown.contains("**Error Spans:**"));
        assertTrue(markdown.contains("**Services Involved:**"));
    }

    @Test
    void whenGenerating_givenValidResult_shouldContainServicesInvolved() {
        final DiagnosticResult result = createDiagnosticResult();

        final String markdown = generator.generate(result);

        assertTrue(markdown.contains("## Services Involved"));
        assertTrue(markdown.contains("**test-service**"));
    }

    @Test
    void whenGenerating_givenErrorSpans_shouldContainErrorDetails() {
        final DiagnosticResult result = createDiagnosticResultWithErrors();

        final String markdown = generator.generate(result);

        assertTrue(markdown.contains("## Error Details"));
        assertTrue(markdown.contains("### error-service"));
        assertTrue(markdown.contains("**Primary Error:**"));
        assertTrue(markdown.contains("**Error Count:**"));
    }

    @Test
    void whenGenerating_givenErrorWithStack_shouldIncludeStackTrace() {
        final DiagnosticResult result = createDiagnosticResultWithStackTrace();

        final String markdown = generator.generate(result);

        assertTrue(markdown.contains("**Stack Trace:**"));
        assertTrue(markdown.contains("```"));
        assertTrue(markdown.contains("NullPointerException"));
    }

    @Test
    void whenGenerating_givenValidResult_shouldContainSpanTimeline() {
        final DiagnosticResult result = createDiagnosticResult();

        final String markdown = generator.generate(result);

        assertTrue(markdown.contains("## Span Timeline"));
        assertTrue(markdown.contains("| Service | Operation | Duration | Status |"));
        assertTrue(markdown.contains("|---------|-----------|----------|--------|"));
    }

    @Test
    void whenGenerating_givenValidResult_shouldContainActionableSteps() {
        final DiagnosticResult result = createDiagnosticResultWithErrors();

        final String markdown = generator.generate(result);

        assertTrue(markdown.contains("## Recommended Actions"));
        assertTrue(markdown.contains("Investigate"));
        assertTrue(markdown.contains("Review recent deployments"));
    }

    @Test
    void whenGenerating_givenValidResult_shouldContainDatadogLinks() {
        final DiagnosticResult result = createDiagnosticResult();

        final String markdown = generator.generate(result);

        assertTrue(markdown.contains("## Datadog Links"));
        assertTrue(markdown.contains("[View Trace in Datadog]"));
        assertTrue(markdown.contains("[Service Dashboard]"));
        assertTrue(markdown.contains("trace-001"));
    }

    @Test
    void whenGenerating_givenDistributedError_shouldIndicateMultipleServices() {
        final DiagnosticResult result = createDistributedErrorResult();

        final String markdown = generator.generate(result);

        assertTrue(markdown.contains("Distributed error"));
        assertTrue(markdown.contains("Check service communication"));
    }

    @Test
    void whenGenerating_givenLogsPresent_shouldIncludeLogsSection() {
        final DiagnosticResult result = createDiagnosticResultWithLogs();

        final String markdown = generator.generate(result);

        assertTrue(markdown.contains("## Related Logs"));
        assertTrue(markdown.contains("Logs"));
    }

    private DiagnosticResult createDiagnosticResult() {
        final SpanDetail span = new SpanDetail(
                "span-1",
                null,
                "test-service",
                "http.request",
                "GET /api",
                Instant.now(),
                50_000_000L,
                false,
                "",
                "",
                "",
                Map.of()
        );

        final TraceDetail trace = new TraceDetail(
                "trace-001",
                "test-service",
                "prod",
                "GET /api",
                Instant.now(),
                100_000_000L,
                List.of(span)
        );

        return DiagnosticResult.of(trace, List.of(), "");
    }

    private DiagnosticResult createDiagnosticResultWithErrors() {
        final SpanDetail errorSpan = new SpanDetail(
                "span-error",
                "parent",
                "error-service",
                "database.query",
                "SELECT * FROM users",
                Instant.now(),
                30_000_000L,
                true,
                "Connection timeout",
                "TimeoutException",
                "",
                Map.of()
        );

        final TraceDetail trace = new TraceDetail(
                "trace-002",
                "error-service",
                "prod",
                "GET /api/users",
                Instant.now(),
                150_000_000L,
                List.of(errorSpan)
        );

        final ServiceErrorView errorView = new ServiceErrorView(
                "error-service",
                List.of(errorSpan),
                List.of(),
                "TimeoutException: Connection timeout",
                Instant.now()
        );

        return DiagnosticResult.of(trace, List.of(errorView), "");
    }

    private DiagnosticResult createDiagnosticResultWithStackTrace() {
        final String stackTrace = "java.lang.NullPointerException: Cannot invoke method\n"
                + "\tat com.example.Service.doSomething(Service.java:42)\n"
                + "\tat com.example.Controller.handle(Controller.java:15)\n"
                + "\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)";

        final SpanDetail errorSpan = new SpanDetail(
                "span-stack",
                "parent",
                "stack-service",
                "service.method",
                "Method.execute",
                Instant.now(),
                20_000_000L,
                true,
                "Cannot invoke method",
                "NullPointerException",
                stackTrace,
                Map.of()
        );

        final TraceDetail trace = new TraceDetail(
                "trace-003",
                "stack-service",
                "prod",
                "POST /api",
                Instant.now(),
                80_000_000L,
                List.of(errorSpan)
        );

        final ServiceErrorView errorView = new ServiceErrorView(
                "stack-service",
                List.of(errorSpan),
                List.of(),
                "NullPointerException: Cannot invoke method",
                Instant.now()
        );

        return DiagnosticResult.of(trace, List.of(errorView), "");
    }

    private DiagnosticResult createDistributedErrorResult() {
        final SpanDetail span1 = new SpanDetail(
                "span-1",
                null,
                "service-a",
                "http.request",
                "GET /api",
                Instant.now(),
                100_000_000L,
                true,
                "Error in service A",
                "ErrorA",
                "",
                Map.of()
        );

        final SpanDetail span2 = new SpanDetail(
                "span-2",
                "span-1",
                "service-b",
                "grpc.call",
                "Service.method",
                Instant.now(),
                50_000_000L,
                true,
                "Error in service B",
                "ErrorB",
                "",
                Map.of()
        );

        final TraceDetail trace = new TraceDetail(
                "trace-distributed",
                "service-a",
                "prod",
                "GET /api",
                Instant.now(),
                150_000_000L,
                List.of(span1, span2)
        );

        final ServiceErrorView errorA = new ServiceErrorView(
                "service-a",
                List.of(span1),
                List.of(),
                "ErrorA: Error in service A",
                Instant.now()
        );

        final ServiceErrorView errorB = new ServiceErrorView(
                "service-b",
                List.of(span2),
                List.of(),
                "ErrorB: Error in service B",
                Instant.now()
        );

        return DiagnosticResult.of(trace, List.of(errorA, errorB), "");
    }

    private DiagnosticResult createDiagnosticResultWithLogs() {
        final SpanDetail errorSpan = new SpanDetail(
                "span-log",
                null,
                "log-service",
                "operation",
                "Resource",
                Instant.now(),
                25_000_000L,
                true,
                "Something failed",
                "FailureException",
                "",
                Map.of()
        );

        final TraceDetail trace = new TraceDetail(
                "trace-logs",
                "log-service",
                "prod",
                "POST /api",
                Instant.now(),
                100_000_000L,
                List.of(errorSpan)
        );

        final ServiceErrorView.LogEntry logEntry = new ServiceErrorView.LogEntry(
                Instant.now(),
                "ERROR",
                "Detailed error log message with context",
                Map.of("key", "value")
        );

        final ServiceErrorView errorView = new ServiceErrorView(
                "log-service",
                List.of(errorSpan),
                List.of(logEntry),
                "FailureException: Something failed",
                Instant.now()
        );

        return DiagnosticResult.of(trace, List.of(errorView), "");
    }
}
