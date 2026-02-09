package co.fanki.datadog.traceinspector.mcp;

import co.fanki.datadog.traceinspector.config.DatadogConfig;
import co.fanki.datadog.traceinspector.config.FilterConfigStore;
import co.fanki.datadog.traceinspector.datadog.DatadogClient;
import co.fanki.datadog.traceinspector.domain.ServiceErrorView;
import co.fanki.datadog.traceinspector.domain.StackTraceFilter;
import co.fanki.datadog.traceinspector.domain.StackTraceFilter.StackTraceDetail;
import co.fanki.datadog.traceinspector.domain.TraceDetail;
import co.fanki.datadog.traceinspector.domain.TraceQuery;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MCP tool for correlating logs with traces.
 *
 * <p>This tool retrieves logs associated with a specific trace ID and
 * optionally includes trace summary information.</p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public final class LogCorrelateTool implements McpTool {

    private static final String TOOL_NAME = "log.correlate";
    private static final String TOOL_DESCRIPTION =
            "Correlate logs and traces by trace ID";

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    private final DatadogClient datadogClient;
    private final DatadogConfig config;
    private final FilterConfigStore filterConfigStore;

    /**
     * Creates a new LogCorrelateTool.
     *
     * @param datadogClient the Datadog client for log and trace operations
     * @param config the Datadog configuration for defaults
     * @param filterConfigStore the store for filter configuration persistence
     */
    public LogCorrelateTool(
            final DatadogClient datadogClient,
            final DatadogConfig config,
            final FilterConfigStore filterConfigStore
    ) {
        this.datadogClient = Objects.requireNonNull(
                datadogClient, "datadogClient must not be null"
        );
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.filterConfigStore = Objects.requireNonNull(
                filterConfigStore, "filterConfigStore must not be null"
        );
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
                "description", "The trace ID to search for"
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

        properties.put("includeTrace", Map.of(
                "type", "boolean",
                "default", true,
                "description", "Include trace summary in the response"
        ));

        properties.put("relevantPackages", Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "description", "Package prefixes to keep in stack traces "
                        + "(e.g., ['co.fanki', 'com.mycompany']). "
                        + "If empty, no filtering is applied."
        ));

        properties.put("stackTraceDetail", Map.of(
                "type", "string",
                "default", "full",
                "enum", List.of("full", "relevant", "minimal"),
                "description", "Stack trace detail level: "
                        + "'full' returns complete stack trace, "
                        + "'relevant' filters to specified packages only, "
                        + "'minimal' shows only exception message and root cause"
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
            final boolean includeTrace = getOptionalBoolean(arguments, "includeTrace", true);
            final List<String> relevantPackages = getOptionalStringList(
                    arguments, "relevantPackages", filterConfigStore.getRelevantPackages()
            );
            final StackTraceDetail stackTraceDetail = parseStackTraceDetail(
                    getOptionalString(arguments, "stackTraceDetail", "full")
            );

            final TraceQuery query = TraceQuery.withDefaultLimit(service, env, from, to);

            // Create filter for stack traces
            final StackTraceFilter filter = new StackTraceFilter(relevantPackages);

            final Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("traceId", traceId);

            if (includeTrace) {
                final TraceDetail traceDetail = datadogClient.getTraceDetail(
                        traceId, service, env, from, to
                );
                if (traceDetail != null) {
                    result.put("trace", buildTraceSummary(traceDetail));
                }
            }

            final List<ServiceErrorView.LogEntry> logs =
                    datadogClient.searchLogsForTrace(traceId, query);

            result.put("logs", buildLogList(logs, filter, stackTraceDetail));
            result.put("logCount", logs.size());

            return result;
        } catch (final IllegalArgumentException e) {
            throw new McpToolException(TOOL_NAME, "Invalid arguments: " + e.getMessage(), e);
        } catch (final Exception e) {
            throw new McpToolException(TOOL_NAME, "Failed to correlate logs: " + e.getMessage(), e);
        }
    }

    /**
     * Builds the trace summary map for the response.
     *
     * @param trace the trace detail
     * @return a map with trace summary information
     */
    private Map<String, Object> buildTraceSummary(final TraceDetail trace) {
        final Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("service", trace.service());
        summary.put("resourceName", trace.resourceName());
        summary.put("duration", trace.formattedDuration());
        summary.put("spanCount", trace.spanCount());
        summary.put("services", new ArrayList<>(trace.involvedServices()));
        summary.put("hasErrors", trace.hasErrors());
        return summary;
    }

    /**
     * Builds the log list for the response.
     *
     * @param logs the log entries
     * @param filter the stack trace filter
     * @param detail the stack trace detail level
     *
     * @return a list of log maps
     */
    private List<Map<String, Object>> buildLogList(
            final List<ServiceErrorView.LogEntry> logs,
            final StackTraceFilter filter,
            final StackTraceDetail detail
    ) {
        final List<Map<String, Object>> logList = new ArrayList<>();
        for (final ServiceErrorView.LogEntry log : logs) {
            final Map<String, Object> logMap = new LinkedHashMap<>();
            logMap.put("timestamp", TIMESTAMP_FORMATTER.format(log.timestamp()));
            logMap.put("level", log.level());
            logMap.put("message", log.message());
            if (!log.attributes().isEmpty()) {
                // Filter stack traces in attributes
                final Map<String, String> filteredAttributes =
                        filter.filterAttributes(log.attributes(), detail);
                logMap.put("attributes", filteredAttributes);
            }
            logList.add(logMap);
        }
        return logList;
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

    private boolean getOptionalBoolean(
            final Map<String, Object> args,
            final String key,
            final boolean defaultValue
    ) {
        final Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(value.toString());
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

    @SuppressWarnings("unchecked")
    private List<String> getOptionalStringList(
            final Map<String, Object> args,
            final String key,
            final List<String> defaultValue
    ) {
        final Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof List<?> list) {
            final List<String> result = new ArrayList<>();
            for (final Object item : list) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
            return result;
        }
        // If it's a single string, treat it as a single-item list
        return List.of(value.toString());
    }

    private StackTraceDetail parseStackTraceDetail(final String value) {
        if (value == null) {
            return StackTraceDetail.FULL;
        }
        return switch (value.toLowerCase()) {
            case "relevant" -> StackTraceDetail.RELEVANT;
            case "minimal" -> StackTraceDetail.MINIMAL;
            default -> StackTraceDetail.FULL;
        };
    }
}
