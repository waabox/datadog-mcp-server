package co.fanki.datadog.traceinspector.mcp;

import co.fanki.datadog.traceinspector.application.TraceScenarioExtractor;
import co.fanki.datadog.traceinspector.config.DatadogConfig;
import co.fanki.datadog.traceinspector.datadog.DatadogClient;
import co.fanki.datadog.traceinspector.domain.EntryPoint;
import co.fanki.datadog.traceinspector.domain.ErrorContext;
import co.fanki.datadog.traceinspector.domain.ExecutionStep;
import co.fanki.datadog.traceinspector.domain.ServiceErrorView;
import co.fanki.datadog.traceinspector.domain.StackTraceLocation;
import co.fanki.datadog.traceinspector.domain.TraceDetail;
import co.fanki.datadog.traceinspector.domain.TraceQuery;
import co.fanki.datadog.traceinspector.domain.TraceScenario;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MCP tool for extracting structured test scenarios from traces.
 *
 * <p>This tool analyzes a trace to produce a structured representation
 * suitable for debugging and unit test generation. It extracts the entry
 * point, execution flow, error context, and suggests a test scenario.</p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public final class TraceExtractScenarioTool implements McpTool {

    private static final String TOOL_NAME = "trace.extract_scenario";
    private static final String TOOL_DESCRIPTION =
            "Extract a structured test scenario from a trace for debugging and unit test generation";

    private final DatadogClient datadogClient;
    private final TraceScenarioExtractor extractor;
    private final DatadogConfig config;

    /**
     * Creates a new TraceExtractScenarioTool.
     *
     * @param datadogClient the Datadog client for trace operations
     * @param extractor the scenario extractor service
     * @param config the Datadog configuration for defaults
     */
    public TraceExtractScenarioTool(
            final DatadogClient datadogClient,
            final TraceScenarioExtractor extractor,
            final DatadogConfig config
    ) {
        this.datadogClient = Objects.requireNonNull(
                datadogClient, "datadogClient must not be null"
        );
        this.extractor = Objects.requireNonNull(
                extractor, "extractor must not be null"
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

        properties.put("traceId", Map.of(
                "type", "string",
                "description", "The trace ID to analyze"
        ));

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

        schema.put("properties", properties);
        schema.put("required", List.of("traceId", "service", "from", "to"));

        return schema;
    }

    @Override
    public Map<String, Object> execute(final Map<String, Object> arguments) {
        Objects.requireNonNull(arguments, "arguments must not be null");

        try {
            final String traceId = getRequiredString(arguments, "traceId");
            final String service = getRequiredString(arguments, "service");
            final String env = getOptionalString(arguments, "env", config.defaultEnv());
            final Instant from = parseTimestamp(getRequiredString(arguments, "from"));
            final Instant to = parseTimestamp(getRequiredString(arguments, "to"));

            // Fetch trace details
            final TraceDetail traceDetail = datadogClient.getTraceDetail(
                    traceId, service, env, from, to
            );

            // Fetch associated logs
            final TraceQuery query = TraceQuery.withDefaultLimit(service, env, from, to);
            final List<ServiceErrorView.LogEntry> logs =
                    datadogClient.searchLogsForTrace(traceId, query);

            // Extract scenario
            final TraceScenario scenario = extractor.extract(traceDetail, logs);

            return buildSuccessResponse(scenario);
        } catch (final IllegalArgumentException e) {
            throw new McpToolException(TOOL_NAME, "Invalid arguments: " + e.getMessage(), e);
        } catch (final Exception e) {
            throw new McpToolException(
                    TOOL_NAME, "Failed to extract scenario: " + e.getMessage(), e
            );
        }
    }

    /**
     * Builds the success response from the extracted scenario.
     *
     * @param scenario the extracted scenario
     * @return the response map
     */
    private Map<String, Object> buildSuccessResponse(final TraceScenario scenario) {
        final Map<String, Object> response = new LinkedHashMap<>();

        response.put("success", true);
        response.put("traceId", scenario.traceId());

        // Entry point
        if (scenario.hasEntryPoint()) {
            response.put("entryPoint", buildEntryPointMap(scenario.entryPoint()));
        }

        // Execution flow
        response.put("executionFlow", buildExecutionFlowList(scenario.executionFlow()));
        response.put("stepCount", scenario.stepCount());

        // Error context
        if (scenario.hasError()) {
            response.put("errorContext", buildErrorContextMap(scenario.errorContext()));
        }

        // Relevant data
        if (!scenario.relevantData().isEmpty()) {
            response.put("relevantData", scenario.relevantData());
        }

        // Involved services
        response.put("involvedServices", scenario.involvedServices());

        // Suggested test scenario
        if (scenario.hasError()) {
            response.put("suggestedTestScenario", scenario.suggestedTestScenario());
        }

        // Total duration
        response.put("totalDurationMs", scenario.totalDurationMs());

        return response;
    }

    /**
     * Builds the entry point map for the response.
     *
     * @param entryPoint the entry point
     * @return the map representation
     */
    private Map<String, Object> buildEntryPointMap(final EntryPoint entryPoint) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("method", entryPoint.method());
        map.put("path", entryPoint.path());

        if (!entryPoint.headers().isEmpty()) {
            map.put("headers", entryPoint.headers());
        }
        if (entryPoint.hasBody()) {
            map.put("body", entryPoint.body());
        }

        return map;
    }

    /**
     * Builds the execution flow list for the response.
     *
     * @param steps the execution steps
     * @return the list representation
     */
    private List<Map<String, Object>> buildExecutionFlowList(final List<ExecutionStep> steps) {
        final List<Map<String, Object>> list = new ArrayList<>();

        for (final ExecutionStep step : steps) {
            final Map<String, Object> stepMap = new LinkedHashMap<>();
            stepMap.put("order", step.order());
            stepMap.put("spanId", step.spanId());

            if (!step.parentSpanId().isBlank()) {
                stepMap.put("parentSpanId", step.parentSpanId());
            }

            stepMap.put("service", step.service());
            stepMap.put("operation", step.operation());
            stepMap.put("type", step.type());

            if (!step.detail().isBlank()) {
                stepMap.put("detail", step.detail());
            }

            stepMap.put("durationMs", step.durationMs());

            if (step.isError()) {
                stepMap.put("isError", true);
            }

            list.add(stepMap);
        }

        return list;
    }

    /**
     * Builds the error context map for the response.
     *
     * @param errorContext the error context
     * @return the map representation
     */
    private Map<String, Object> buildErrorContextMap(final ErrorContext errorContext) {
        final Map<String, Object> map = new LinkedHashMap<>();

        map.put("service", errorContext.service());
        map.put("operation", errorContext.operation());
        map.put("exceptionType", errorContext.exceptionType());
        map.put("message", errorContext.message());

        if (!errorContext.stackTrace().isBlank()) {
            map.put("stackTrace", errorContext.stackTrace());
        }

        if (errorContext.hasLocation()) {
            final StackTraceLocation location = errorContext.location();
            final Map<String, Object> locationMap = new LinkedHashMap<>();
            locationMap.put("className", location.className());
            locationMap.put("methodName", location.methodName());
            locationMap.put("fileName", location.fileName());
            locationMap.put("lineNumber", location.lineNumber());
            map.put("location", locationMap);
        }

        if (!errorContext.spanTags().isEmpty()) {
            map.put("spanTags", errorContext.spanTags());
        }

        return map;
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
