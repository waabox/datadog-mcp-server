package co.fanki.datadog.traceinspector.datadog.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * DTO for Datadog's log search API response.
 *
 * <p>Maps to the response from POST /api/v2/logs/events/search endpoint.</p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LogSearchResponse(
        @JsonProperty("data") List<LogEvent> data,
        @JsonProperty("meta") Meta meta
) {

    /**
     * Default constructor for empty response.
     */
    public LogSearchResponse() {
        this(List.of(), null);
    }

    /**
     * Represents a single log event.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LogEvent(
            @JsonProperty("id") String id,
            @JsonProperty("type") String type,
            @JsonProperty("attributes") LogAttributes attributes
    ) {}

    /**
     * Log event attributes.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LogAttributes(
            @JsonProperty("timestamp") String timestamp,
            @JsonProperty("status") String status,
            @JsonProperty("service") String service,
            @JsonProperty("message") String message,
            @JsonProperty("host") String host,
            @JsonProperty("attributes") Map<String, Object> attributes,
            @JsonProperty("tags") List<String> tags
    ) {
        /**
         * Returns the log level/status.
         *
         * @return the log level or INFO
         */
        public String level() {
            return status != null ? status.toUpperCase() : "INFO";
        }

        /**
         * Extracts the trace ID from attributes if present.
         *
         * @return the trace ID or null
         */
        public String traceId() {
            if (attributes == null) {
                return null;
            }
            final Object traceId = attributes.get("trace_id");
            if (traceId != null) {
                return traceId.toString();
            }
            final Object ddTraceId = attributes.get("dd.trace_id");
            if (ddTraceId != null) {
                return ddTraceId.toString();
            }
            return null;
        }

        /**
         * Converts nested attributes to a flat string map for domain use.
         *
         * @return flattened attributes map
         */
        public Map<String, String> flattenedAttributes() {
            if (attributes == null) {
                return Map.of();
            }
            final var result = new java.util.HashMap<String, String>();
            for (final var entry : attributes.entrySet()) {
                if (entry.getValue() != null) {
                    result.put(entry.getKey(), entry.getValue().toString());
                }
            }
            return Map.copyOf(result);
        }
    }

    /**
     * Response metadata.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(
            @JsonProperty("page") Page page,
            @JsonProperty("elapsed") Long elapsed,
            @JsonProperty("status") String status
    ) {}

    /**
     * Pagination information.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page(
            @JsonProperty("after") String after
    ) {}

    /**
     * Returns logs or empty list if null.
     *
     * @return the list of log events
     */
    public List<LogEvent> safeLogs() {
        return data != null ? data : List.of();
    }

    /**
     * Checks if there are more results.
     *
     * @return true if pagination cursor exists
     */
    public boolean hasMoreResults() {
        return meta != null && meta.page() != null && meta.page().after() != null;
    }
}
