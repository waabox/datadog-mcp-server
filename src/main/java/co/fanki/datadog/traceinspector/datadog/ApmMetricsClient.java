package co.fanki.datadog.traceinspector.datadog;

import co.fanki.datadog.traceinspector.domain.ApmMetrics;
import co.fanki.datadog.traceinspector.domain.ResourceStats;
import co.fanki.datadog.traceinspector.domain.TimeWindow;

import java.util.List;
import java.util.Optional;

/**
 * Reads APM trace metrics from Datadog.
 *
 * <p>Numbers always come from trace metrics ({@code trace.<operation>.*}), which Datadog computes
 * on 100% of ingested traffic. Indexed spans are only used to detect the operation name.</p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public interface ApmMetricsClient {

    /**
     * Detects the operation name of a service's entry spans.
     *
     * <p>Looks up the most recent service entry span ({@code span.kind:server} and top level) in the
     * window. The top-level filter skips inner server spans such as {@code spring.handler}.</p>
     *
     * @param service the service name
     * @param env the environment
     * @param window the window to search in
     *
     * @return the operation name, or empty if no entry span exists in the window
     *
     * @throws DatadogApiException if the API call fails after retries
     */
    Optional<String> detectEntryOperation(String service, String env, TimeWindow window);

    /**
     * Queries hits, errors and latency percentiles for a service.
     *
     * @param service the service name
     * @param env the environment
     * @param operation the operation name, such as {@code servlet.request}
     * @param window the window to aggregate over
     *
     * @return the metrics; zero counts and null latency when there is no data
     *
     * @throws DatadogApiException if the API call fails after retries
     */
    ApmMetrics queryServiceMetrics(String service, String env, String operation, TimeWindow window);

    /**
     * Queries hits, errors and latency percentiles for each resource of a service.
     *
     * @param service the service name
     * @param env the environment
     * @param operation the operation name
     * @param window the window to aggregate over
     *
     * @return one entry per resource, in the order returned by Datadog; empty when there is no data
     *
     * @throws DatadogApiException if the API call fails after retries
     */
    List<ResourceStats> queryResourceMetrics(String service, String env, String operation, TimeWindow window);
}
