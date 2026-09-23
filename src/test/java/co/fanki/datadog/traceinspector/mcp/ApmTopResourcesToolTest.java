package co.fanki.datadog.traceinspector.mcp;

import co.fanki.datadog.traceinspector.application.ApmHealthService;
import co.fanki.datadog.traceinspector.config.DatadogConfig;
import co.fanki.datadog.traceinspector.datadog.FakeApmMetricsClient;
import co.fanki.datadog.traceinspector.domain.ApmMetrics;
import co.fanki.datadog.traceinspector.domain.ResourceStats;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for ApmTopResourcesTool.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class ApmTopResourcesToolTest {

    private FakeApmMetricsClient client;
    private ApmTopResourcesTool tool;

    @BeforeEach
    void setUp() {
        client = new FakeApmMetricsClient();
        client.detectedOperation("servlet.request");
        client.resources(List.of(
                new ResourceStats("GET /a", new ApmMetrics(100, 1, 10.0, 20.0, 30.0)),
                new ResourceStats("POST /b", new ApmMetrics(4200, 310, 120.0, 850.0, 2100.0)),
                new ResourceStats("GET /c", new ApmMetrics(500, 3, null, null, null))));
        final DatadogConfig config = new DatadogConfig("api-key", "app-key", "datadoghq.com", "prod");
        tool = new ApmTopResourcesTool(new ApmHealthService(client), config);
    }

    private static Map<String, Object> args() {
        final Map<String, Object> args = new HashMap<>();
        args.put("service", "payments");
        args.put("from", "2026-09-23T13:00:00Z");
        args.put("to", "2026-09-23T14:00:00Z");
        return args;
    }

    @Test
    void whenGettingName_shouldReturnApmTopResources() {
        assertEquals("apm.top_resources", tool.name());
    }

    @Test
    @SuppressWarnings("unchecked")
    void whenExecuting_givenDefaults_shouldRankByErrors() {
        final Map<String, Object> result = tool.execute(args());

        assertEquals(true, result.get("success"));
        assertEquals("errors", result.get("sortBy"));
        assertEquals(3, result.get("count"));

        final List<Map<String, Object>> resources = (List<Map<String, Object>>) result.get("resources");
        assertEquals("POST /b", resources.get(0).get("resource"));
        assertEquals(310L, resources.get(0).get("errors"));
        assertEquals(7.38, resources.get(0).get("errorRate"));
        assertEquals(850.0, resources.get(0).get("latencyP95"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void whenExecuting_givenSortByLatencyAndLimit_shouldApplyBoth() {
        final Map<String, Object> args = args();
        args.put("sortBy", "latencyP95");
        args.put("limit", 2);

        final Map<String, Object> result = tool.execute(args);

        final List<Map<String, Object>> resources = (List<Map<String, Object>>) result.get("resources");
        assertEquals(2, resources.size());
        assertEquals("POST /b", resources.get(0).get("resource"));
        assertEquals("GET /a", resources.get(1).get("resource"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void whenExecuting_givenResourceWithoutLatency_shouldReturnNullLatency() {
        final Map<String, Object> args = args();
        args.put("sortBy", "hits");

        final Map<String, Object> result = tool.execute(args);

        final List<Map<String, Object>> resources = (List<Map<String, Object>>) result.get("resources");
        assertEquals("GET /c", resources.get(1).get("resource"));
        assertNull(resources.get(1).get("latencyP95"));
    }

    @Test
    void whenExecuting_givenUnknownSortBy_shouldThrowInvalidArguments() {
        final Map<String, Object> args = args();
        args.put("sortBy", "bogus");

        final McpToolException e = assertThrows(McpToolException.class, () -> tool.execute(args));

        assertTrue(e.getMessage().contains("Invalid arguments"));
    }

    @Test
    void whenExecuting_givenLimitAboveMax_shouldThrowInvalidArguments() {
        final Map<String, Object> args = args();
        args.put("limit", 51);

        final McpToolException e = assertThrows(McpToolException.class, () -> tool.execute(args));

        assertTrue(e.getMessage().contains("Invalid arguments"));
    }
}
