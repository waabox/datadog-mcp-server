package co.fanki.datadog.traceinspector.datadog;

import co.fanki.datadog.traceinspector.config.DatadogConfig;
import co.fanki.datadog.traceinspector.domain.LogQuery;
import co.fanki.datadog.traceinspector.domain.LogSummary;
import co.fanki.datadog.traceinspector.domain.ServiceErrorView;
import co.fanki.datadog.traceinspector.domain.SpanDetail;
import co.fanki.datadog.traceinspector.domain.TraceDetail;
import co.fanki.datadog.traceinspector.domain.TraceQuery;
import co.fanki.datadog.traceinspector.domain.TraceSummary;
import com.datadog.api.client.ApiClient;
import com.datadog.api.client.ApiException;
import com.datadog.api.client.v2.api.LogsApi;
import com.datadog.api.client.v2.api.SpansApi;
import com.datadog.api.client.v2.model.Log;
import com.datadog.api.client.v2.model.LogsListRequest;
import com.datadog.api.client.v2.model.LogsListRequestPage;
import com.datadog.api.client.v2.model.LogsListResponse;
import com.datadog.api.client.v2.model.LogsQueryFilter;
import com.datadog.api.client.v2.model.LogsSort;
import com.datadog.api.client.v2.model.Span;
import com.datadog.api.client.v2.model.SpansListRequest;
import com.datadog.api.client.v2.model.SpansListRequestAttributes;
import com.datadog.api.client.v2.model.SpansListRequestData;
import com.datadog.api.client.v2.model.SpansListRequestPage;
import com.datadog.api.client.v2.model.SpansListRequestType;
import com.datadog.api.client.v2.model.SpansListResponse;
import com.datadog.api.client.v2.model.SpansQueryFilter;
import com.datadog.api.client.v2.model.SpansSort;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SDK-based implementation of the DatadogClient interface.
 *
 * <p>Uses the official Datadog API client SDK to communicate with Datadog's APIs.
 * Provides type-safe requests and responses with automatic authentication.</p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public final class DatadogClientImpl implements DatadogClient {

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ISO_INSTANT;

    private final SpansApi spansApi;
    private final LogsApi logsApi;

    /**
     * Creates a new DatadogClientImpl with the given configuration.
     *
     * @param config the Datadog configuration
     */
    public DatadogClientImpl(final DatadogConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        final ApiClient apiClient = config.buildApiClient();
        this.spansApi = new SpansApi(apiClient);
        this.logsApi = new LogsApi(apiClient);
    }

    /**
     * Creates a new DatadogClientImpl with custom API instances.
     * Useful for testing.
     *
     * @param spansApi the spans API instance
     * @param logsApi the logs API instance
     */
    public DatadogClientImpl(final SpansApi spansApi, final LogsApi logsApi) {
        this.spansApi = Objects.requireNonNull(spansApi, "spansApi must not be null");
        this.logsApi = Objects.requireNonNull(logsApi, "logsApi must not be null");
    }

    @Override
    public List<TraceSummary> searchErrorTraces(final TraceQuery query) {
        Objects.requireNonNull(query, "query must not be null");

        try {
            final SpansListRequest request = buildSpanSearchRequest(query);
            final SpansListResponse response = spansApi.listSpans(request);

            return mapToTraceSummaries(response);
        } catch (final ApiException e) {
            throw new DatadogApiException(
                    "Failed to search error traces: " + e.getResponseBody(),
                    e.getCode()
            );
        }
    }

    @Override
    public TraceDetail getTraceDetail(
            final String traceId,
            final String service,
            final String env,
            final Instant from,
            final Instant to
    ) {
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(service, "service must not be null");
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");

        try {
            // trace_id is a reserved attribute in Datadog, no @ prefix needed
            final SpansQueryFilter filter = new SpansQueryFilter()
                    .query("trace_id:" + traceId)
                    .from(formatInstant(from))
                    .to(formatInstant(to));

            final SpansListRequest request = new SpansListRequest()
                    .data(new SpansListRequestData()
                            .type(SpansListRequestType.SEARCH_REQUEST)
                            .attributes(new SpansListRequestAttributes()
                                    .filter(filter)
                                    .page(new SpansListRequestPage().limit(1000))
                                    .sort(SpansSort.TIMESTAMP_ASCENDING)));

            final SpansListResponse response = spansApi.listSpans(request);

            return mapToTraceDetail(response, traceId, service, env != null ? env : "");
        } catch (final ApiException e) {
            throw new DatadogApiException(
                    "Failed to get trace detail: " + e.getResponseBody(),
                    e.getCode()
            );
        }
    }

    @Override
    public List<ServiceErrorView.LogEntry> searchLogsForTrace(
            final String traceId,
            final TraceQuery query
    ) {
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(query, "query must not be null");

        try {
            // trace_id is a reserved attribute in Datadog, no @ prefix needed
            final LogsQueryFilter filter = new LogsQueryFilter()
                    .query("trace_id:" + traceId)
                    .from(formatInstant(query.from()))
                    .to(formatInstant(query.to()));

            final LogsListRequest body = new LogsListRequest()
                    .filter(filter)
                    .page(new LogsListRequestPage().limit(100))
                    .sort(LogsSort.TIMESTAMP_ASCENDING);

            final LogsListResponse response = logsApi.listLogs(
                    new LogsApi.ListLogsOptionalParameters().body(body)
            );

            return mapToLogEntries(response);
        } catch (final ApiException e) {
            throw new DatadogApiException(
                    "Failed to search logs for trace: " + e.getResponseBody(),
                    e.getCode()
            );
        }
    }

    @Override
    public List<LogSummary> searchLogs(final LogQuery query) {
        Objects.requireNonNull(query, "query must not be null");

        try {
            final LogsQueryFilter filter = new LogsQueryFilter()
                    .query(query.toDatadogQuery())
                    .from(formatInstant(query.from()))
                    .to(formatInstant(query.to()));

            final LogsListRequest body = new LogsListRequest()
                    .filter(filter)
                    .page(new LogsListRequestPage().limit(query.limit()))
                    .sort(LogsSort.TIMESTAMP_DESCENDING);

            final LogsListResponse response = logsApi.listLogs(
                    new LogsApi.ListLogsOptionalParameters().body(body)
            );

            return mapToLogSummaries(response);
        } catch (final ApiException e) {
            throw new DatadogApiException(
                    "Failed to search logs: " + e.getResponseBody(),
                    e.getCode()
            );
        }
    }

    private SpansListRequest buildSpanSearchRequest(final TraceQuery query) {
        final SpansQueryFilter filter = new SpansQueryFilter()
                .query(query.toDatadogQuery())
                .from(formatInstant(query.from()))
                .to(formatInstant(query.to()));

        return new SpansListRequest()
                .data(new SpansListRequestData()
                        .type(SpansListRequestType.SEARCH_REQUEST)
                        .attributes(new SpansListRequestAttributes()
                                .filter(filter)
                                .page(new SpansListRequestPage().limit(query.limit()))
                                .sort(SpansSort.TIMESTAMP_DESCENDING)));
    }

    private String formatInstant(final Instant instant) {
        return ISO_FORMATTER.format(instant);
    }

    private List<TraceSummary> mapToTraceSummaries(final SpansListResponse response) {
        if (response == null || response.getData() == null) {
            return List.of();
        }

        return response.getData().stream()
                .filter(span -> span.getAttributes() != null)
                .map(this::mapSpanToTraceSummary)
                .toList();
    }

    private TraceSummary mapSpanToTraceSummary(final Span span) {
        final var attrs = span.getAttributes();
        final Map<String, Object> customAttrs = attrs.getCustom() != null
                ? attrs.getCustom()
                : Map.of();

        // trace_id is a reserved attribute with its own getter in the SDK
        final String traceId = attrs.getTraceId() != null ? attrs.getTraceId() : "";
        final String service = attrs.getService() != null ? attrs.getService() : "";
        final String resourceName = attrs.getResourceName() != null ? attrs.getResourceName() : "";
        final String errorMessage = extractAttribute(customAttrs, "error.message", "");

        final Instant timestamp = parseTimestamp(attrs.getEndTimestamp());
        final long duration = calculateDuration(attrs.getStartTimestamp(), attrs.getEndTimestamp());

        return new TraceSummary(traceId, service, resourceName, errorMessage, timestamp, duration);
    }

    private TraceDetail mapToTraceDetail(
            final SpansListResponse response,
            final String traceId,
            final String service,
            final String env
    ) {
        if (response == null || response.getData() == null || response.getData().isEmpty()) {
            return new TraceDetail(traceId, service, env, "", Instant.now(), 0L, List.of());
        }

        final List<SpanDetail> spans = response.getData().stream()
                .filter(span -> span.getAttributes() != null)
                .map(this::mapToSpanDetail)
                .toList();

        final Span firstSpan = response.getData().get(0);
        final var attrs = firstSpan.getAttributes();

        final Instant startTime = parseTimestamp(attrs.getStartTimestamp());

        final long totalDuration = spans.stream()
                .mapToLong(SpanDetail::duration)
                .max()
                .orElse(0L);

        final String resourceName = attrs != null && attrs.getResourceName() != null
                ? attrs.getResourceName()
                : "";

        return new TraceDetail(traceId, service, env, resourceName, startTime, totalDuration, spans);
    }

    private SpanDetail mapToSpanDetail(final Span span) {
        final var attrs = span.getAttributes();
        final Map<String, Object> customAttrs = attrs.getCustom() != null
                ? attrs.getCustom()
                : Map.of();

        // spanId and parentId are reserved attributes with their own getters
        final String spanId = attrs.getSpanId() != null ? attrs.getSpanId() : "";
        final String parentId = attrs.getParentId();
        final String service = attrs.getService() != null ? attrs.getService() : "";
        final String operationName = extractAttribute(customAttrs, "operation_name", "");
        final String resourceName = attrs.getResourceName() != null ? attrs.getResourceName() : "";

        final Instant startTime = parseTimestamp(attrs.getStartTimestamp());
        final long duration = calculateDuration(attrs.getStartTimestamp(), attrs.getEndTimestamp());

        final String spanType = attrs.getType() != null ? attrs.getType() : "";
        final boolean isError = "error".equalsIgnoreCase(spanType)
                || extractAttribute(customAttrs, "error", "").equals("true")
                || extractAttribute(customAttrs, "error.message", null) != null;
        final String errorMessage = extractAttribute(customAttrs, "error.message", null);
        final String errorType = extractAttribute(customAttrs, "error.type", null);
        final String errorStack = extractAttribute(customAttrs, "error.stack", null);

        final Map<String, String> tags = flattenAttributes(customAttrs);

        return new SpanDetail(
                spanId, parentId, service, operationName, resourceName,
                startTime, duration, isError, errorMessage, errorType, errorStack, tags
        );
    }

    private List<ServiceErrorView.LogEntry> mapToLogEntries(final LogsListResponse response) {
        if (response == null || response.getData() == null) {
            return List.of();
        }

        return response.getData().stream()
                .filter(log -> log.getAttributes() != null)
                .map(this::mapToLogEntry)
                .toList();
    }

    private ServiceErrorView.LogEntry mapToLogEntry(final Log log) {
        final var attrs = log.getAttributes();
        final Map<String, Object> customAttrs = attrs.getAttributes() != null
                ? attrs.getAttributes()
                : Map.of();

        final Instant timestamp = parseTimestamp(attrs.getTimestamp());
        final String level = attrs.getStatus() != null
                ? attrs.getStatus().toUpperCase()
                : "INFO";
        final String message = attrs.getMessage() != null ? attrs.getMessage() : "";
        final Map<String, String> attributes = flattenAttributes(customAttrs);

        return new ServiceErrorView.LogEntry(timestamp, level, message, attributes);
    }

    private List<LogSummary> mapToLogSummaries(final LogsListResponse response) {
        if (response == null || response.getData() == null) {
            return List.of();
        }

        return response.getData().stream()
                .filter(log -> log.getAttributes() != null)
                .map(this::mapToLogSummary)
                .toList();
    }

    private LogSummary mapToLogSummary(final Log log) {
        final var attrs = log.getAttributes();
        final Map<String, Object> customAttrs = attrs.getAttributes() != null
                ? attrs.getAttributes()
                : Map.of();

        final Instant timestamp = parseTimestamp(attrs.getTimestamp());
        final String level = attrs.getStatus() != null
                ? attrs.getStatus().toUpperCase()
                : "INFO";
        final String service = attrs.getService() != null ? attrs.getService() : "";
        final String message = attrs.getMessage() != null ? attrs.getMessage() : "";
        final String host = attrs.getHost() != null ? attrs.getHost() : "";

        final String traceId = extractAttribute(customAttrs, "trace_id",
                extractAttribute(customAttrs, "dd.trace_id", ""));

        return new LogSummary(timestamp, level, service, message, host, traceId);
    }

    private Instant parseTimestamp(final OffsetDateTime dateTime) {
        if (dateTime == null) {
            return Instant.now();
        }
        return dateTime.toInstant();
    }

    private long calculateDuration(final OffsetDateTime start, final OffsetDateTime end) {
        if (start == null || end == null) {
            return 0L;
        }
        return java.time.Duration.between(start, end).toNanos();
    }

    private long parseDuration(final Map<String, Object> attrs) {
        final Object duration = attrs.get("duration");
        if (duration == null) {
            return 0L;
        }
        if (duration instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(duration.toString());
        } catch (final NumberFormatException e) {
            return 0L;
        }
    }

    private String extractAttribute(final Map<String, Object> attributes,
                                    final String key,
                                    final String defaultValue) {
        if (attributes == null) {
            return defaultValue;
        }
        final Object value = attributes.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private Map<String, String> flattenAttributes(final Map<String, Object> attributes) {
        if (attributes == null) {
            return Map.of();
        }

        final Map<String, String> result = new HashMap<>();
        for (final var entry : attributes.entrySet()) {
            if (entry.getValue() != null) {
                result.put(entry.getKey(), entry.getValue().toString());
            }
        }
        return result;
    }
}
