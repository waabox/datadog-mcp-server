package co.fanki.datadog.traceinspector.mcp;

import co.fanki.datadog.traceinspector.config.DatadogConfig;
import co.fanki.datadog.traceinspector.datadog.DatadogClient;
import co.fanki.datadog.traceinspector.domain.LogQuery;
import co.fanki.datadog.traceinspector.domain.LogSummary;
import co.fanki.datadog.traceinspector.domain.ServiceErrorView;
import co.fanki.datadog.traceinspector.domain.SpanDetail;
import co.fanki.datadog.traceinspector.domain.TraceDetail;
import co.fanki.datadog.traceinspector.domain.TraceQuery;
import co.fanki.datadog.traceinspector.domain.TraceSummary;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for LogCorrelateTool.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class LogCorrelateToolTest {

    private TestDatadogClient datadogClient;
    private DatadogConfig config;
    private LogCorrelateTool tool;

    @BeforeEach
    void setUp() {
        datadogClient = new TestDatadogClient();
        config = new DatadogConfig("api-key", "app-key", "datadoghq.com", "prod");
        tool = new LogCorrelateTool(datadogClient, config);
    }

    @Test
    void whenGettingName_shouldReturnLogCorrelate() {
        assertEquals("log.correlate", tool.name());
    }

    @Test
    void whenGettingDescription_shouldReturnDescription() {
        assertEquals("Correlate logs and traces by trace ID", tool.description());
    }

    @Test
    void whenGettingInputSchema_shouldReturnValidSchema() {
        final Map<String, Object> schema = tool.inputSchema();

        assertNotNull(schema);
        assertEquals("object", schema.get("type"));

        @SuppressWarnings("unchecked")
        final Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertNotNull(properties);
        assertTrue(properties.containsKey("traceId"));
        assertTrue(properties.containsKey("service"));
        assertTrue(properties.containsKey("env"));
        assertTrue(properties.containsKey("from"));
        assertTrue(properties.containsKey("to"));
        assertTrue(properties.containsKey("includeTrace"));

        @SuppressWarnings("unchecked")
        final List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("traceId"));
        assertTrue(required.contains("service"));
        assertTrue(required.contains("from"));
        assertTrue(required.contains("to"));
    }

    @Test
    void whenExecuting_givenValidArgumentsWithTrace_shouldReturnCorrelatedData() {
        final String traceId = "abc123";
        final String service = "ticket-service";
        final String env = "prod";
        final Instant from = Instant.parse("2026-02-08T17:00:00Z");

        final SpanDetail rootSpan = new SpanDetail(
                "span1", null, service, "markTicketsAsInCart",
                "TicketController.markTicketsAsInCart", from, 542_000_000L,
                true, "Ticket not available", "RuntimeException", "", Map.of()
        );

        datadogClient.setTraceDetail(new TraceDetail(
                traceId, service, env, "TicketController.markTicketsAsInCart",
                from, 542_000_000L, List.of(rootSpan)
        ));

        datadogClient.setLogsForTrace(List.of(
                new ServiceErrorView.LogEntry(
                        from.plusSeconds(1), "ERROR",
                        "The following 1 tickets could not be fulfilled...",
                        Map.of("host", "prod-01")
                )
        ));

        final Map<String, Object> arguments = new HashMap<>();
        arguments.put("traceId", traceId);
        arguments.put("service", service);
        arguments.put("env", env);
        arguments.put("from", "2026-02-08T17:00:00Z");
        arguments.put("to", "2026-02-08T18:00:00Z");
        arguments.put("includeTrace", true);

        final Map<String, Object> result = tool.execute(arguments);

        assertEquals(true, result.get("success"));
        assertEquals(traceId, result.get("traceId"));
        assertEquals(1, result.get("logCount"));

        @SuppressWarnings("unchecked")
        final Map<String, Object> trace = (Map<String, Object>) result.get("trace");
        assertNotNull(trace);
        assertEquals(service, trace.get("service"));
        assertEquals("TicketController.markTicketsAsInCart", trace.get("resourceName"));
        assertEquals("542.00ms", trace.get("duration"));
        assertEquals(1, trace.get("spanCount"));
        assertEquals(true, trace.get("hasErrors"));

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> logs = (List<Map<String, Object>>) result.get("logs");
        assertNotNull(logs);
        assertEquals(1, logs.size());
        assertEquals("ERROR", logs.get(0).get("level"));
        assertTrue(logs.get(0).get("message").toString().contains("tickets could not be fulfilled"));
    }

    @Test
    void whenExecuting_givenIncludeTraceFalse_shouldSkipTraceDetail() {
        final String traceId = "abc123";
        final Instant from = Instant.parse("2026-02-08T17:00:00Z");

        datadogClient.setLogsForTrace(List.of(
                new ServiceErrorView.LogEntry(from.plusSeconds(1), "ERROR", "Error message", Map.of())
        ));

        final Map<String, Object> arguments = new HashMap<>();
        arguments.put("traceId", traceId);
        arguments.put("service", "ticket-service");
        arguments.put("from", "2026-02-08T17:00:00Z");
        arguments.put("to", "2026-02-08T18:00:00Z");
        arguments.put("includeTrace", false);

        final Map<String, Object> result = tool.execute(arguments);

        assertEquals(true, result.get("success"));
        assertEquals(traceId, result.get("traceId"));
        assertFalse(result.containsKey("trace"));
        assertEquals(1, result.get("logCount"));
        assertFalse(datadogClient.wasGetTraceDetailCalled());
    }

    @Test
    void whenExecuting_givenDefaultEnv_shouldUseConfigDefault() {
        final Map<String, Object> arguments = new HashMap<>();
        arguments.put("traceId", "abc123");
        arguments.put("service", "ticket-service");
        arguments.put("from", "2026-02-08T17:00:00Z");
        arguments.put("to", "2026-02-08T18:00:00Z");

        datadogClient.setLogsForTrace(List.of());

        tool.execute(arguments);

        assertEquals("prod", datadogClient.getLastEnvUsed());
    }

    @Test
    void whenExecuting_givenMissingTraceId_shouldThrowException() {
        final Map<String, Object> arguments = new HashMap<>();
        arguments.put("service", "ticket-service");
        arguments.put("from", "2026-02-08T17:00:00Z");
        arguments.put("to", "2026-02-08T18:00:00Z");

        final McpToolException exception = assertThrows(
                McpToolException.class,
                () -> tool.execute(arguments)
        );

        assertTrue(exception.getMessage().contains("traceId"));
    }

    @Test
    void whenExecuting_givenMissingService_shouldThrowException() {
        final Map<String, Object> arguments = new HashMap<>();
        arguments.put("traceId", "abc123");
        arguments.put("from", "2026-02-08T17:00:00Z");
        arguments.put("to", "2026-02-08T18:00:00Z");

        final McpToolException exception = assertThrows(
                McpToolException.class,
                () -> tool.execute(arguments)
        );

        assertTrue(exception.getMessage().contains("service"));
    }

    @Test
    void whenExecuting_givenInvalidTimestamp_shouldThrowException() {
        final Map<String, Object> arguments = new HashMap<>();
        arguments.put("traceId", "abc123");
        arguments.put("service", "ticket-service");
        arguments.put("from", "invalid-timestamp");
        arguments.put("to", "2026-02-08T18:00:00Z");

        final McpToolException exception = assertThrows(
                McpToolException.class,
                () -> tool.execute(arguments)
        );

        assertTrue(exception.getMessage().contains("Invalid timestamp"));
    }

    @Test
    void whenExecuting_givenNullArguments_shouldThrowException() {
        assertThrows(NullPointerException.class, () -> tool.execute(null));
    }

    @Test
    void whenExecuting_givenEmptyLogs_shouldReturnEmptyList() {
        datadogClient.setLogsForTrace(List.of());

        final Map<String, Object> arguments = new HashMap<>();
        arguments.put("traceId", "abc123");
        arguments.put("service", "ticket-service");
        arguments.put("from", "2026-02-08T17:00:00Z");
        arguments.put("to", "2026-02-08T18:00:00Z");

        final Map<String, Object> result = tool.execute(arguments);

        assertEquals(true, result.get("success"));
        assertEquals(0, result.get("logCount"));

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> logs = (List<Map<String, Object>>) result.get("logs");
        assertNotNull(logs);
        assertTrue(logs.isEmpty());
    }

    @Test
    void whenCreatingTool_givenNullClient_shouldThrowException() {
        assertThrows(
                NullPointerException.class,
                () -> new LogCorrelateTool(null, config)
        );
    }

    @Test
    void whenCreatingTool_givenNullConfig_shouldThrowException() {
        assertThrows(
                NullPointerException.class,
                () -> new LogCorrelateTool(datadogClient, null)
        );
    }

    @Test
    void whenExecuting_givenLogWithAttributes_shouldIncludeAttributes() {
        final Instant from = Instant.parse("2026-02-08T17:00:00Z");

        datadogClient.setLogsForTrace(List.of(
                new ServiceErrorView.LogEntry(
                        from, "ERROR", "Error with attributes",
                        Map.of("host", "prod-01", "trace_id", "abc123")
                )
        ));

        final Map<String, Object> arguments = new HashMap<>();
        arguments.put("traceId", "abc123");
        arguments.put("service", "ticket-service");
        arguments.put("from", "2026-02-08T17:00:00Z");
        arguments.put("to", "2026-02-08T18:00:00Z");
        arguments.put("includeTrace", false);

        final Map<String, Object> result = tool.execute(arguments);

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> logs = (List<Map<String, Object>>) result.get("logs");
        assertNotNull(logs.get(0).get("attributes"));
    }

    @Test
    void whenExecuting_givenLogWithoutAttributes_shouldNotIncludeAttributes() {
        final Instant from = Instant.parse("2026-02-08T17:00:00Z");

        datadogClient.setLogsForTrace(List.of(
                new ServiceErrorView.LogEntry(from, "ERROR", "Error without attributes", Map.of())
        ));

        final Map<String, Object> arguments = new HashMap<>();
        arguments.put("traceId", "abc123");
        arguments.put("service", "ticket-service");
        arguments.put("from", "2026-02-08T17:00:00Z");
        arguments.put("to", "2026-02-08T18:00:00Z");
        arguments.put("includeTrace", false);

        final Map<String, Object> result = tool.execute(arguments);

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> logs = (List<Map<String, Object>>) result.get("logs");
        assertFalse(logs.get(0).containsKey("attributes"));
    }

    /**
     * Test implementation of DatadogClient for unit testing.
     */
    private static class TestDatadogClient implements DatadogClient {

        private TraceDetail traceDetail;
        private List<ServiceErrorView.LogEntry> logsForTrace = new ArrayList<>();
        private boolean getTraceDetailCalled = false;
        private String lastEnvUsed;

        void setTraceDetail(final TraceDetail traceDetail) {
            this.traceDetail = traceDetail;
        }

        void setLogsForTrace(final List<ServiceErrorView.LogEntry> logs) {
            this.logsForTrace = logs;
        }

        boolean wasGetTraceDetailCalled() {
            return getTraceDetailCalled;
        }

        String getLastEnvUsed() {
            return lastEnvUsed;
        }

        @Override
        public List<TraceSummary> searchErrorTraces(final TraceQuery query) {
            return List.of();
        }

        @Override
        public TraceDetail getTraceDetail(
                final String traceId,
                final String service,
                final String env,
                final Instant from,
                final Instant to
        ) {
            getTraceDetailCalled = true;
            lastEnvUsed = env;
            return traceDetail;
        }

        @Override
        public List<ServiceErrorView.LogEntry> searchLogsForTrace(
                final String traceId,
                final TraceQuery query
        ) {
            lastEnvUsed = query.env();
            return logsForTrace;
        }

        @Override
        public List<LogSummary> searchLogs(final LogQuery query) {
            return List.of();
        }
    }
}
