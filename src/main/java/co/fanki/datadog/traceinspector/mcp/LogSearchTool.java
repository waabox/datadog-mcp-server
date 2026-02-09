package co.fanki.datadog.traceinspector.mcp;

import co.fanki.datadog.traceinspector.config.DatadogConfig;
import co.fanki.datadog.traceinspector.config.FilterConfigStore;
import co.fanki.datadog.traceinspector.datadog.DatadogClient;
import co.fanki.datadog.traceinspector.domain.LogGroupSummary;
import co.fanki.datadog.traceinspector.domain.LogQuery;
import co.fanki.datadog.traceinspector.domain.LogSummary;
import co.fanki.datadog.traceinspector.domain.StackTraceFilter;
import co.fanki.datadog.traceinspector.domain.StackTraceFilter.StackTraceDetail;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MCP tool for searching logs from Datadog.
 *
 * <p>This tool searches for logs matching the specified service,
 * environment, time range, and optional filters.</p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public final class LogSearchTool implements McpTool {

    private static final String TOOL_NAME = "log.search_logs";
    private static final String TOOL_DESCRIPTION =
            "Search logs for a service within a time window";

    private final DatadogClient datadogClient;
    private final DatadogConfig config;
    private final FilterConfigStore filterConfigStore;

    /**
     * Creates a new LogSearchTool.
     *
     * @param datadogClient the Datadog client for log operations
     * @param config the Datadog configuration for defaults
     * @param filterConfigStore the store for filter configuration persistence
     */
    public LogSearchTool(
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

        properties.put("query", Map.of(
                "type", "string",
                "description", "Additional Datadog query string"
        ));

        properties.put("level", Map.of(
                "type", "string",
                "description", "Log level filter (ERROR, WARN, INFO, DEBUG)"
        ));

        properties.put("limit", Map.of(
                "type", "number",
                "default", 100,
                "description", "Max logs to return"
        ));

        properties.put("outputMode", Map.of(
                "type", "string",
                "default", "full",
                "enum", List.of("full", "summarize"),
                "description", "Output mode: 'full' returns all logs, "
                        + "'summarize' groups similar logs by pattern"
        ));

        properties.put("maxMessageLength", Map.of(
                "type", "number",
                "default", 500,
                "description", "Max message length (only for 'full' mode)"
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
            final String query = getOptionalString(arguments, "query", null);
            final String level = getOptionalString(arguments, "level", null);
            final int limit = getOptionalInt(arguments, "limit", 100);
            final String outputMode = getOptionalString(arguments, "outputMode", "full");
            final int maxMessageLength = getOptionalInt(arguments, "maxMessageLength", 500);
            final String currentProject = FilterConfigStore.detectCurrentProject();
            final List<String> relevantPackages = getOptionalStringList(
                    arguments, "relevantPackages",
                    filterConfigStore.getRelevantPackages(currentProject)
            );
            final StackTraceDetail stackTraceDetail = parseStackTraceDetail(
                    getOptionalString(arguments, "stackTraceDetail", "full")
            );

            final LogQuery logQuery = new LogQuery(service, env, from, to, query, level, limit);
            final List<LogSummary> logs = datadogClient.searchLogs(logQuery);

            // Create filter if relevant packages are specified
            final StackTraceFilter filter = new StackTraceFilter(relevantPackages);

            if ("summarize".equalsIgnoreCase(outputMode)) {
                return buildSummarizedResponse(logs);
            }
            return buildSuccessResponse(logs, maxMessageLength, filter, stackTraceDetail);
        } catch (final IllegalArgumentException e) {
            throw new McpToolException(TOOL_NAME, "Invalid arguments: " + e.getMessage(), e);
        } catch (final Exception e) {
            throw new McpToolException(TOOL_NAME, "Failed to search logs: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildSuccessResponse(
            final List<LogSummary> logs,
            final int maxMessageLength,
            final StackTraceFilter filter,
            final StackTraceDetail detail
    ) {
        final List<Map<String, Object>> logList = new ArrayList<>();

        for (final LogSummary log : logs) {
            final Map<String, Object> logMap = new HashMap<>();
            logMap.put("timestamp", log.formattedTimestamp());
            logMap.put("level", log.level());
            logMap.put("service", log.service());
            // Filter stack traces in message if present
            final String filteredMessage = filter.filter(log.message(), detail);
            logMap.put("message", truncateMessage(filteredMessage, maxMessageLength));
            logMap.put("host", log.host());
            if (log.hasTrace()) {
                logMap.put("traceId", log.traceId());
            }
            logList.add(logMap);
        }

        return Map.of(
                "success", true,
                "count", logs.size(),
                "logs", logList
        );
    }

    private String truncateMessage(final String message, final int maxLength) {
        if (message == null || message.length() <= maxLength) {
            return message;
        }
        return message.substring(0, maxLength - 3) + "...";
    }

    private Map<String, Object> buildSummarizedResponse(final List<LogSummary> logs) {
        final Map<String, GroupAccumulator> groups = new LinkedHashMap<>();

        for (final LogSummary log : logs) {
            final String pattern = LogGroupSummary.extractPattern(log.message());
            final String key = log.level() + "|" + pattern;

            groups.computeIfAbsent(key, k -> new GroupAccumulator(
                    pattern, log.level(), log.service(), log.message()
            )).add(log);
        }

        final List<LogGroupSummary> summaries = groups.values().stream()
                .map(GroupAccumulator::toSummary)
                .sorted(Comparator.comparingInt(LogGroupSummary::count).reversed())
                .toList();

        final List<Map<String, Object>> groupList = new ArrayList<>();
        for (final LogGroupSummary summary : summaries) {
            final Map<String, Object> groupMap = new LinkedHashMap<>();
            groupMap.put("pattern", summary.pattern());
            groupMap.put("level", summary.level());
            groupMap.put("count", summary.count());
            groupMap.put("firstOccurrence", summary.firstOccurrence().toString());
            groupMap.put("lastOccurrence", summary.lastOccurrence().toString());
            groupList.add(groupMap);
        }

        return Map.of(
                "success", true,
                "totalLogs", logs.size(),
                "uniquePatterns", summaries.size(),
                "groups", groupList
        );
    }

    /**
     * Helper class to accumulate logs into groups.
     */
    private static final class GroupAccumulator {
        private final String pattern;
        private final String level;
        private final String service;
        private final String sampleMessage;
        private int count;
        private Instant first;
        private Instant last;

        GroupAccumulator(
                final String pattern,
                final String level,
                final String service,
                final String sampleMessage
        ) {
            this.pattern = pattern;
            this.level = level;
            this.service = service;
            this.sampleMessage = sampleMessage;
            this.count = 0;
            this.first = null;
            this.last = null;
        }

        void add(final LogSummary log) {
            count++;
            if (first == null || log.timestamp().isBefore(first)) {
                first = log.timestamp();
            }
            if (last == null || log.timestamp().isAfter(last)) {
                last = log.timestamp();
            }
        }

        LogGroupSummary toSummary() {
            return new LogGroupSummary(
                    pattern, level, service, count, first, last, sampleMessage
            );
        }
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
