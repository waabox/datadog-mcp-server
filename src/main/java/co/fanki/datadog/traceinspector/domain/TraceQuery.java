package co.fanki.datadog.traceinspector.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Query parameters for searching error traces in Datadog.
 *
 * <p>This record encapsulates all the parameters needed to query Datadog's
 * trace search API, including the service name, environment, time range,
 * and pagination limit.</p>
 *
 * @param service the service name to filter traces
 * @param env the environment (e.g., prod, staging)
 * @param from the start of the time range
 * @param to the end of the time range
 * @param limit the maximum number of traces to return
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public record TraceQuery(
        String service,
        String env,
        Instant from,
        Instant to,
        int limit
) {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    /**
     * Creates a TraceQuery with validated parameters.
     *
     * @param service the service name, must not be null or blank
     * @param env the environment, must not be null or blank
     * @param from the start timestamp, must not be null
     * @param to the end timestamp, must not be null and must be after from
     * @param limit the maximum number of results, must be positive and <= 100
     *
     * @throws IllegalArgumentException if any validation fails
     */
    public TraceQuery {
        Objects.requireNonNull(service, "service must not be null");
        Objects.requireNonNull(env, "env must not be null");
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");

        if (service.isBlank()) {
            throw new IllegalArgumentException("service must not be blank");
        }
        if (env.isBlank()) {
            throw new IllegalArgumentException("env must not be blank");
        }
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("to must be after from");
        }
        if (limit <= 0 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException(
                    "limit must be between 1 and %d".formatted(MAX_LIMIT)
            );
        }
    }

    /**
     * Creates a TraceQuery with the default limit.
     *
     * @param service the service name
     * @param env the environment
     * @param from the start timestamp
     * @param to the end timestamp
     *
     * @return a new TraceQuery with default limit of 20
     */
    public static TraceQuery withDefaultLimit(
            final String service,
            final String env,
            final Instant from,
            final Instant to
    ) {
        return new TraceQuery(service, env, from, to, DEFAULT_LIMIT);
    }

    /**
     * Builds the Datadog query string for trace search.
     *
     * @return the query string in Datadog query format
     */
    public String toDatadogQuery() {
        return "service:%s env:%s status:error".formatted(service, env);
    }
}
