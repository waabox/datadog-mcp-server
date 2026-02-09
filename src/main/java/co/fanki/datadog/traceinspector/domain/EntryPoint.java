package co.fanki.datadog.traceinspector.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Represents the entry point of a trace request.
 *
 * <p>This record captures the HTTP request details that initiated the trace,
 * enabling reproduction of the exact request for debugging and testing.</p>
 *
 * @param method the HTTP method (GET, POST, PUT, DELETE, etc.)
 * @param path the request path
 * @param headers relevant request headers
 * @param body the request body if available
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public record EntryPoint(
        String method,
        String path,
        Map<String, String> headers,
        String body
) {

    /**
     * Creates an EntryPoint with validated parameters.
     */
    public EntryPoint {
        method = method != null ? method : "";
        path = path != null ? path : "";
        headers = headers != null ? Map.copyOf(headers) : Map.of();
        body = body != null ? body : "";
    }

    /**
     * Creates an empty entry point.
     *
     * @return an empty EntryPoint
     */
    public static EntryPoint empty() {
        return new EntryPoint("", "", Map.of(), "");
    }

    /**
     * Returns whether this entry point has valid HTTP information.
     *
     * @return true if method and path are present
     */
    public boolean isValid() {
        return !method.isBlank() && !path.isBlank();
    }

    /**
     * Returns a formatted request line (e.g., "POST /api/orders").
     *
     * @return the formatted request line
     */
    public String toRequestLine() {
        if (!isValid()) {
            return "";
        }
        return "%s %s".formatted(method, path);
    }

    /**
     * Returns whether this entry point has a request body.
     *
     * @return true if body is present
     */
    public boolean hasBody() {
        return !body.isBlank();
    }
}
