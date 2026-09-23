package co.fanki.datadog.traceinspector.datadog;

import co.fanki.datadog.traceinspector.config.DatadogConfig;
import co.fanki.datadog.traceinspector.domain.ApmMetrics;
import co.fanki.datadog.traceinspector.domain.ResourceStats;
import co.fanki.datadog.traceinspector.domain.TimeWindow;
import com.datadog.api.client.ApiClient;
import com.datadog.api.client.v2.api.MetricsApi;
import com.datadog.api.client.v2.api.SpansApi;
import com.datadog.api.client.v2.model.DataScalarColumn;
import com.datadog.api.client.v2.model.GroupScalarColumn;
import com.datadog.api.client.v2.model.MetricsAggregator;
import com.datadog.api.client.v2.model.MetricsDataSource;
import com.datadog.api.client.v2.model.MetricsScalarQuery;
import com.datadog.api.client.v2.model.ScalarColumn;
import com.datadog.api.client.v2.model.ScalarFormulaQueryRequest;
import com.datadog.api.client.v2.model.ScalarFormulaQueryResponse;
import com.datadog.api.client.v2.model.ScalarFormulaRequest;
import com.datadog.api.client.v2.model.ScalarFormulaRequestAttributes;
import com.datadog.api.client.v2.model.ScalarFormulaRequestType;
import com.datadog.api.client.v2.model.ScalarQuery;
import com.datadog.api.client.v2.model.SpansAttributes;
import com.datadog.api.client.v2.model.SpansListRequest;
import com.datadog.api.client.v2.model.SpansListRequestAttributes;
import com.datadog.api.client.v2.model.SpansListRequestData;
import com.datadog.api.client.v2.model.SpansListRequestPage;
import com.datadog.api.client.v2.model.SpansListRequestType;
import com.datadog.api.client.v2.model.SpansListResponse;
import com.datadog.api.client.v2.model.SpansQueryFilter;
import com.datadog.api.client.v2.model.SpansSort;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * SDK-based implementation of {@link ApmMetricsClient}.
 *
 * <p>Uses the Metrics API v2 scalar endpoint for numbers and the Spans API to detect the
 * operation name. All calls go through {@link RetryExecutor}.</p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public final class ApmMetricsClientImpl implements ApmMetricsClient {

    private static final String HITS = "hits";
    private static final String ERRORS = "errors";
    private static final String P50 = "p50";
    private static final String P95 = "p95";
    private static final String P99 = "p99";
    private static final List<String> QUERY_NAMES = List.of(HITS, ERRORS, P50, P95, P99);

    private static final String RESOURCE_TAG = "resource_name";
    private static final String OPERATION_NAME = "operation_name";
    private static final double SECONDS_TO_MILLIS = 1000.0;
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final MetricsApi metricsApi;
    private final SpansApi spansApi;
    private final RetryExecutor retryExecutor;

    /**
     * Creates a new ApmMetricsClientImpl with the given configuration.
     *
     * @param config the Datadog configuration
     */
    public ApmMetricsClientImpl(final DatadogConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        final ApiClient apiClient = config.buildApiClient();
        this.metricsApi = new MetricsApi(apiClient);
        this.spansApi = new SpansApi(apiClient);
        this.retryExecutor = new RetryExecutor(RetryConfig.defaults());
    }

    /**
     * Creates a new ApmMetricsClientImpl with custom API instances. Useful for testing.
     *
     * @param metricsApi the metrics API instance
     * @param spansApi the spans API instance
     * @param retryConfig the retry configuration
     */
    public ApmMetricsClientImpl(final MetricsApi metricsApi, final SpansApi spansApi, final RetryConfig retryConfig) {
        this.metricsApi = Objects.requireNonNull(metricsApi, "metricsApi must not be null");
        this.spansApi = Objects.requireNonNull(spansApi, "spansApi must not be null");
        this.retryExecutor = new RetryExecutor(Objects.requireNonNull(retryConfig, "retryConfig must not be null"));
    }

    @Override
    public Optional<String> detectEntryOperation(final String service, final String env, final TimeWindow window) {
        Objects.requireNonNull(service, "service must not be null");
        Objects.requireNonNull(env, "env must not be null");
        Objects.requireNonNull(window, "window must not be null");

        final SpansQueryFilter filter = new SpansQueryFilter()
                .query("service:" + service + " env:" + env + " @span.kind:server")
                .from(ISO_FORMATTER.format(window.from()))
                .to(ISO_FORMATTER.format(window.to()));

        final SpansListRequest request = new SpansListRequest()
                .data(new SpansListRequestData()
                        .type(SpansListRequestType.SEARCH_REQUEST)
                        .attributes(new SpansListRequestAttributes()
                                .filter(filter)
                                .page(new SpansListRequestPage().limit(1))
                                .sort(SpansSort.TIMESTAMP_DESCENDING)));

        final SpansListResponse response = retryExecutor.execute(
                () -> spansApi.listSpans(request),
                "detect entry operation"
        );

        if (response == null || response.getData() == null || response.getData().isEmpty()) {
            return Optional.empty();
        }
        final SpansAttributes attributes = response.getData().get(0).getAttributes();
        if (attributes == null) {
            return Optional.empty();
        }
        final Optional<String> fromAttributes = readString(attributes.getAttributes(), OPERATION_NAME);
        if (fromAttributes.isPresent()) {
            return fromAttributes;
        }
        return readString(attributes.getCustom(), OPERATION_NAME);
    }

    @Override
    public ApmMetrics queryServiceMetrics(
            final String service,
            final String env,
            final String operation,
            final TimeWindow window
    ) {
        final ScalarFormulaQueryResponse response = queryScalar(
                buildRequest(service, env, operation, window, ""),
                "query service metrics"
        );
        return toMetrics(dataColumns(response), 0);
    }

    @Override
    public List<ResourceStats> queryResourceMetrics(
            final String service,
            final String env,
            final String operation,
            final TimeWindow window
    ) {
        final ScalarFormulaQueryResponse response = queryScalar(
                buildRequest(service, env, operation, window, " by {" + RESOURCE_TAG + "}"),
                "query resource metrics"
        );
        final List<String> resources = groupValues(response);
        final Map<String, List<Double>> columns = dataColumns(response);

        final List<ResourceStats> stats = new ArrayList<>();
        for (int row = 0; row < resources.size(); row++) {
            final String resource = resources.get(row);
            if (!resource.isBlank()) {
                stats.add(new ResourceStats(resource, toMetrics(columns, row)));
            }
        }
        return List.copyOf(stats);
    }

    private ScalarFormulaQueryRequest buildRequest(
            final String service,
            final String env,
            final String operation,
            final TimeWindow window,
            final String groupBy
    ) {
        Objects.requireNonNull(service, "service must not be null");
        Objects.requireNonNull(env, "env must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(window, "window must not be null");

        final String scope = "{service:" + service + ",env:" + env + "}" + groupBy;
        final String metric = "trace." + operation;

        final List<ScalarQuery> queries = List.of(
                query(HITS, "sum:" + metric + ".hits" + scope + ".as_count()", MetricsAggregator.SUM),
                query(ERRORS, "sum:" + metric + ".errors" + scope + ".as_count()", MetricsAggregator.SUM),
                query(P50, "p50:" + metric + scope, MetricsAggregator.PERCENTILE),
                query(P95, "p95:" + metric + scope, MetricsAggregator.PERCENTILE),
                query(P99, "p99:" + metric + scope, MetricsAggregator.PERCENTILE)
        );

        return new ScalarFormulaQueryRequest()
                .data(new ScalarFormulaRequest()
                        .type(ScalarFormulaRequestType.SCALAR_REQUEST)
                        .attributes(new ScalarFormulaRequestAttributes()
                                .from(window.from().toEpochMilli())
                                .to(window.to().toEpochMilli())
                                .queries(queries)));
    }

    private static ScalarQuery query(final String name, final String query, final MetricsAggregator aggregator) {
        return new ScalarQuery(new MetricsScalarQuery()
                .name(name)
                .query(query)
                .aggregator(aggregator)
                .dataSource(MetricsDataSource.METRICS));
    }

    private ScalarFormulaQueryResponse queryScalar(
            final ScalarFormulaQueryRequest request,
            final String operationName
    ) {
        final ScalarFormulaQueryResponse response = retryExecutor.execute(
                () -> metricsApi.queryScalarData(request),
                operationName
        );
        if (response != null && response.getData() == null
                && response.getErrors() != null && !response.getErrors().isBlank()) {
            throw new DatadogApiException("Metrics query failed: " + response.getErrors());
        }
        return response;
    }

    private static List<ScalarColumn> columns(final ScalarFormulaQueryResponse response) {
        if (response == null || response.getData() == null || response.getData().getAttributes() == null
                || response.getData().getAttributes().getColumns() == null) {
            return List.of();
        }
        return response.getData().getAttributes().getColumns();
    }

    private static Map<String, List<Double>> dataColumns(final ScalarFormulaQueryResponse response) {
        final List<DataScalarColumn> dataColumns = new ArrayList<>();
        for (final ScalarColumn column : columns(response)) {
            if (column.getActualInstance() instanceof DataScalarColumn data) {
                dataColumns.add(data);
            }
        }

        final boolean anyNamed = dataColumns.stream()
                .anyMatch(data -> data.getName() != null && QUERY_NAMES.contains(data.getName()));
        final boolean usePositional = !anyNamed && dataColumns.size() == QUERY_NAMES.size();

        final Map<String, List<Double>> byName = new HashMap<>();
        for (int position = 0; position < dataColumns.size(); position++) {
            final DataScalarColumn data = dataColumns.get(position);
            final List<Double> values = data.getValues() != null ? data.getValues() : List.of();
            if (data.getName() != null && QUERY_NAMES.contains(data.getName())) {
                byName.put(data.getName(), values);
            } else if (usePositional) {
                byName.put(QUERY_NAMES.get(position), values);
            }
        }
        return byName;
    }

    private static List<String> groupValues(final ScalarFormulaQueryResponse response) {
        for (final ScalarColumn column : columns(response)) {
            if (column.getActualInstance() instanceof GroupScalarColumn group && group.getValues() != null) {
                final List<String> values = new ArrayList<>();
                for (final List<String> row : group.getValues()) {
                    values.add(row == null || row.isEmpty() || row.get(0) == null ? "" : stripTag(row.get(0)));
                }
                return values;
            }
        }
        return List.of();
    }

    private static String stripTag(final String value) {
        final String prefix = RESOURCE_TAG + ":";
        return value.startsWith(prefix) ? value.substring(prefix.length()) : value;
    }

    private static ApmMetrics toMetrics(final Map<String, List<Double>> columns, final int row) {
        return new ApmMetrics(
                toCount(value(columns, HITS, row)),
                toCount(value(columns, ERRORS, row)),
                toMillis(value(columns, P50, row)),
                toMillis(value(columns, P95, row)),
                toMillis(value(columns, P99, row))
        );
    }

    private static Double value(final Map<String, List<Double>> columns, final String name, final int row) {
        final List<Double> values = columns.get(name);
        if (values == null || row >= values.size()) {
            return null;
        }
        return values.get(row);
    }

    private static long toCount(final Double value) {
        return value == null ? 0L : Math.max(0L, Math.round(value));
    }

    private static Double toMillis(final Double seconds) {
        return seconds == null ? null : seconds * SECONDS_TO_MILLIS;
    }

    private static Optional<String> readString(final Map<String, Object> map, final String key) {
        if (map == null) {
            return Optional.empty();
        }
        final Object value = map.get(key);
        if (value == null || value.toString().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.toString());
    }
}
