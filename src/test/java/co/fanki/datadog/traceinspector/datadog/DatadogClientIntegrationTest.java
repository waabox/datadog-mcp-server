package co.fanki.datadog.traceinspector.datadog;

import co.fanki.datadog.traceinspector.domain.LogQuery;
import co.fanki.datadog.traceinspector.domain.LogSummary;
import co.fanki.datadog.traceinspector.domain.ServiceErrorView;
import co.fanki.datadog.traceinspector.domain.SpanDetail;
import co.fanki.datadog.traceinspector.domain.TraceDetail;
import co.fanki.datadog.traceinspector.domain.TraceQuery;
import co.fanki.datadog.traceinspector.domain.TraceSummary;
import com.datadog.api.client.ApiException;
import com.datadog.api.client.v2.api.LogsApi;
import com.datadog.api.client.v2.api.SpansApi;
import com.datadog.api.client.v2.model.Log;
import com.datadog.api.client.v2.model.LogAttributes;
import com.datadog.api.client.v2.model.LogsListResponse;
import com.datadog.api.client.v2.model.Span;
import com.datadog.api.client.v2.model.SpansAttributes;
import com.datadog.api.client.v2.model.SpansListRequest;
import com.datadog.api.client.v2.model.SpansListResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for DatadogClientImpl using mock SDK APIs.
 *
 * <p>These tests use mock implementations of SpansApi and LogsApi
 * to test the client without hitting the real Datadog API.</p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class DatadogClientIntegrationTest {

    private DatadogClient client;
    private MockSpansApi mockSpansApi;
    private MockLogsApi mockLogsApi;

    @BeforeEach
    void setUp() {
        mockSpansApi = new MockSpansApi();
        mockLogsApi = new MockLogsApi();
        client = new DatadogClientImpl(mockSpansApi, mockLogsApi);
    }

    @Test
    void whenSearchingErrorTraces_givenMockResponse_shouldReturnTraceSummaries() {
        mockSpansApi.setResponse(createSpansListResponse());

        final TraceQuery query = new TraceQuery(
                "user-service",
                "prod",
                Instant.parse("2024-01-15T10:00:00Z"),
                Instant.parse("2024-01-15T11:00:00Z"),
                20
        );

        final List<TraceSummary> traces = client.searchErrorTraces(query);

        assertNotNull(traces);
        assertEquals(2, traces.size());

        final TraceSummary first = traces.get(0);
        assertEquals("user-service", first.service());
        assertEquals("POST /api/users", first.resourceName());
    }

    @Test
    void whenGettingTraceDetail_givenMockResponse_shouldReturnFullTrace() {
        mockSpansApi.setResponse(createDetailedSpansListResponse());

        final TraceDetail trace = client.getTraceDetail(
                "trace-001",
                "user-service",
                "prod",
                Instant.parse("2024-01-15T10:00:00Z"),
                Instant.parse("2024-01-15T11:00:00Z")
        );

        assertNotNull(trace);
        assertEquals("trace-001", trace.traceId());
        assertEquals("user-service", trace.service());
        assertEquals("prod", trace.env());
        assertFalse(trace.spans().isEmpty());
    }

    @Test
    void whenSearchingLogs_givenMockResponse_shouldReturnLogEntries() {
        mockLogsApi.setResponse(createLogsListResponse());

        final TraceQuery query = new TraceQuery(
                "user-service",
                "prod",
                Instant.parse("2024-01-15T10:00:00Z"),
                Instant.parse("2024-01-15T11:00:00Z"),
                20
        );

        final List<ServiceErrorView.LogEntry> logs =
                client.searchLogsForTrace("trace-001", query);

        assertNotNull(logs);
        assertEquals(2, logs.size());

        final ServiceErrorView.LogEntry firstLog = logs.get(0);
        assertEquals("ERROR", firstLog.level());
        assertFalse(firstLog.message().isEmpty());
    }

    @Test
    void whenSearchingLogsWithLogQuery_givenMockResponse_shouldReturnLogSummaries() {
        mockLogsApi.setResponse(createLogsListResponse());

        final LogQuery query = new LogQuery(
                "user-service",
                "prod",
                Instant.parse("2024-01-15T10:00:00Z"),
                Instant.parse("2024-01-15T11:00:00Z"),
                null,
                "ERROR",
                100
        );

        final List<LogSummary> logs = client.searchLogs(query);

        assertNotNull(logs);
        assertEquals(2, logs.size());

        final LogSummary firstLog = logs.get(0);
        assertEquals("ERROR", firstLog.level());
        assertEquals("user-service", firstLog.service());
    }

    private SpansListResponse createSpansListResponse() {
        final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        final OffsetDateTime start = now.minusSeconds(1);

        final SpansAttributes attrs1 = new SpansAttributes()
                .traceId("trace-001")
                .spanId("span-1")
                .service("user-service")
                .resourceName("POST /api/users")
                .startTimestamp(start)
                .endTimestamp(now)
                .custom(Map.of(
                        "error.message", "Connection timeout"
                ));

        final SpansAttributes attrs2 = new SpansAttributes()
                .traceId("trace-002")
                .spanId("span-2")
                .service("user-service")
                .resourceName("GET /api/users/123")
                .startTimestamp(start)
                .endTimestamp(now);

        final Span span1 = new Span().id("span-1").attributes(attrs1);
        final Span span2 = new Span().id("span-2").attributes(attrs2);

        return new SpansListResponse().data(List.of(span1, span2));
    }

    private SpansListResponse createDetailedSpansListResponse() {
        final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        final OffsetDateTime start = now.minusSeconds(2);

        final SpansAttributes attrs1 = new SpansAttributes()
                .traceId("trace-001")
                .spanId("span-1")
                .service("api-gateway")
                .resourceName("POST /api/users")
                .startTimestamp(start)
                .endTimestamp(now)
                .type("web");

        final SpansAttributes attrs2 = new SpansAttributes()
                .traceId("trace-001")
                .spanId("span-2")
                .parentId("span-1")
                .service("user-service")
                .resourceName("createUser")
                .startTimestamp(start.plusNanos(100000000))
                .endTimestamp(now.minusNanos(100000000))
                .type("error")
                .custom(Map.of(
                        "error.message", "User validation failed"
                ));

        final Span span1 = new Span().id("span-1").attributes(attrs1);
        final Span span2 = new Span().id("span-2").attributes(attrs2);

        return new SpansListResponse().data(List.of(span1, span2));
    }

    private LogsListResponse createLogsListResponse() {
        final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        final LogAttributes attrs1 = new LogAttributes()
                .service("user-service")
                .message("User validation failed: email is required")
                .status("error")
                .host("prod-server-1")
                .timestamp(now)
                .attributes(Map.of("trace_id", "trace-001"));

        final LogAttributes attrs2 = new LogAttributes()
                .service("user-service")
                .message("Database connection timeout")
                .status("error")
                .host("prod-server-1")
                .timestamp(now.plusSeconds(1))
                .attributes(Map.of("trace_id", "trace-001"));

        final Log log1 = new Log().id("log-1").attributes(attrs1);
        final Log log2 = new Log().id("log-2").attributes(attrs2);

        return new LogsListResponse().data(List.of(log1, log2));
    }

    /**
     * Mock implementation of SpansApi for testing.
     */
    private static class MockSpansApi extends SpansApi {
        private SpansListResponse response;

        MockSpansApi() {
            super(null);
        }

        void setResponse(final SpansListResponse response) {
            this.response = response;
        }

        @Override
        public SpansListResponse listSpans(final SpansListRequest body) throws ApiException {
            return response;
        }
    }

    /**
     * Mock implementation of LogsApi for testing.
     */
    private static class MockLogsApi extends LogsApi {
        private LogsListResponse response;

        MockLogsApi() {
            super(null);
        }

        void setResponse(final LogsListResponse response) {
            this.response = response;
        }

        @Override
        public LogsListResponse listLogs(final ListLogsOptionalParameters parameters)
                throws ApiException {
            return response;
        }
    }
}
