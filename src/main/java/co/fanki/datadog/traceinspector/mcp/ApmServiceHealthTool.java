package co.fanki.datadog.traceinspector.mcp;

import co.fanki.datadog.traceinspector.application.ApmHealthService;
import co.fanki.datadog.traceinspector.config.DatadogConfig;
import co.fanki.datadog.traceinspector.domain.MetricComparison;
import co.fanki.datadog.traceinspector.domain.ServiceHealth;
import co.fanki.datadog.traceinspector.domain.TimeWindow;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MCP tool that reports a service's APM health compared with the previous window.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public final class ApmServiceHealthTool implements McpTool {

    private static final String TOOL_NAME = "apm.service_health";
    private static final String TOOL_DESCRIPTION =
            "Get APM health (hits, errors, error rate, p50/p95/p99 latency) for a service from trace metrics "
                    + "and compare it with the previous window of the same length. Flags the service as degraded "
                    + "when error rate, p95 latency or traffic change past fixed thresholds";

    private final ApmHealthService healthService;
    private final DatadogConfig config;

    /**
     * Creates a new ApmServiceHealthTool.
     *
     * @param healthService the APM health service
     * @param config the Datadog configuration for defaults
     */
    public ApmServiceHealthTool(final ApmHealthService healthService, final DatadogConfig config) {
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

            final ServiceHealth health = healthService.serviceHealth(service, env, new TimeWindow(from, to), operation);

            return buildSuccessResponse(health);
        } catch (final IllegalArgumentException e) {
            throw new McpToolException(TOOL_NAME, "Invalid arguments: " + e.getMessage(), e);
        } catch (final Exception e) {
            throw new McpToolException(TOOL_NAME, "Failed to get service health: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildSuccessResponse(final ServiceHealth health) {
        final Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("hits", comparison(health.hits(), true));
        metrics.put("errors", comparison(health.errors(), true));
        metrics.put("errorRate", comparison(health.errorRate(), false));
        metrics.put("latencyP50", comparison(health.latencyP50(), false));
        metrics.put("latencyP95", comparison(health.latencyP95(), false));
        metrics.put("latencyP99", comparison(health.latencyP99(), false));

        final Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("service", health.service());
        response.put("env", health.env());
        response.put("operation", health.operation().name());
        response.put("operationSource", health.operation().sourceLabel());
        response.put("window", windowMap(health.window()));
        response.put("baseline", windowMap(health.baselineWindow()));
        response.put("metrics", metrics);
        response.put("degraded", health.isDegraded());
        response.put("signals", health.signals());
        response.put("notes", health.notes());
        return response;
    }

    private static Map<String, Object> windowMap(final TimeWindow window) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("from", window.from().toString());
        map.put("to", window.to().toString());
        return map;
    }

    private static Map<String, Object> comparison(final MetricComparison comparison, final boolean wholeNumber) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("current", format(comparison.current(), wholeNumber));
        map.put("baseline", format(comparison.baseline(), wholeNumber));
        map.put("deltaPct", round(comparison.deltaPct()));
        return map;
    }

    private static Object format(final Double value, final boolean wholeNumber) {
        if (value == null) {
            return null;
        }
        if (wholeNumber) {
            return Math.round(value);
        }
        return round(value);
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

    private Instant parseTimestamp(final String timestamp) {
        try {
            return Instant.parse(timestamp);
        } catch (final Exception e) {
            throw new IllegalArgumentException("Invalid timestamp format. Expected ISO-8601: " + timestamp);
        }
    }
}
