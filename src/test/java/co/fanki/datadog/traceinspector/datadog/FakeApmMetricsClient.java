package co.fanki.datadog.traceinspector.datadog;

import co.fanki.datadog.traceinspector.domain.ApmMetrics;
import co.fanki.datadog.traceinspector.domain.ResourceStats;
import co.fanki.datadog.traceinspector.domain.TimeWindow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory ApmMetricsClient for tests.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public final class FakeApmMetricsClient implements ApmMetricsClient {

    private final Map<TimeWindow, ApmMetrics> metricsByWindow = new HashMap<>();
    private final List<TimeWindow> queriedWindows = new ArrayList<>();
    private List<ResourceStats> resources = List.of();
    private String detectedOperation;
    private String lastOperation;
    private int detectCalls;
    private TimeWindow lastDetectionWindow;

    /**
     * Sets the operation returned by detection; null means no entry span found.
     *
     * @param operation the operation name
     */
    public void detectedOperation(final String operation) {
        this.detectedOperation = operation;
    }

    /**
     * Sets the metrics returned for a window.
     *
     * @param window the window
     * @param metrics the metrics
     */
    public void metricsFor(final TimeWindow window, final ApmMetrics metrics) {
        metricsByWindow.put(window, metrics);
    }

    /**
     * Sets the resources returned by queryResourceMetrics.
     *
     * @param resources the resources
     */
    public void resources(final List<ResourceStats> resources) {
        this.resources = List.copyOf(resources);
    }

    /**
     * Returns how many times detection was called.
     *
     * @return the call count
     */
    public int detectCalls() {
        return detectCalls;
    }

    /**
     * Returns the windows passed to the metric queries, in call order.
     *
     * @return the windows
     */
    public List<TimeWindow> queriedWindows() {
        return List.copyOf(queriedWindows);
    }

    /**
     * Returns the operation passed to the last metric query.
     *
     * @return the operation name, or null if no query ran
     */
    public String lastOperation() {
        return lastOperation;
    }

    /**
     * Returns the window passed to the last detection call.
     *
     * @return the window, or null if detection was never called
     */
    public TimeWindow lastDetectionWindow() {
        return lastDetectionWindow;
    }

    @Override
    public Optional<String> detectEntryOperation(final String service, final String env, final TimeWindow window) {
        detectCalls++;
        lastDetectionWindow = window;
        return Optional.ofNullable(detectedOperation);
    }

    @Override
    public ApmMetrics queryServiceMetrics(
            final String service,
            final String env,
            final String operation,
            final TimeWindow window
    ) {
        lastOperation = operation;
        queriedWindows.add(window);
        return metricsByWindow.getOrDefault(window, ApmMetrics.empty());
    }

    @Override
    public List<ResourceStats> queryResourceMetrics(
            final String service,
            final String env,
            final String operation,
            final TimeWindow window
    ) {
        lastOperation = operation;
        queriedWindows.add(window);
        return resources;
    }
}
