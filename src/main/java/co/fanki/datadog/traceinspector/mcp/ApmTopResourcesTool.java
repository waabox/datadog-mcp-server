package co.fanki.datadog.traceinspector.mcp;

import co.fanki.datadog.traceinspector.application.ApmHealthService;
import co.fanki.datadog.traceinspector.config.DatadogConfig;
import co.fanki.datadog.traceinspector.domain.ApmMetrics;
import co.fanki.datadog.traceinspector.domain.ResourceSortCriteria;
import co.fanki.datadog.traceinspector.domain.ResourceStats;
import co.fanki.datadog.traceinspector.domain.TimeWindow;
import co.fanki.datadog.traceinspector.domain.TopResources;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MCP tool that ranks a service's resources (endpoints) by errors, error rate, latency or traffic.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public final class ApmTopResourcesTool implements McpTool {

    private static final String TOOL_NAME = "apm.top_resources";
    private static final String TOOL_DESCRIPTION =
            "Rank a service's resources (endpoints) by errors, error rate, p95 latency or hits "
                    + "using APM trace metrics for a time window";

    private final ApmHealthService healthService;
    private final DatadogConfig config;

    /**
     * Creates a new ApmTopResourcesTool.
     *
     * @param healthService the APM health service
     * @param config the Datadog configuration for defaults
     */
    public ApmTopResourcesTool(final ApmHealthService healthService, final DatadogConfig config) {
        this.healthService = Objects.requireNonNull(healthService, "healthService must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return TOOL_DESCRIPTION;
    }

    @Override
    public Map<String, Object> inputSchema() {
        final Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        final Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("service", Map.of("type", "string", "description", "Service name in Datadog"));
        properties.put("env", Map.of("type", "string", "default", config.defaultEnv(), "description", "Environment"));
        properties.put("from", Map.of("type", "string", "description", "ISO-8601 start timestamp"));
        properties.put("to", Map.of("type", "string", "description", "ISO-8601 end timestamp"));
        properties.put("operation", Map.of(
                "type", "string",
                "description", "APM operation name (e.g. servlet.request, next.request). Detected when omitted"
        ));
        properties.put("sortBy", Map.of(
                "type", "string",
                "enum", List.of("errors", "errorRate", "latencyP95", "hits"),
                "default", "errors",
                "description", "Ranking criteria, descending"
        ));
        properties.put("limit", Map.of(
                "type", "number",
                "default", ResourceSortCriteria.DEFAULT_LIMIT,
                "description", "Max resources to return (1-" + ResourceSortCriteria.MAX_LIMIT + ")"
        ));

        schema.put("properties", properties);
        schema.put("required", List.of("service", "from", "to"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(final Map<String, Object> arguments) {
        Objects.requireNonNull(arguments, "arguments must not be null");

        try {
            final String service = getRequiredString(arguments, "service");
            final String env = getOptionalString(arguments, "env", config.defaultEnv());
            final Instant from = parseTimestamp(getRequiredString(arguments, "from"));
            final Instant to = parseTimestamp(getRequiredString(arguments, "to"));
            final String operation = getOptionalString(arguments, "operation", null);
            final ResourceSortCriteria sortBy = ResourceSortCriteria.fromKey(
                    getOptionalString(arguments, "sortBy", ResourceSortCriteria.ERRORS.key()));
            final int limit = getOptionalInt(arguments, "limit", ResourceSortCriteria.DEFAULT_LIMIT);

            final TopResources top = healthService.topResources(
                    service, env, new TimeWindow(from, to), operation, sortBy, limit);

            return buildSuccessResponse(top);
        } catch (final IllegalArgumentException e) {
            throw new McpToolException(TOOL_NAME, "Invalid arguments: " + e.getMessage(), e);
        } catch (final Exception e) {
            throw new McpToolException(TOOL_NAME, "Failed to get top resources: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildSuccessResponse(final TopResources top) {
        final List<Map<String, Object>> resources = new ArrayList<>();
        for (final ResourceStats stats : top.resources()) {
            final ApmMetrics metrics = stats.metrics();
            final Map<String, Object> map = new LinkedHashMap<>();
            map.put("resource", stats.resource());
            map.put("hits", metrics.hits());
            map.put("errors", metrics.errors());
            map.put("errorRate", round(metrics.errorRate()));
            map.put("latencyP50", round(metrics.latencyP50()));
            map.put("latencyP95", round(metrics.latencyP95()));
            map.put("latencyP99", round(metrics.latencyP99()));
            resources.add(map);
        }

        final Map<String, Object> window = new LinkedHashMap<>();
        window.put("from", top.window().from().toString());
        window.put("to", top.window().to().toString());

        final Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("service", top.service());
        response.put("env", top.env());
        response.put("operation", top.operation().name());
        response.put("operationSource", top.operation().sourceLabel());
        response.put("window", window);
        response.put("sortBy", top.sortBy().key());
        response.put("count", resources.size());
        response.put("resources", resources);
        response.put("notes", top.notes());
        return response;
    }

    private static Double round(final Double value) {
        if (value == null) {
            return null;
        }
        return Math.round(value * 100.0) / 100.0;
    }

    private String getRequiredString(final Map<String, Object> args, final String key) {
        final Object value = args.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        return value.toString();
    }

    private String getOptionalString(final Map<String, Object> args, final String key, final String defaultValue) {
        final Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    private int getOptionalInt(final Map<String, Object> args, final String key, final int defaultValue) {
        final Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private Instant parseTimestamp(final String timestamp) {
        try {
            return Instant.parse(timestamp);
        } catch (final Exception e) {
            throw new IllegalArgumentException("Invalid timestamp format. Expected ISO-8601: " + timestamp);
        }
    }
}
