package co.fanki.datadog.traceinspector.datadog.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * DTO for Datadog's trace detail API response.
 *
 * <p>Maps to the response from GET /api/v1/trace/{traceId} endpoint.</p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TraceDetailResponse(
        @JsonProperty("data") TraceData data
) {

    /**
     * Default constructor for empty response.
     */
    public TraceDetailResponse() {
        this(null);
    }

    /**
     * The main trace data container.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TraceData(
            @JsonProperty("type") String type,
            @JsonProperty("attributes") TraceAttributes attributes
    ) {}

    /**
     * Trace attributes including all spans.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TraceAttributes(
            @JsonProperty("trace_id") String traceId,
            @JsonProperty("service") String service,
            @JsonProperty("env") String env,
            @JsonProperty("start") Long start,
            @JsonProperty("end") Long end,
            @JsonProperty("duration") Long duration,
            @JsonProperty("resource_name") String resourceName,
            @JsonProperty("spans") List<Span> spans
    ) {
        /**
         * Returns spans or empty list if null.
         *
         * @return the list of spans
         */
        public List<Span> safeSpans() {
            return spans != null ? spans : List.of();
        }
    }

    /**
     * Individual span within a trace.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Span(
            @JsonProperty("span_id") String spanId,
            @JsonProperty("parent_id") String parentId,
            @JsonProperty("trace_id") String traceId,
            @JsonProperty("service") String service,
            @JsonProperty("name") String operationName,
            @JsonProperty("resource") String resource,
            @JsonProperty("start") Long start,
            @JsonProperty("duration") Long duration,
            @JsonProperty("error") Integer error,
            @JsonProperty("meta") Map<String, String> meta,
            @JsonProperty("metrics") Map<String, Double> metrics
    ) {
        /**
         * Checks if this span has an error.
         *
         * @return true if error flag is set
         */
        public boolean isError() {
            return error != null && error != 0;
        }

        /**
         * Extracts the error message from meta.
         *
         * @return the error message or empty string
         */
        public String errorMessage() {
            if (meta == null) {
                return "";
            }
            final String msg = meta.get("error.message");
            if (msg != null) {
                return msg;
            }
            return meta.getOrDefault("error.msg", "");
        }

        /**
         * Extracts the error type from meta.
         *
         * @return the error type or empty string
         */
        public String errorType() {
            if (meta == null) {
                return "";
            }
            return meta.getOrDefault("error.type", "");
        }

        /**
         * Extracts the error stack trace from meta.
         *
         * @return the stack trace or empty string
         */
        public String errorStack() {
            if (meta == null) {
                return "";
            }
            return meta.getOrDefault("error.stack", "");
        }

        /**
         * Returns meta as a safe non-null map.
         *
         * @return meta tags or empty map
         */
        public Map<String, String> safeMeta() {
            return meta != null ? meta : Map.of();
        }
    }

    /**
     * Checks if this response contains valid trace data.
     *
     * @return true if data is present
     */
    public boolean hasData() {
        return data != null && data.attributes() != null;
    }
}
