package co.fanki.datadog.traceinspector.mcp;

import co.fanki.datadog.traceinspector.application.TraceDiagnosticService;
import co.fanki.datadog.traceinspector.config.DatadogConfig;
import co.fanki.datadog.traceinspector.domain.DiagnosticResult;
import co.fanki.datadog.traceinspector.domain.TraceQuery;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MCP tool for inspecting a specific error trace.
 *
 * <p>This tool retrieves detailed information about an error trace,
 * including all spans, associated logs, and generates an actionable
 * diagnostic workflow.</p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public final class TraceInspectErrorTraceTool implements McpTool {

    private static final String TOOL_NAME = "trace.inspect_error_trace";
    private static final String TOOL_DESCRIPTION =
            "Inspect a specific error trace and generate diagnostic workflow";

    private final TraceDiagnosticService diagnosticService;
    private final DatadogConfig config;

    /**
     * Creates a new TraceInspectErrorTraceTool.
     *
     * @param diagnosticService the diagnostic service for trace operations
     * @param config the Datadog configuration for defaults
     */
    public TraceInspectErrorTraceTool(
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

        properties.put("traceId", Map.of(
                "type", "string",
                "description", "Trace ID to inspect"
        ));

        schema.put("properties", properties);
        schema.put("required", List.of("service", "from", "to", "traceId"));

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
            final String traceId = getRequiredString(arguments, "traceId");

            final TraceQuery query = TraceQuery.withDefaultLimit(service, env, from, to);
            final DiagnosticResult result = diagnosticService.inspectErrorTrace(traceId, query);

            return buildSuccessResponse(result);
        } catch (final IllegalArgumentException e) {
            throw new McpToolException(TOOL_NAME, "Invalid arguments: " + e.getMessage(), e);
        } catch (final Exception e) {
            throw new McpToolException(
                    TOOL_NAME, "Failed to inspect trace: " + e.getMessage(), e
            );
        }
    }

    private Map<String, Object> buildSuccessResponse(final DiagnosticResult result) {
        final Map<String, Object> response = new LinkedHashMap<>();

        response.put("success", true);
        response.put("traceId", result.traceId());
        response.put("service", result.service());
        response.put("involvedServices", new ArrayList<>(result.involvedServices()));
        response.put("totalErrors", result.totalErrorCount());
        response.put("isDistributedError", result.isDistributedError());

        // Trace summary
        final Map<String, Object> traceSummary = new LinkedHashMap<>();
        traceSummary.put("duration", result.traceDetail().formattedDuration());
        traceSummary.put("spanCount", result.traceDetail().spanCount());
        traceSummary.put("errorSpanCount", result.traceDetail().errorSpans().size());
        traceSummary.put("startTime", result.traceDetail().startTime().toString());
        response.put("traceSummary", traceSummary);

        // Service errors
        final List<Map<String, Object>> serviceErrors = new ArrayList<>();
        for (final var serviceError : result.serviceErrors()) {
            final Map<String, Object> errorMap = new LinkedHashMap<>();
            errorMap.put("serviceName", serviceError.serviceName());
            errorMap.put("errorCount", serviceError.errorCount());
            errorMap.put("primaryError", serviceError.primaryError());
            errorMap.put("errorTypes", serviceError.uniqueErrorTypes());
            serviceErrors.add(errorMap);
        }
        response.put("serviceErrors", serviceErrors);

        // The diagnostic workflow markdown
        response.put("workflow", result.workflow());

        return response;
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
