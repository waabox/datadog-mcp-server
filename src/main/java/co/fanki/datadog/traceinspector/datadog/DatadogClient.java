package co.fanki.datadog.traceinspector.datadog;

import co.fanki.datadog.traceinspector.domain.LogQuery;
import co.fanki.datadog.traceinspector.domain.LogSummary;
import co.fanki.datadog.traceinspector.domain.ServiceErrorView;
import co.fanki.datadog.traceinspector.domain.TraceDetail;
import co.fanki.datadog.traceinspector.domain.TraceQuery;
import co.fanki.datadog.traceinspector.domain.TraceSummary;

import java.time.Instant;
import java.util.List;

/**
 * Client for interacting with Datadog's APM and Logs APIs.
 *
 * <p>This interface defines the contract for retrieving trace and log data
 * from Datadog for diagnostic purposes.</p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public interface DatadogClient {

    /**
     * Searches for error traces matching the given query parameters.
     *
     * @param query the search parameters
     *
     * @return a list of trace summaries
     *
     * @throws DatadogApiException if the API call fails
     */
    List<TraceSummary> searchErrorTraces(TraceQuery query);

    /**
     * Retrieves the full details of a specific trace.
     *
     * @param traceId the trace identifier
     * @param service the service name for context
     * @param env the environment
     * @param from the start of the time range to search
     * @param to the end of the time range to search
     *
     * @return the complete trace details
     *
     * @throws DatadogApiException if the API call fails
     */
    TraceDetail getTraceDetail(String traceId, String service, String env, Instant from, Instant to);

    /**
     * Searches for logs associated with a specific trace.
     *
     * @param traceId the trace identifier
     * @param query the query for time context
     *
     * @return log entries grouped by service as ServiceErrorView.LogEntry
     *
     * @throws DatadogApiException if the API call fails
     */
    List<ServiceErrorView.LogEntry> searchLogsForTrace(String traceId, TraceQuery query);

    /**
     * Searches for logs matching the given query parameters.
     *
     * @param query the log search parameters
     *
     * @return a list of log summaries
     *
     * @throws DatadogApiException if the API call fails
     */
    List<LogSummary> searchLogs(LogQuery query);
}
