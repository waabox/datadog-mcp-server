package co.fanki.datadog.traceinspector.datadog.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * DTO for Datadog's span search API response.
 *
 * <p>Maps to the response from POST /api/v2/spans/events/search endpoint.</p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TraceSearchResponse(
        @JsonProperty("data") List<SpanEvent> data,
        @JsonProperty("meta") Meta meta
) {

    /**
     * Default constructor for empty response.
     */
    public TraceSearchResponse() {
        this(List.of(), null);
    }

    /**
     * Represents a single span event from the search results.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SpanEvent(
            @JsonProperty("id") String id,
            @JsonProperty("type") String type,
            @JsonProperty("attributes") SpanAttributes attributes
    ) {}

    /**
     * Span event attributes.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SpanAttributes(
            @JsonProperty("timestamp") String timestamp,
            @JsonProperty("service") String service,
            @JsonProperty("resource_name") String resourceName,
            @JsonProperty("span_id") String spanId,
            @JsonProperty("trace_id") String traceId,
            @JsonProperty("duration") Long duration,
            @JsonProperty("status") String status,
            @JsonProperty("attributes") Map<String, Object> tags
    ) {
        /**
         * Extracts the error message from attributes if present.
         *
         * @return the error message or empty string
         */
        public String errorMessage() {
            if (tags == null) {
                return "";
            }
            final Object errorMsg = tags.get("error.message");
            if (errorMsg != null) {
                return errorMsg.toString();
            }
            final Object errorMeta = tags.get("error.msg");
            if (errorMeta != null) {
                return errorMeta.toString();
            }
            return "";
        }
    }

    /**
     * Response metadata including pagination.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(
            @JsonProperty("page") Page page,
            @JsonProperty("elapsed") Long elapsed
    ) {}

    /**
     * Pagination information.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page(
            @JsonProperty("after") String after
    ) {}

    /**
     * Checks if there are more results available.
     *
     * @return true if pagination cursor exists
     */
    public boolean hasMoreResults() {
        return meta != null && meta.page() != null && meta.page().after() != null;
    }

    /**
     * Returns the pagination cursor for the next page.
     *
     * @return the cursor or null
     */
    public String nextPageCursor() {
        if (meta == null || meta.page() == null) {
            return null;
        }
        return meta.page().after();
    }
}
