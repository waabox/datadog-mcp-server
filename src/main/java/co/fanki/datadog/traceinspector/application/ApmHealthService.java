package co.fanki.datadog.traceinspector.application;

import co.fanki.datadog.traceinspector.datadog.ApmMetricsClient;
import co.fanki.datadog.traceinspector.domain.ApmMetrics;
import co.fanki.datadog.traceinspector.domain.ApmOperation;
import co.fanki.datadog.traceinspector.domain.ResourceSortCriteria;
import co.fanki.datadog.traceinspector.domain.ResourceStats;
import co.fanki.datadog.traceinspector.domain.ServiceHealth;
import co.fanki.datadog.traceinspector.domain.TimeWindow;
import co.fanki.datadog.traceinspector.domain.TopResources;

import java.util.List;
import java.util.Objects;

/**
 * Orchestrates APM health and resource ranking queries.
 *
 * <p>Resolves the operation name and fetches metrics. All comparison and ranking rules live in the
 * domain ({@link ServiceHealth}, {@link ResourceSortCriteria}).</p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public final class ApmHealthService {

    private final ApmMetricsClient metricsClient;

    /**
     * Creates a new ApmHealthService.
     *
     * @param metricsClient the APM metrics client
     */
    public ApmHealthService(final ApmMetricsClient metricsClient) {
        this.metricsClient = Objects.requireNonNull(metricsClient, "metricsClient must not be null");
    }

    /**
     * Returns the health of a service in a window compared with the previous window.
     *
     * @param service the service name, must not be blank
     * @param env the environment, must not be blank
     * @param window the current window
     * @param operationOverride the operation name to use; null or blank to detect it
     *
     * @return the service health
     *
     * @throws IllegalArgumentException if service or env is blank
     * @throws IllegalStateException if no operation is given and none can be detected
     */
    public ServiceHealth serviceHealth(
            final String service,
            final String env,
            final TimeWindow window,
            final String operationOverride
    ) {
        requireNotBlank(service, "service");
        requireNotBlank(env, "env");
        Objects.requireNonNull(window, "window must not be null");

        final ApmOperation operation = resolveOperation(service, env, window, operationOverride);
        final ApmMetrics current = metricsClient.queryServiceMetrics(service, env, operation.name(), window);
        final TimeWindow baselineWindow = window.previous();
        final ApmMetrics baseline = metricsClient.queryServiceMetrics(service, env, operation.name(), baselineWindow);

        return new ServiceHealth(service, env, operation, window, current, baseline);
    }

    /**
     * Returns a service's resources ranked by the given criteria.
     *
     * @param service the service name, must not be blank
     * @param env the environment, must not be blank
     * @param window the window
     * @param operationOverride the operation name to use; null or blank to detect it
     * @param sortBy the ranking criteria
     * @param limit the maximum number of resources, between 1 and {@link ResourceSortCriteria#MAX_LIMIT}
     *
     * @return the ranked resources
     *
     * @throws IllegalArgumentException if service or env is blank, or limit is out of range
     * @throws IllegalStateException if no operation is given and none can be detected
     */
    public TopResources topResources(
            final String service,
            final String env,
            final TimeWindow window,
            final String operationOverride,
            final ResourceSortCriteria sortBy,
            final int limit
    ) {
        requireNotBlank(service, "service");
        requireNotBlank(env, "env");
        Objects.requireNonNull(window, "window must not be null");
        Objects.requireNonNull(sortBy, "sortBy must not be null");
        ResourceSortCriteria.requireValidLimit(limit);

        final ApmOperation operation = resolveOperation(service, env, window, operationOverride);
        final List<ResourceStats> stats = metricsClient.queryResourceMetrics(service, env, operation.name(), window);

        return new TopResources(service, env, operation, window, sortBy, sortBy.rank(stats, limit));
    }

    private ApmOperation resolveOperation(
            final String service,
            final String env,
            final TimeWindow window,
            final String operationOverride
    ) {
        if (operationOverride != null && !operationOverride.isBlank()) {
            return ApmOperation.provided(operationOverride.trim());
        }
        return metricsClient.detectEntryOperation(service, env, window)
                .map(ApmOperation::detected)
                .orElseThrow(() -> new IllegalStateException(
                        "No entry spans found for service " + service + " in env " + env
                                + "; pass 'operation' explicitly"));
    }

    private static void requireNotBlank(final String value, final String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
