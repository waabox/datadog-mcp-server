package co.fanki.datadog.traceinspector.datadog;

import co.fanki.datadog.traceinspector.domain.ApmMetrics;
import co.fanki.datadog.traceinspector.domain.ResourceStats;
import co.fanki.datadog.traceinspector.domain.TimeWindow;
import com.datadog.api.client.v2.api.MetricsApi;
import com.datadog.api.client.v2.api.SpansApi;
import com.datadog.api.client.v2.model.DataScalarColumn;
import com.datadog.api.client.v2.model.GroupScalarColumn;
import com.datadog.api.client.v2.model.MetricsAggregator;
import com.datadog.api.client.v2.model.MetricsScalarQuery;
import com.datadog.api.client.v2.model.ScalarColumn;
import com.datadog.api.client.v2.model.ScalarFormulaQueryRequest;
import com.datadog.api.client.v2.model.ScalarFormulaQueryResponse;
import com.datadog.api.client.v2.model.ScalarFormulaRequestAttributes;
import com.datadog.api.client.v2.model.ScalarFormulaResponseAtrributes;
import com.datadog.api.client.v2.model.ScalarQuery;
import com.datadog.api.client.v2.model.ScalarResponse;
import com.datadog.api.client.v2.model.Span;
import com.datadog.api.client.v2.model.SpansAttributes;
import com.datadog.api.client.v2.model.SpansListRequest;
import com.datadog.api.client.v2.model.SpansListResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for ApmMetricsClientImpl using stubbed SDK APIs.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class ApmMetricsClientImplTest {

    private static final TimeWindow WINDOW = new TimeWindow(
            Instant.parse("2026-09-23T13:00:00Z"),
            Instant.parse("2026-09-23T14:00:00Z")
    );

    private StubMetricsApi metricsApi;
    private StubSpansApi spansApi;
    private ApmMetricsClient client;

    @BeforeEach
    void setUp() {
        metricsApi = new StubMetricsApi();
        spansApi = new StubSpansApi();
        client = new ApmMetricsClientImpl(metricsApi, spansApi, RetryConfig.defaults());
    }

    @Test
    void whenQueryingServiceMetrics_givenFullResponse_shouldMapCountsAndConvertLatencyToMillis() {
        metricsApi.response = response(
                data("hits", 12000.0), data("errors", 340.0),
                data("p50", 0.045), data("p95", 0.31), data("p99", 0.9));

        final ApmMetrics metrics = client.queryServiceMetrics("payments", "prod", "servlet.request", WINDOW);

        assertEquals(12000L, metrics.hits());
        assertEquals(340L, metrics.errors());
        assertEquals(45.0, metrics.latencyP50(), 0.0001);
        assertEquals(310.0, metrics.latencyP95(), 0.0001);
        assertEquals(900.0, metrics.latencyP99(), 0.0001);
    }

    @Test
    void whenQueryingServiceMetrics_shouldBuildTraceMetricQueries() {
        metricsApi.response = response();

        client.queryServiceMetrics("payments", "prod", "servlet.request", WINDOW);

        final ScalarFormulaRequestAttributes attributes = metricsApi.lastRequest.getData().getAttributes();
        assertEquals(WINDOW.from().toEpochMilli(), attributes.getFrom());
        assertEquals(WINDOW.to().toEpochMilli(), attributes.getTo());

        final List<MetricsScalarQuery> queries = attributes.getQueries().stream()
                .map(ScalarQuery::getMetricsScalarQuery)
                .toList();
        assertEquals(List.of("hits", "errors", "p50", "p95", "p99"),
                queries.stream().map(MetricsScalarQuery::getName).toList());
        assertEquals("sum:trace.servlet.request.hits{service:payments,env:prod}.as_count()",
                queries.get(0).getQuery());
        assertEquals("sum:trace.servlet.request.errors{service:payments,env:prod}.as_count()",
                queries.get(1).getQuery());
        assertEquals("p95:trace.servlet.request{service:payments,env:prod}", queries.get(3).getQuery());
        assertEquals(MetricsAggregator.SUM, queries.get(0).getAggregator());
        assertEquals(MetricsAggregator.PERCENTILE, queries.get(3).getAggregator());
    }

    @Test
    void whenQueryingServiceMetrics_givenNoLatencyColumns_shouldReturnNullLatency() {
        metricsApi.response = response(data("hits", 10.0), data("errors", 1.0));

        final ApmMetrics metrics = client.queryServiceMetrics("payments", "prod", "servlet.request", WINDOW);

        assertEquals(10L, metrics.hits());
        assertNull(metrics.latencyP95());
    }

    @Test
    void whenQueryingServiceMetrics_givenEmptyResponse_shouldReturnEmptyMetrics() {
        metricsApi.response = new ScalarFormulaQueryResponse();

        final ApmMetrics metrics = client.queryServiceMetrics("payments", "prod", "servlet.request", WINDOW);

        assertEquals(ApmMetrics.empty(), metrics);
    }

    @Test
    void whenQueryingServiceMetrics_givenUnnamedColumns_shouldMapByPosition() {
        metricsApi.response = response(
                data("query1", 100.0), data("query2", 5.0),
                data("query3", 0.01), data("query4", 0.02), data("query5", 0.03));

        final ApmMetrics metrics = client.queryServiceMetrics("payments", "prod", "servlet.request", WINDOW);

        assertEquals(100L, metrics.hits());
        assertEquals(5L, metrics.errors());
        assertEquals(20.0, metrics.latencyP95(), 0.0001);
    }

    @Test
    void whenQueryingResourceMetrics_shouldGroupByResourceName() {
        metricsApi.response = response();

        client.queryResourceMetrics("payments", "prod", "servlet.request", WINDOW);

        final MetricsScalarQuery hits = metricsApi.lastRequest.getData().getAttributes().getQueries().get(0)
                .getMetricsScalarQuery();
        assertEquals("sum:trace.servlet.request.hits{service:payments,env:prod} by {resource_name}.as_count()",
                hits.getQuery());
    }

    @Test
    void whenQueryingResourceMetrics_givenGroupedResponse_shouldMapOneStatsPerRow() {
        metricsApi.response = response(
                group("GET /a", "resource_name:POST /b"),
                data("hits", 100.0, 50.0),
                data("errors", 5.0, null),
                data("p95", 0.2, null));

        final List<ResourceStats> stats = client.queryResourceMetrics("payments", "prod", "servlet.request", WINDOW);

        assertEquals(2, stats.size());
        assertEquals("GET /a", stats.get(0).resource());
        assertEquals(5L, stats.get(0).metrics().errors());
        assertEquals(200.0, stats.get(0).metrics().latencyP95(), 0.0001);
        assertEquals("POST /b", stats.get(1).resource());
        assertEquals(0L, stats.get(1).metrics().errors());
        assertNull(stats.get(1).metrics().latencyP95());
    }

    @Test
    void whenQueryingResourceMetrics_givenEmptyResponse_shouldReturnEmptyList() {
        metricsApi.response = new ScalarFormulaQueryResponse();

        assertTrue(client.queryResourceMetrics("payments", "prod", "servlet.request", WINDOW).isEmpty());
    }

    @Test
    void whenDetectingOperation_givenSpanWithOperationName_shouldReturnIt() {
        spansApi.response = spans(new SpansAttributes().attributes(Map.of("operation_name", "servlet.request")));

        final Optional<String> operation = client.detectEntryOperation("payments", "prod", WINDOW);

        assertEquals(Optional.of("servlet.request"), operation);
        assertEquals("service:payments env:prod @span.kind:server",
                spansApi.lastRequest.getData().getAttributes().getFilter().getQuery());
        assertEquals(1, spansApi.lastRequest.getData().getAttributes().getPage().getLimit());
    }

    @Test
    void whenDetectingOperation_givenOperationNameInCustomAttributes_shouldReturnIt() {
        spansApi.response = spans(new SpansAttributes().custom(Map.of("operation_name", "next.request")));

        assertEquals(Optional.of("next.request"), client.detectEntryOperation("portal", "prod", WINDOW));
    }

    @Test
    void whenDetectingOperation_givenNoSpans_shouldReturnEmpty() {
        spansApi.response = new SpansListResponse().data(List.of());

        assertEquals(Optional.empty(), client.detectEntryOperation("payments", "prod", WINDOW));
    }

    private static ScalarColumn data(final String name, final Double... values) {
        return new ScalarColumn(new DataScalarColumn().name(name).values(Arrays.asList(values)));
    }

    private static ScalarColumn group(final String... values) {
        final List<List<String>> rows = new ArrayList<>();
        for (final String value : values) {
            rows.add(List.of(value));
        }
        return new ScalarColumn(new GroupScalarColumn().name("resource_name").values(rows));
    }

    private static ScalarFormulaQueryResponse response(final ScalarColumn... columns) {
        return new ScalarFormulaQueryResponse().data(new ScalarResponse()
                .attributes(new ScalarFormulaResponseAtrributes().columns(List.of(columns))));
    }

    private static SpansListResponse spans(final SpansAttributes attributes) {
        return new SpansListResponse().data(List.of(new Span().attributes(attributes)));
    }

    private static final class StubMetricsApi extends MetricsApi {
        private ScalarFormulaQueryResponse response = new ScalarFormulaQueryResponse();
        private ScalarFormulaQueryRequest lastRequest;

        @Override
        public ScalarFormulaQueryResponse queryScalarData(final ScalarFormulaQueryRequest body) {
            lastRequest = body;
            return response;
        }
    }

    private static final class StubSpansApi extends SpansApi {
        private SpansListResponse response = new SpansListResponse();
        private SpansListRequest lastRequest;

        @Override
        public SpansListResponse listSpans(final SpansListRequest body) {
            lastRequest = body;
            return response;
        }
    }
}
