package co.fanki.datadog.traceinspector.domain;

import java.util.List;
import java.util.Objects;

/**
 * A service's resources ranked by one criteria over one time window.
 *
 * @param service the service name
 * @param env the environment
 * @param operation the APM operation the metrics belong to
 * @param window the queried window
 * @param sortBy the ranking criteria
 * @param resources the ranked resources
 * @param queriedCount the number of resources returned by Datadog before ranking and filtering
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public record TopResources(
        String service,
        String env,
        ApmOperation operation,
        TimeWindow window,
        ResourceSortCriteria sortBy,
        List<ResourceStats> resources,
        int queriedCount
) {

    /**
     * Creates new TopResources.
     *
     * @throws NullPointerException if any argument is null
     * @throws IllegalArgumentException if queriedCount is negative
     */
    public TopResources {
        Objects.requireNonNull(service, "service must not be null");
        Objects.requireNonNull(env, "env must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(window, "window must not be null");
        Objects.requireNonNull(sortBy, "sortBy must not be null");
        resources = List.copyOf(Objects.requireNonNull(resources, "resources must not be null"));
        if (queriedCount < 0) {
            throw new IllegalArgumentException("queriedCount must not be negative");
        }
    }

    /**
     * Returns explanations about missing data (BR-21) or about resources excluded by the errorRate
     * ranking's minimum-hits filter.
     *
     * @return the notes, possibly empty
     */
    public List<String> notes() {
        if (queriedCount == 0) {
            return List.of("no trace metrics found for operation " + operation.name());
        }
        if (resources.isEmpty() && sortBy == ResourceSortCriteria.ERROR_RATE) {
            return List.of(queriedCount + " resources excluded by errorRate ranking (fewer than "
                    + ResourceSortCriteria.MIN_HITS_FOR_ERROR_RATE + " hits each)");
        }
        return List.of();
    }
}
