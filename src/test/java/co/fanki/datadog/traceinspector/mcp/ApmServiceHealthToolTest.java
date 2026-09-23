package co.fanki.datadog.traceinspector.mcp;

import co.fanki.datadog.traceinspector.application.ApmHealthService;
import co.fanki.datadog.traceinspector.config.DatadogConfig;
import co.fanki.datadog.traceinspector.datadog.FakeApmMetricsClient;
import co.fanki.datadog.traceinspector.domain.ApmMetrics;
import co.fanki.datadog.traceinspector.domain.TimeWindow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for ApmServiceHealthTool.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class ApmServiceHealthToolTest {

    private static final String FROM = "2026-09-23T13:00:00Z";
    private static final String TO = "2026-09-23T14:00:00Z";
    private static final TimeWindow WINDOW = new TimeWindow(Instant.parse(FROM), Instant.parse(TO));

    private FakeApmMetricsClient client;
    private ApmServiceHealthTool tool;

    @BeforeEach
    void setUp() {
        client = new FakeApmMetricsClient();
        final DatadogConfig config = new DatadogConfig("api-key", "app-key", "datadoghq.com", "prod");
        tool = new ApmServiceHealthTool(new ApmHealthService(client), config);
    }

    private static Map<String, Object> args() {
        final Map<String, Object> args = new HashMap<>();
        args.put("service", "payments");
        args.put("from", FROM);
        args.put("to", TO);
        return args;
    }

    @Test
    void whenGettingName_shouldReturnApmServiceHealth() {
        assertEquals("apm.service_health", tool.name());
    }

    @Test
    @SuppressWarnings("unchecked")
    void whenGettingSchema_shouldRequireServiceFromAndTo() {
        assertEquals(List.of("service", "from", "to"), tool.inputSchema().get("required"));
        final Map<String, Object> properties = (Map<String, Object>) tool.inputSchema().get("properties");
        assertTrue(properties.containsKey("operation"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void whenExecuting_givenErrorSpike_shouldReturnDegradedComparison() {
        client.detectedOperation("servlet.request");
        client.metricsFor(WINDOW, new ApmMetrics(1000, 20, 45.0, 310.456, 900.0));
        client.metricsFor(WINDOW.previous(), new ApmMetrics(1000, 10, 42.0, 290.0, 850.0));

        final Map<String, Object> result = tool.execute(args());

        assertEquals(true, result.get("success"));
        assertEquals("prod", result.get("env"));
        assertEquals("servlet.request", result.get("operation"));
        assertEquals("detected", result.get("operationSource"));
        assertEquals(true, result.get("degraded"));
        assertEquals(List.of("error rate 1.00% -> 2.00%"), result.get("signals"));

        final Map<String, Object> window = (Map<String, Object>) result.get("baseline");
        assertEquals("2026-09-23T12:00:00Z", window.get("from"));

        final Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");
        final Map<String, Object> hits = (Map<String, Object>) metrics.get("hits");
        assertEquals(1000L, hits.get("current"));
        final Map<String, Object> p95 = (Map<String, Object>) metrics.get("latencyP95");
        assertEquals(310.46, p95.get("current"));
        final Map<String, Object> errorRate = (Map<String, Object>) metrics.get("errorRate");
        assertEquals(100.0, errorRate.get("deltaPct"));
    }

    @Test
    void whenExecuting_givenOperationArgument_shouldReportProvidedSource() {
        final Map<String, Object> args = args();
        args.put("operation", "next.request");

        final Map<String, Object> result = tool.execute(args);

        assertEquals("next.request", result.get("operation"));
        assertEquals("provided", result.get("operationSource"));
    }

    @Test
    void whenExecuting_givenMissingService_shouldThrowInvalidArguments() {
        final Map<String, Object> args = args();
        args.remove("service");

        final McpToolException e = assertThrows(McpToolException.class, () -> tool.execute(args));

        assertTrue(e.getMessage().contains("Invalid arguments"));
    }

    @Test
    void whenExecuting_givenFromAfterTo_shouldThrowInvalidArguments() {
        final Map<String, Object> args = args();
        args.put("from", TO);
        args.put("to", FROM);

        final McpToolException e = assertThrows(McpToolException.class, () -> tool.execute(args));

        assertTrue(e.getMessage().contains("Invalid arguments"));
    }

    @Test
    void whenExecuting_givenNoDetectableOperation_shouldThrowWithHint() {
        final McpToolException e = assertThrows(McpToolException.class, () -> tool.execute(args()));

        assertTrue(e.getMessage().contains("No entry spans found for service payments"));
    }
}
