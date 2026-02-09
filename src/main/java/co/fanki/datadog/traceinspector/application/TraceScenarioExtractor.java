package co.fanki.datadog.traceinspector.application;

import co.fanki.datadog.traceinspector.domain.EntryPoint;
import co.fanki.datadog.traceinspector.domain.ErrorContext;
import co.fanki.datadog.traceinspector.domain.ExecutionStep;
import co.fanki.datadog.traceinspector.domain.ServiceErrorView;
import co.fanki.datadog.traceinspector.domain.SpanDetail;
import co.fanki.datadog.traceinspector.domain.StackTraceLocation;
import co.fanki.datadog.traceinspector.domain.TraceDetail;
import co.fanki.datadog.traceinspector.domain.TraceScenario;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Extracts a structured scenario from a trace for debugging and test generation.
 *
 * <p>This service analyzes a trace's spans and logs to produce a structured
 * representation of what happened during the request, including the entry point,
 * execution flow, and error context.</p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public final class TraceScenarioExtractor {

    /** HTTP-related tag prefixes. */
    private static final Set<String> HTTP_TAGS = Set.of(
            "http.method", "http.url", "http.route", "http.path",
            "http.status_code", "http.request.body", "http.request.headers"
    );

    /** Database-related tag prefixes. */
    private static final Set<String> DB_TAGS = Set.of(
            "db.type", "db.statement", "db.instance", "db.name",
            "db.operation", "sql.query"
    );

    /** Cache-related tag prefixes. */
    private static final Set<String> CACHE_TAGS = Set.of(
            "cache.type", "redis.command", "memcached.command"
    );

    /** Queue-related tag prefixes. */
    private static final Set<String> QUEUE_TAGS = Set.of(
            "kafka.topic", "rabbitmq.queue", "sqs.queue", "message.type"
    );

    /** Tags that contain relevant business data. */
    private static final Set<String> RELEVANT_DATA_PATTERNS = Set.of(
            "user_id", "user.id", "customer_id", "order_id", "product_id",
            "request_id", "correlation_id", "session_id", "tenant_id",
            "amount", "quantity", "status", "type"
    );

    /**
     * Creates a new TraceScenarioExtractor.
     */
    public TraceScenarioExtractor() {
        // Default constructor
    }

    /**
     * Extracts a structured scenario from a trace.
     *
     * @param traceDetail the trace detail with spans
     * @param logs the associated log entries
     * @return the extracted scenario
     */
    public TraceScenario extract(
            final TraceDetail traceDetail,
            final List<ServiceErrorView.LogEntry> logs
    ) {
        Objects.requireNonNull(traceDetail, "traceDetail must not be null");

        final List<SpanDetail> spans = traceDetail.spans();
        if (spans.isEmpty()) {
            return new TraceScenario(
                    traceDetail.traceId(),
                    EntryPoint.empty(),
                    List.of(),
                    ErrorContext.empty(),
                    Map.of(),
                    List.of()
            );
        }

        final EntryPoint entryPoint = extractEntryPoint(spans);
        final List<ExecutionStep> executionFlow = buildExecutionFlow(spans);
        final ErrorContext errorContext = extractErrorContext(spans);
        final Map<String, String> relevantData = extractRelevantData(spans, logs);
        final List<String> involvedServices = extractInvolvedServices(spans);

        return new TraceScenario(
                traceDetail.traceId(),
                entryPoint,
                executionFlow,
                errorContext,
                relevantData,
                involvedServices
        );
    }

    /**
     * Extracts the HTTP entry point from the root span.
     *
     * @param spans the list of spans
     * @return the entry point
     */
    private EntryPoint extractEntryPoint(final List<SpanDetail> spans) {
        final SpanDetail rootSpan = findRootSpan(spans);
        if (rootSpan == null) {
            return EntryPoint.empty();
        }

        final Map<String, String> tags = rootSpan.tags();

        final String method = getFirstMatch(tags,
                "http.method", "http.request.method");
        final String path = getFirstMatch(tags,
                "http.url", "http.route", "http.path", "http.target");
        final String body = getFirstMatch(tags,
                "http.request.body", "request.body");

        final Map<String, String> headers = extractHeaders(tags);

        return new EntryPoint(method, path, headers, body);
    }

    /**
     * Builds the execution flow from spans, ordered chronologically.
     *
     * @param spans the list of spans
     * @return the ordered execution steps
     */
    private List<ExecutionStep> buildExecutionFlow(final List<SpanDetail> spans) {
        final List<SpanDetail> sortedSpans = spans.stream()
                .sorted(Comparator.comparing(SpanDetail::startTime))
                .toList();

        final List<ExecutionStep> steps = new ArrayList<>();
        int order = 1;

        for (final SpanDetail span : sortedSpans) {
            final String type = classifySpanType(span);
            final String operation = buildOperationDescription(span, type);
            final String detail = extractDetail(span, type);
            final long durationMs = span.duration() / 1_000_000; // nano to ms

            steps.add(new ExecutionStep(
                    order++,
                    span.spanId(),
                    span.parentSpanId(),
                    span.service(),
                    operation,
                    type,
                    detail,
                    durationMs,
                    span.isError()
            ));
        }

        return steps;
    }

    /**
     * Extracts the error context from the first error span.
     *
     * @param spans the list of spans
     * @return the error context
     */
    private ErrorContext extractErrorContext(final List<SpanDetail> spans) {
        final SpanDetail errorSpan = spans.stream()
                .filter(SpanDetail::isError)
                .findFirst()
                .orElse(null);

        if (errorSpan == null) {
            return ErrorContext.empty();
        }

        final StackTraceLocation location = StackTraceLocation.parseFirst(
                errorSpan.errorStack()
        );

        return new ErrorContext(
                errorSpan.service(),
                buildOperationDescription(errorSpan, classifySpanType(errorSpan)),
                errorSpan.errorType(),
                errorSpan.errorMessage(),
                errorSpan.errorStack(),
                location,
                filterRelevantTags(errorSpan.tags())
        );
    }

    /**
     * Extracts relevant business data from spans and logs.
     *
     * @param spans the list of spans
     * @param logs the list of log entries
     * @return the relevant data map
     */
    private Map<String, String> extractRelevantData(
            final List<SpanDetail> spans,
            final List<ServiceErrorView.LogEntry> logs
    ) {
        final Map<String, String> data = new HashMap<>();

        // Extract from spans
        for (final SpanDetail span : spans) {
            for (final var entry : span.tags().entrySet()) {
                if (isRelevantDataKey(entry.getKey())) {
                    data.put(normalizeKey(entry.getKey()), entry.getValue());
                }
            }
        }

        // Extract from logs
        if (logs != null) {
            for (final ServiceErrorView.LogEntry log : logs) {
                for (final var entry : log.attributes().entrySet()) {
                    if (isRelevantDataKey(entry.getKey())) {
                        data.put(normalizeKey(entry.getKey()), entry.getValue());
                    }
                }
            }
        }

        return data;
    }

    /**
     * Extracts the list of unique services involved in the trace.
     *
     * @param spans the list of spans
     * @return the list of services
     */
    private List<String> extractInvolvedServices(final List<SpanDetail> spans) {
        return spans.stream()
                .map(SpanDetail::service)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Finds the root span (no parent or parent is "0").
     *
     * @param spans the list of spans
     * @return the root span, or the first span if no root found
     */
    private SpanDetail findRootSpan(final List<SpanDetail> spans) {
        return spans.stream()
                .filter(SpanDetail::isRoot)
                .findFirst()
                .orElse(spans.isEmpty() ? null : spans.get(0));
    }

    /**
     * Classifies a span by its type based on tags.
     *
     * @param span the span to classify
     * @return the span type
     */
    private String classifySpanType(final SpanDetail span) {
        final Map<String, String> tags = span.tags();

        if (hasAnyTag(tags, HTTP_TAGS)) {
            return ExecutionStep.TYPE_HTTP;
        }
        if (hasAnyTag(tags, DB_TAGS)) {
            return ExecutionStep.TYPE_DB;
        }
        if (hasAnyTag(tags, CACHE_TAGS)) {
            return ExecutionStep.TYPE_CACHE;
        }
        if (hasAnyTag(tags, QUEUE_TAGS)) {
            return ExecutionStep.TYPE_QUEUE;
        }

        // Check span.kind for external calls
        final String spanKind = tags.getOrDefault("span.kind", "");
        if ("client".equalsIgnoreCase(spanKind)) {
            return ExecutionStep.TYPE_EXTERNAL;
        }

        return ExecutionStep.TYPE_INTERNAL;
    }

    /**
     * Builds a human-readable operation description.
     *
     * @param span the span
     * @param type the span type
     * @return the operation description
     */
    private String buildOperationDescription(final SpanDetail span, final String type) {
        final Map<String, String> tags = span.tags();

        switch (type) {
            case ExecutionStep.TYPE_HTTP -> {
                final String method = getFirstMatch(tags, "http.method");
                final String path = getFirstMatch(tags, "http.route", "http.url", "http.path");
                if (!method.isEmpty() && !path.isEmpty()) {
                    return "%s %s".formatted(method, path);
                }
            }
            case ExecutionStep.TYPE_DB -> {
                final String dbOp = getFirstMatch(tags, "db.operation");
                if (!dbOp.isEmpty()) {
                    return dbOp;
                }
                // Try to extract operation from statement
                final String statement = getFirstMatch(tags, "db.statement", "sql.query");
                if (!statement.isEmpty()) {
                    return truncate(statement, 80);
                }
            }
        }

        // Fallback to resource name or operation name
        if (!span.resourceName().isBlank()) {
            return span.resourceName();
        }
        if (!span.operationName().isBlank()) {
            return span.operationName();
        }

        return span.service();
    }

    /**
     * Extracts additional detail for a span based on its type.
     *
     * @param span the span
     * @param type the span type
     * @return the detail string
     */
    private String extractDetail(final SpanDetail span, final String type) {
        final Map<String, String> tags = span.tags();

        return switch (type) {
            case ExecutionStep.TYPE_DB -> getFirstMatch(tags, "db.statement", "sql.query");
            case ExecutionStep.TYPE_HTTP -> getFirstMatch(tags, "http.status_code");
            case ExecutionStep.TYPE_CACHE -> getFirstMatch(tags, "redis.command", "cache.key");
            case ExecutionStep.TYPE_QUEUE -> getFirstMatch(tags, "kafka.topic", "rabbitmq.queue");
            default -> "";
        };
    }

    /**
     * Extracts HTTP headers from tags.
     *
     * @param tags the span tags
     * @return the headers map
     */
    private Map<String, String> extractHeaders(final Map<String, String> tags) {
        final Map<String, String> headers = new HashMap<>();

        for (final var entry : tags.entrySet()) {
            final String key = entry.getKey().toLowerCase();
            if (key.startsWith("http.request.headers.") || key.startsWith("http.header.")) {
                final String headerName = key.substring(key.lastIndexOf('.') + 1);
                headers.put(headerName, entry.getValue());
            }
        }

        // Also check for common headers stored directly
        addIfPresent(headers, tags, "content-type", "http.content_type");
        addIfPresent(headers, tags, "user-agent", "http.user_agent");

        return headers;
    }

    /**
     * Filters tags to only include relevant ones.
     *
     * @param tags the span tags
     * @return the filtered tags
     */
    private Map<String, String> filterRelevantTags(final Map<String, String> tags) {
        final Map<String, String> filtered = new HashMap<>();

        for (final var entry : tags.entrySet()) {
            if (isRelevantDataKey(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }

        return filtered;
    }

    /**
     * Checks if a key matches relevant data patterns.
     *
     * @param key the key to check
     * @return true if relevant
     */
    private boolean isRelevantDataKey(final String key) {
        final String lowerKey = key.toLowerCase();
        return RELEVANT_DATA_PATTERNS.stream()
                .anyMatch(pattern -> lowerKey.contains(pattern));
    }

    /**
     * Normalizes a key by converting to snake_case.
     *
     * @param key the key to normalize
     * @return the normalized key
     */
    private String normalizeKey(final String key) {
        return key.toLowerCase()
                .replace(".", "_")
                .replace("-", "_");
    }

    /**
     * Gets the first non-empty value from multiple possible tag keys.
     *
     * @param tags the tags map
     * @param keys the keys to try
     * @return the first non-empty value, or empty string
     */
    private String getFirstMatch(final Map<String, String> tags, final String... keys) {
        for (final String key : keys) {
            final String value = tags.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    /**
     * Checks if any of the given tags are present.
     *
     * @param tags the tags map
     * @param checkTags the tag names to check
     * @return true if any tag is present
     */
    private boolean hasAnyTag(final Map<String, String> tags, final Set<String> checkTags) {
        for (final String tagKey : tags.keySet()) {
            for (final String checkTag : checkTags) {
                if (tagKey.startsWith(checkTag)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Adds a value to the map if present in tags.
     *
     * @param target the target map
     * @param tags the source tags
     * @param targetKey the key to use in target
     * @param sourceKeys the keys to try in source
     */
    private void addIfPresent(
            final Map<String, String> target,
            final Map<String, String> tags,
            final String targetKey,
            final String... sourceKeys
    ) {
        final String value = getFirstMatch(tags, sourceKeys);
        if (!value.isEmpty()) {
            target.put(targetKey, value);
        }
    }

    /**
     * Truncates a string to a maximum length.
     *
     * @param value the string to truncate
     * @param maxLength the maximum length
     * @return the truncated string
     */
    private String truncate(final String value, final int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }
}
