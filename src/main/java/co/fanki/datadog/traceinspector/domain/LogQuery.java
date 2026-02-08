package co.fanki.datadog.traceinspector.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Query parameters for searching logs in Datadog.
 *
 * <p>This record encapsulates all the parameters needed to query Datadog's
 * log search API, including the service name, environment, time range,
 * optional query filter, log level, and pagination limit.</p>
 *
 * @param service the service name to filter logs
 * @param env the environment (e.g., prod, staging)
 * @param from the start of the time range
 * @param to the end of the time range
 * @param query optional additional query string
 * @param level optional log level filter (ERROR, WARN, INFO, DEBUG)
 * @param limit the maximum number of logs to return
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public record LogQuery(
        String service,
        String env,
        Instant from,
        Instant to,
        String query,
        String level,
        int limit
) {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

    /**
     * Creates a LogQuery with validated parameters.
     *
     * @param service the service name, must not be null or blank
     * @param env the environment, must not be null or blank
     * @param from the start timestamp, must not be null
     * @param to the end timestamp, must not be null and must be after from
     * @param query optional additional query, may be null
     * @param level optional log level, may be null
     * @param limit the maximum number of results, must be positive and <= 1000
     *
     * @throws IllegalArgumentException if any validation fails
     */
    public LogQuery {
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
     * Creates a LogQuery with the default limit.
     *
     * @param service the service name
     * @param env the environment
     * @param from the start timestamp
     * @param to the end timestamp
     *
     * @return a new LogQuery with default limit of 100
     */
    public static LogQuery withDefaultLimit(
            final String service,
            final String env,
            final Instant from,
            final Instant to
    ) {
        return new LogQuery(service, env, from, to, null, null, DEFAULT_LIMIT);
    }

    /**
     * Builds the Datadog query string for log search.
     *
     * @return the query string in Datadog query format
     */
    public String toDatadogQuery() {
        final StringBuilder sb = new StringBuilder();
        sb.append("service:").append(service);
        sb.append(" env:").append(env);

        if (level != null && !level.isBlank()) {
            sb.append(" status:").append(level.toLowerCase());
        }

        if (query != null && !query.isBlank()) {
            sb.append(" ").append(query);
        }

        return sb.toString();
    }
}
