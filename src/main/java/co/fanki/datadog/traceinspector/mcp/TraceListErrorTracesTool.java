package co.fanki.datadog.traceinspector.mcp;

import co.fanki.datadog.traceinspector.application.TraceDiagnosticService;
import co.fanki.datadog.traceinspector.config.DatadogConfig;
import co.fanki.datadog.traceinspector.domain.TraceQuery;
import co.fanki.datadog.traceinspector.domain.TraceSummary;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MCP tool for listing error traces from Datadog.
 *
 * <p>This tool searches for error traces matching the specified service,
 * environment, and time range.</p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public final class TraceListErrorTracesTool implements McpTool {

    private static final String TOOL_NAME = "trace.list_error_traces";
    private static final String TOOL_DESCRIPTION =
            "List error traces for a service within a time window";

    private final TraceDiagnosticService diagnosticService;
    private final DatadogConfig config;

    /**
     * Creates a new TraceListErrorTracesTool.
     *
     * @param diagnosticService the diagnostic service for trace operations
     * @param config the Datadog configuration for defaults
     */
    public TraceListErrorTracesTool(
            final TraceDiagnosticService diagnosticService,
            final DatadogConfig config
    ) {
        this.diagnosticService = Objects.requireNonNull(
                diagnosticService, "diagnosticService must not be null"
        );
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

        properties.put("service", Map.of(
                "type", "string",
                "description", "Service name in Datadog"
        ));

        properties.put("env", Map.of(
                "type", "string",
                "default", "prod",
                "description", "Environment"
        ));

        properties.put("from", Map.of(
                "type", "string",
                "description", "ISO-8601 start timestamp"
        ));

        properties.put("to", Map.of(
                "type", "string",
                "description", "ISO-8601 end timestamp"
        ));

        properties.put("limit", Map.of(
                "type", "number",
                "default", 20,
                "description", "Max traces to return"
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
            final int limit = getOptionalInt(arguments, "limit", 20);

            final TraceQuery query = new TraceQuery(service, env, from, to, limit);
            final List<TraceSummary> traces = diagnosticService.listErrorTraces(query);

            return buildSuccessResponse(traces);
        } catch (final IllegalArgumentException e) {
            throw new McpToolException(TOOL_NAME, "Invalid arguments: " + e.getMessage(), e);
        } catch (final Exception e) {
            throw new McpToolException(TOOL_NAME, "Failed to list traces: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildSuccessResponse(final List<TraceSummary> traces) {
        final List<Map<String, Object>> traceList = new ArrayList<>();

        for (final TraceSummary trace : traces) {
            final Map<String, Object> traceMap = new HashMap<>();
            traceMap.put("traceId", trace.traceId());
            traceMap.put("service", trace.service());
            traceMap.put("resourceName", trace.resourceName());
            traceMap.put("errorMessage", trace.errorMessage());
            traceMap.put("timestamp", trace.timestamp().toString());
            traceMap.put("duration", trace.formattedDuration());
            traceList.add(traceMap);
        }

        return Map.of(
                "success", true,
                "count", traces.size(),
                "traces", traceList
        );
    }

    private String getRequiredString(
            final Map<String, Object> args,
            final String key
    ) {
        final Object value = args.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        return value.toString();
    }

    private String getOptionalString(
            final Map<String, Object> args,
            final String key,
            final String defaultValue
    ) {
        final Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    private int getOptionalInt(
            final Map<String, Object> args,
            final String key,
            final int defaultValue
    ) {
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
            throw new IllegalArgumentException(
                    "Invalid timestamp format. Expected ISO-8601: " + timestamp
            );
        }
    }
}
