package co.fanki.datadog.traceinspector.datadog;

import co.fanki.datadog.traceinspector.config.DatadogConfig;
import co.fanki.datadog.traceinspector.datadog.model.LogSearchResponse;
import co.fanki.datadog.traceinspector.datadog.model.TraceDetailResponse;
import co.fanki.datadog.traceinspector.datadog.model.TraceSearchResponse;
import co.fanki.datadog.traceinspector.domain.ServiceErrorView;
import co.fanki.datadog.traceinspector.domain.SpanDetail;
import co.fanki.datadog.traceinspector.domain.TraceDetail;
import co.fanki.datadog.traceinspector.domain.TraceQuery;
import co.fanki.datadog.traceinspector.domain.TraceSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP implementation of the DatadogClient interface.
 *
 * <p>Uses Java's built-in HttpClient to communicate with Datadog's APIs.
 * Handles authentication, request building, and response parsing.</p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public final class DatadogClientImpl implements DatadogClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final DatadogConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrlOverride;

    /**
     * Creates a new DatadogClientImpl with the given configuration.
     *
     * @param config the Datadog configuration
     */
    public DatadogClientImpl(final DatadogConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        this.baseUrlOverride = null;
    }

    /**
     * Creates a new DatadogClientImpl with custom HTTP client.
     * Useful for testing with custom base URLs.
     *
     * @param config the Datadog configuration
     * @param httpClient the HTTP client to use
     */
    public DatadogClientImpl(final DatadogConfig config, final HttpClient httpClient) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        this.baseUrlOverride = null;
    }

    /**
     * Creates a new DatadogClientImpl with custom base URL for testing.
     *
     * @param config the Datadog configuration
     * @param baseUrl the base URL to use instead of config's base URL
     */
    public DatadogClientImpl(final DatadogConfig config, final String baseUrl) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        this.baseUrlOverride = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
    }

    private String getBaseUrl() {
        return baseUrlOverride != null ? baseUrlOverride : config.baseUrl();
    }

    @Override
    public List<TraceSummary> searchErrorTraces(final TraceQuery query) {
        Objects.requireNonNull(query, "query must not be null");

        final String requestBody = buildSpanSearchRequest(query);
        final String url = getBaseUrl() + "/api/v2/spans/events/search";

        final HttpRequest request = buildPostRequest(url, requestBody);
        final TraceSearchResponse response = executeRequest(request, TraceSearchResponse.class);

        return mapToTraceSummaries(response);
    }

    @Override
    public TraceDetail getTraceDetail(
            final String traceId,
            final String service,
            final String env
    ) {
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(service, "service must not be null");

        final String url = getBaseUrl() + "/api/v1/trace/" + traceId;
        final HttpRequest request = buildGetRequest(url);
        final TraceDetailResponse response = executeRequest(request, TraceDetailResponse.class);

        return mapToTraceDetail(response, traceId, service, env != null ? env : "");
    }

    @Override
    public List<ServiceErrorView.LogEntry> searchLogsForTrace(
            final String traceId,
            final TraceQuery query
    ) {
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(query, "query must not be null");

        final String requestBody = buildLogSearchRequest(traceId, query);
        final String url = getBaseUrl() + "/api/v2/logs/events/search";

        final HttpRequest request = buildPostRequest(url, requestBody);
        final LogSearchResponse response = executeRequest(request, LogSearchResponse.class);

        return mapToLogEntries(response);
    }

    private String buildSpanSearchRequest(final TraceQuery query) {
        try {
            final Map<String, Object> filter = Map.of(
                    "from", query.from().toString(),
                    "to", query.to().toString(),
                    "query", query.toDatadogQuery()
            );

            final Map<String, Object> page = Map.of(
                    "limit", query.limit()
            );

            final Map<String, Object> attributes = Map.of(
                    "filter", filter,
                    "page", page,
                    "sort", "-timestamp"
            );

            final Map<String, Object> data = Map.of(
                    "type", "search_request",
                    "attributes", attributes
            );

            final Map<String, Object> body = Map.of("data", data);

            return objectMapper.writeValueAsString(body);
        } catch (final IOException e) {
            throw new DatadogApiException("Failed to build search request", e);
        }
    }

    private String buildLogSearchRequest(final String traceId, final TraceQuery query) {
        try {
            final Map<String, Object> filter = Map.of(
                    "from", query.from().toString(),
                    "to", query.to().toString(),
                    "query", "trace_id:" + traceId
            );

            final Map<String, Object> page = Map.of(
                    "limit", 100
            );

            final Map<String, Object> body = Map.of(
                    "filter", filter,
                    "page", page,
                    "sort", "timestamp"
            );

            return objectMapper.writeValueAsString(body);
        } catch (final IOException e) {
            throw new DatadogApiException("Failed to build log search request", e);
        }
    }

    private HttpRequest buildPostRequest(final String url, final String body) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("DD-API-KEY", config.apiKey())
                .header("DD-APPLICATION-KEY", config.appKey())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private HttpRequest buildGetRequest(final String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("DD-API-KEY", config.apiKey())
                .header("DD-APPLICATION-KEY", config.appKey())
                .GET()
                .build();
    }

    private <T> T executeRequest(final HttpRequest request, final Class<T> responseType) {
        try {
            final HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() >= 400) {
                throw new DatadogApiException(
                        "API request failed: " + response.body(),
                        response.statusCode()
                );
            }

            return objectMapper.readValue(response.body(), responseType);
        } catch (final IOException e) {
            throw new DatadogApiException("Failed to execute request", e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatadogApiException("Request interrupted", e);
        }
    }

    private List<TraceSummary> mapToTraceSummaries(final TraceSearchResponse response) {
        if (response == null || response.data() == null) {
            return List.of();
        }

        return response.data().stream()
                .filter(event -> event.attributes() != null)
                .map(event -> {
                    final TraceSearchResponse.SpanAttributes attrs = event.attributes();
                    return new TraceSummary(
                            attrs.traceId(),
                            attrs.service(),
                            attrs.resourceName(),
                            attrs.errorMessage(),
                            parseTimestamp(attrs.timestamp()),
                            attrs.duration() != null ? attrs.duration() : 0L
                    );
                })
                .toList();
    }

    private TraceDetail mapToTraceDetail(
            final TraceDetailResponse response,
            final String traceId,
            final String service,
            final String env
    ) {
        if (response == null || !response.hasData()) {
            return new TraceDetail(
                    traceId,
                    service,
                    env,
                    "",
                    Instant.now(),
                    0L,
                    List.of()
            );
        }

        final TraceDetailResponse.TraceAttributes attrs = response.data().attributes();

        final List<SpanDetail> spans = attrs.safeSpans().stream()
                .map(this::mapToSpanDetail)
                .toList();

        return new TraceDetail(
                attrs.traceId() != null ? attrs.traceId() : traceId,
                attrs.service() != null ? attrs.service() : service,
                attrs.env() != null ? attrs.env() : env,
                attrs.resourceName(),
                attrs.start() != null
                        ? Instant.ofEpochMilli(attrs.start() / 1_000_000)
                        : Instant.now(),
                attrs.duration() != null ? attrs.duration() : 0L,
                spans
        );
    }

    private SpanDetail mapToSpanDetail(final TraceDetailResponse.Span span) {
        return new SpanDetail(
                span.spanId(),
                span.parentId(),
                span.service(),
                span.operationName(),
                span.resource(),
                span.start() != null
                        ? Instant.ofEpochMilli(span.start() / 1_000_000)
                        : Instant.now(),
                span.duration() != null ? span.duration() : 0L,
                span.isError(),
                span.errorMessage(),
                span.errorType(),
                span.errorStack(),
                span.safeMeta()
        );
    }

    private List<ServiceErrorView.LogEntry> mapToLogEntries(final LogSearchResponse response) {
        return response.safeLogs().stream()
                .filter(log -> log.attributes() != null)
                .map(log -> {
                    final LogSearchResponse.LogAttributes attrs = log.attributes();
                    return new ServiceErrorView.LogEntry(
                            parseTimestamp(attrs.timestamp()),
                            attrs.level(),
                            attrs.message(),
                            attrs.flattenedAttributes()
                    );
                })
                .toList();
    }

    private Instant parseTimestamp(final String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(timestamp);
        } catch (final Exception e) {
            try {
                return Instant.ofEpochMilli(Long.parseLong(timestamp));
            } catch (final Exception e2) {
                return Instant.now();
            }
        }
    }
}
