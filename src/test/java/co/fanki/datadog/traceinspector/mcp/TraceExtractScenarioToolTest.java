package co.fanki.datadog.traceinspector.mcp;

import co.fanki.datadog.traceinspector.application.TraceScenarioExtractor;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for TraceExtractScenarioTool.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class TraceExtractScenarioToolTest {

    private TestDatadogClient datadogClient;
    private TraceScenarioExtractor extractor;
    private DatadogConfig config;
    private TraceExtractScenarioTool tool;

    @BeforeEach
    void setUp() {
        datadogClient = new TestDatadogClient();
        extractor = new TraceScenarioExtractor();
        config = new DatadogConfig("api-key", "app-key", "datadoghq.com", "prod");
        tool = new TraceExtractScenarioTool(datadogClient, extractor, config);
    }

    @Test
    void whenGettingName_shouldReturnTraceExtractScenario() {
        assertEquals("trace.extract_scenario", tool.name());
    }

    @Test
    void whenGettingDescription_shouldReturnDescription() {
        assertTrue(tool.description().contains("scenario"));
    }

    @Test
    void whenGettingInputSchema_shouldReturnValidSchema() {
        final Map<String, Object> schema = tool.inputSchema();

        assertNotNull(schema);
        assertEquals("object", schema.get("type"));

        @SuppressWarnings("unchecked")
        final Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("traceId"));
        assertTrue(properties.containsKey("service"));
        assertTrue(properties.containsKey("from"));
        assertTrue(properties.containsKey("to"));
        assertTrue(properties.containsKey("env"));

        @SuppressWarnings("unchecked")
        final List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("traceId"));
        assertTrue(required.contains("service"));
        assertTrue(required.contains("from"));
        assertTrue(required.contains("to"));
    }

    @Test
    void whenExecuting_givenValidTrace_shouldReturnScenario() {
        final Instant now = Instant.parse("2026-02-08T17:00:00Z");
        final String stackTrace = """
                java.lang.RuntimeException: Error
                    at com.example.Service.method(Service.java:42)
                """;

        final SpanDetail span = new SpanDetail(
                "span1", null, "order-service", "OrderController.create",
                "POST /api/orders", now, 100_000_000L, true,
                "Order failed", "RuntimeException", stackTrace,
                Map.of(
                        "http.method", "POST",
                        "http.url", "/api/orders",
                        "user_id", "123"
                )
        );

        datadogClient.setTraceDetail(new TraceDetail(
                "trace123", "order-service", "prod", "POST /api/orders",
                now, 100_000_000L, List.of(span)
        ));
        datadogClient.setLogsForTrace(List.of());

        final Map<String, Object> arguments = new HashMap<>();
        arguments.put("traceId", "trace123");
        arguments.put("service", "order-service");
        arguments.put("from", "2026-02-08T17:00:00Z");
        arguments.put("to", "2026-02-08T18:00:00Z");

        final Map<String, Object> result = tool.execute(arguments);

        assertEquals(true, result.get("success"));
        assertEquals("trace123", result.get("traceId"));
        assertEquals(1, result.get("stepCount"));

        // Check entry point
        @SuppressWarnings("unchecked")
        final Map<String, Object> entryPoint = (Map<String, Object>) result.get("entryPoint");
        assertNotNull(entryPoint);
        assertEquals("POST", entryPoint.get("method"));
        assertEquals("/api/orders", entryPoint.get("path"));

        // Check error context
        @SuppressWarnings("unchecked")
        final Map<String, Object> errorContext = (Map<String, Object>) result.get("errorContext");
        assertNotNull(errorContext);
        assertEquals("RuntimeException", errorContext.get("exceptionType"));
        assertEquals("Order failed", errorContext.get("message"));

        // Check location
        @SuppressWarnings("unchecked")
        final Map<String, Object> location = (Map<String, Object>) errorContext.get("location");
        assertNotNull(location);
        assertEquals("Service.java", location.get("fileName"));
        assertEquals(42, location.get("lineNumber"));

        // Check execution flow
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> flow = (List<Map<String, Object>>) result.get("executionFlow");
        assertNotNull(flow);
        assertEquals(1, flow.size());
        assertEquals("order-service", flow.get(0).get("service"));

        // Check involved services
        @SuppressWarnings("unchecked")
        final List<String> services = (List<String>) result.get("involvedServices");
        assertTrue(services.contains("order-service"));

        // Check relevant data
        @SuppressWarnings("unchecked")
        final Map<String, String> relevantData = (Map<String, String>) result.get("relevantData");
        assertNotNull(relevantData);
        assertEquals("123", relevantData.get("user_id"));

        // Check suggested test scenario
        @SuppressWarnings("unchecked")
        final Map<String, String> testScenario = (Map<String, String>) result.get("suggestedTestScenario");
        assertNotNull(testScenario);
        assertTrue(testScenario.containsKey("given"));
        assertTrue(testScenario.containsKey("when"));
        assertTrue(testScenario.containsKey("then"));
    }

    @Test
    void whenExecuting_givenMissingTraceId_shouldThrowException() {
        final Map<String, Object> arguments = new HashMap<>();
        arguments.put("service", "order-service");
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
        arguments.put("traceId", "trace123");
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
        arguments.put("traceId", "trace123");
        arguments.put("service", "order-service");
        arguments.put("from", "invalid");
        arguments.put("to", "2026-02-08T18:00:00Z");

        final McpToolException exception = assertThrows(
                McpToolException.class,
                () -> tool.execute(arguments)
        );

        assertTrue(exception.getMessage().contains("timestamp"));
    }

    @Test
    void whenExecuting_givenNoError_shouldNotIncludeErrorContext() {
        final Instant now = Instant.parse("2026-02-08T17:00:00Z");

        final SpanDetail span = new SpanDetail(
                "span1", null, "order-service", "process",
                "process", now, 100_000_000L, false,
                "", "", "", Map.of()
        );

        datadogClient.setTraceDetail(new TraceDetail(
                "trace123", "order-service", "prod", "process",
                now, 100_000_000L, List.of(span)
        ));
        datadogClient.setLogsForTrace(List.of());

        final Map<String, Object> arguments = new HashMap<>();
        arguments.put("traceId", "trace123");
        arguments.put("service", "order-service");
        arguments.put("from", "2026-02-08T17:00:00Z");
        arguments.put("to", "2026-02-08T18:00:00Z");

        final Map<String, Object> result = tool.execute(arguments);

        assertEquals(true, result.get("success"));
        assertFalse(result.containsKey("errorContext"));
        assertFalse(result.containsKey("suggestedTestScenario"));
    }

    @Test
    void whenCreatingTool_givenNullClient_shouldThrowException() {
        assertThrows(
                NullPointerException.class,
                () -> new TraceExtractScenarioTool(null, extractor, config)
        );
    }

    @Test
    void whenCreatingTool_givenNullExtractor_shouldThrowException() {
        assertThrows(
                NullPointerException.class,
                () -> new TraceExtractScenarioTool(datadogClient, null, config)
        );
    }

    @Test
    void whenCreatingTool_givenNullConfig_shouldThrowException() {
        assertThrows(
                NullPointerException.class,
                () -> new TraceExtractScenarioTool(datadogClient, extractor, null)
        );
    }

    /**
     * Test implementation of DatadogClient.
     */
    private static class TestDatadogClient implements DatadogClient {

        private TraceDetail traceDetail;
        private List<ServiceErrorView.LogEntry> logsForTrace = List.of();

        void setTraceDetail(final TraceDetail traceDetail) {
            this.traceDetail = traceDetail;
        }

        void setLogsForTrace(final List<ServiceErrorView.LogEntry> logs) {
            this.logsForTrace = logs;
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
            return traceDetail;
        }

        @Override
        public List<ServiceErrorView.LogEntry> searchLogsForTrace(
                final String traceId,
                final TraceQuery query
        ) {
            return logsForTrace;
        }

        @Override
        public List<LogSummary> searchLogs(final LogQuery query) {
            return List.of();
        }
    }
}
