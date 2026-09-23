package co.fanki.datadog.traceinspector.domain;

import java.util.Objects;

/**
 * APM metrics for one resource (endpoint) of a service.
 *
 * @param resource the resource name, such as {@code POST /api/checkout}; never blank
 * @param metrics the metrics for this resource
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public record ResourceStats(String resource, ApmMetrics metrics) {

    /**
     * Creates new ResourceStats.
     *
     * @throws IllegalArgumentException if resource is blank
     */
    public ResourceStats {
        Objects.requireNonNull(resource, "resource must not be null");
        Objects.requireNonNull(metrics, "metrics must not be null");
        if (resource.isBlank()) {
            throw new IllegalArgumentException("resource must not be blank");
        }
    }
}
