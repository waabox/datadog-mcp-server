package co.fanki.datadog.traceinspector.datadog;

import co.fanki.datadog.traceinspector.config.DatadogConfig;
import co.fanki.datadog.traceinspector.domain.ServiceErrorView;
import co.fanki.datadog.traceinspector.domain.TraceDetail;
import co.fanki.datadog.traceinspector.domain.TraceQuery;
import co.fanki.datadog.traceinspector.domain.TraceSummary;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for DatadogClientImpl using Testcontainers with nginx.
 *
 * <p>These tests spin up an nginx container that serves mock Datadog API
 * responses, allowing us to test the HTTP client without hitting the
 * real Datadog API.</p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
@Testcontainers
class DatadogClientIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    private static final GenericContainer<?> nginx = new GenericContainer<>("nginx:alpine")
            .withExposedPorts(80)
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("docker/nginx.conf"),
                    "/etc/nginx/conf.d/default.conf"
            )
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("mock-responses/trace-search-response.json"),
                    "/mock-responses/trace-search-response.json"
            )
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("mock-responses/trace-detail-response.json"),
                    "/mock-responses/trace-detail-response.json"
            )
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("mock-responses/log-search-response.json"),
                    "/mock-responses/log-search-response.json"
            )
            .waitingFor(Wait.forHttp("/health").forStatusCode(200));

    private DatadogClient client;

    @BeforeEach
    void setUp() {
        final String baseUrl = "http://" + nginx.getHost() + ":" + nginx.getMappedPort(80);

        // Create config with test credentials
        final DatadogConfig config = new DatadogConfig(
                "test-api-key",
                "test-app-key",
                "datadoghq.com",
                "prod"
        );

        // Use the base URL override constructor for testing
        client = new DatadogClientImpl(config, baseUrl);
    }

    @Test
    void whenSearchingErrorTraces_givenMockResponse_shouldReturnTraceSummaries() {
        final TraceQuery query = new TraceQuery(
                "user-service",
                "prod",
                Instant.parse("2024-01-15T10:00:00Z"),
                Instant.parse("2024-01-15T11:00:00Z"),
                20
        );

        final List<TraceSummary> traces = client.searchErrorTraces(query);

        assertNotNull(traces);
        assertEquals(3, traces.size());

        final TraceSummary first = traces.get(0);
        assertEquals("trace-001", first.traceId());
        assertEquals("user-service", first.service());
        assertEquals("POST /api/users", first.resourceName());
        assertFalse(first.errorMessage().isEmpty());
    }

    @Test
    void whenGettingTraceDetail_givenMockResponse_shouldReturnFullTrace() {
        final TraceDetail trace = client.getTraceDetail(
                "trace-001",
                "user-service",
                "prod"
        );

        assertNotNull(trace);
        assertEquals("trace-001", trace.traceId());
        assertEquals("user-service", trace.service());
        assertEquals("prod", trace.env());
        assertEquals(4, trace.spans().size());
        assertTrue(trace.hasErrors());
    }

    @Test
    void whenGettingTraceDetail_givenMockResponse_shouldHaveCorrectServices() {
        final TraceDetail trace = client.getTraceDetail(
                "trace-001",
                "user-service",
                "prod"
        );

        final var services = trace.involvedServices();

        assertEquals(3, services.size());
        assertTrue(services.contains("api-gateway"));
        assertTrue(services.contains("user-service"));
        assertTrue(services.contains("postgres"));
    }

    @Test
    void whenGettingTraceDetail_givenMockResponse_shouldHaveErrorSpans() {
        final TraceDetail trace = client.getTraceDetail(
                "trace-001",
                "user-service",
                "prod"
        );

        final var errorSpans = trace.errorSpans();

        assertEquals(2, errorSpans.size());
        assertTrue(errorSpans.stream().allMatch(span -> span.isError()));
    }

    @Test
    void whenSearchingLogs_givenMockResponse_shouldReturnLogEntries() {
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
        assertEquals(3, logs.size());

        final ServiceErrorView.LogEntry firstLog = logs.get(0);
        assertEquals("ERROR", firstLog.level());
        assertFalse(firstLog.message().isEmpty());
    }

    @Test
    void whenSearchingLogs_givenMockResponse_shouldHaveCorrectAttributes() {
        final TraceQuery query = new TraceQuery(
                "user-service",
                "prod",
                Instant.parse("2024-01-15T10:00:00Z"),
                Instant.parse("2024-01-15T11:00:00Z"),
                20
        );

        final List<ServiceErrorView.LogEntry> logs =
                client.searchLogsForTrace("trace-001", query);

        final ServiceErrorView.LogEntry firstLog = logs.get(0);
        assertNotNull(firstLog.attributes());
        assertFalse(firstLog.attributes().isEmpty());
    }
}
