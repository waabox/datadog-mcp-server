package co.fanki.datadog.traceinspector.application;

import co.fanki.datadog.traceinspector.datadog.DatadogClient;
import co.fanki.datadog.traceinspector.domain.DiagnosticResult;
import co.fanki.datadog.traceinspector.domain.ServiceErrorView;
import co.fanki.datadog.traceinspector.domain.SpanDetail;
import co.fanki.datadog.traceinspector.domain.TraceDetail;
import co.fanki.datadog.traceinspector.domain.TraceQuery;
import co.fanki.datadog.traceinspector.domain.TraceSummary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Orchestration service for trace diagnostics.
 *
 * <p>This service coordinates calls to the Datadog client and transforms
 * the results into actionable diagnostic information. It aggregates trace
 * data, associated logs, and generates structured error views.</p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public final class TraceDiagnosticService {

    private final DatadogClient datadogClient;
    private final MarkdownWorkflowGenerator workflowGenerator;

    /**
     * Creates a new TraceDiagnosticService.
     *
     * @param datadogClient the client for Datadog API calls
     * @param workflowGenerator the generator for workflow markdown
     */
    public TraceDiagnosticService(
            final DatadogClient datadogClient,
            final MarkdownWorkflowGenerator workflowGenerator
    ) {
        this.datadogClient = Objects.requireNonNull(
                datadogClient, "datadogClient must not be null"
        );
        this.workflowGenerator = Objects.requireNonNull(
                workflowGenerator, "workflowGenerator must not be null"
        );
    }

    /**
     * Lists error traces matching the query parameters.
     *
     * @param query the search parameters
     *
     * @return a list of trace summaries
     */
    public List<TraceSummary> listErrorTraces(final TraceQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        return datadogClient.searchErrorTraces(query);
    }

    /**
     * Performs a complete diagnostic inspection of an error trace.
     *
     * <p>This method:
     * <ol>
     *   <li>Retrieves the full trace details from Datadog</li>
     *   <li>Fetches associated log entries</li>
     *   <li>Groups errors by service</li>
     *   <li>Generates an actionable workflow markdown</li>
     * </ol>
     * </p>
     *
     * @param traceId the trace identifier to inspect
     * @param query the query context for time range and environment
     *
     * @return a complete diagnostic result
     */
    public DiagnosticResult inspectErrorTrace(
            final String traceId,
            final TraceQuery query
    ) {
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(query, "query must not be null");

        // Fetch trace details
        final TraceDetail traceDetail = datadogClient.getTraceDetail(
                traceId,
                query.service(),
                query.env()
        );

        // Fetch associated logs
        final List<ServiceErrorView.LogEntry> logs =
                datadogClient.searchLogsForTrace(traceId, query);

        // Group errors by service
        final List<ServiceErrorView> serviceErrors =
                buildServiceErrorViews(traceDetail, logs);

        // Generate workflow markdown
        final DiagnosticResult intermediateResult = DiagnosticResult.of(
                traceDetail,
                serviceErrors,
                "" // Empty workflow for now
        );

        final String workflow = workflowGenerator.generate(intermediateResult);

        return DiagnosticResult.of(traceDetail, serviceErrors, workflow);
    }

    private List<ServiceErrorView> buildServiceErrorViews(
            final TraceDetail trace,
            final List<ServiceErrorView.LogEntry> allLogs
    ) {
        // Group error spans by service
        final Map<String, List<SpanDetail>> errorSpansByService = new HashMap<>();

        for (final SpanDetail span : trace.errorSpans()) {
            errorSpansByService
                    .computeIfAbsent(span.service(), k -> new ArrayList<>())
                    .add(span);
        }

        // Build ServiceErrorView for each service with errors
        final List<ServiceErrorView> result = new ArrayList<>();

        for (final Map.Entry<String, List<SpanDetail>> entry
                : errorSpansByService.entrySet()) {

            final String serviceName = entry.getKey();
            final List<SpanDetail> errorSpans = entry.getValue();

            // Find the primary error (first error or most significant)
            final String primaryError = findPrimaryError(errorSpans);

            // Get the earliest error timestamp
            final java.time.Instant timestamp = errorSpans.stream()
                    .map(SpanDetail::startTime)
                    .min(java.time.Instant::compareTo)
                    .orElse(java.time.Instant.now());

            // Filter logs for this service (simplified - logs don't have service field)
            // In a real implementation, we'd filter by service from log attributes
            final List<ServiceErrorView.LogEntry> serviceLogs = allLogs;

            result.add(new ServiceErrorView(
                    serviceName,
                    errorSpans,
                    serviceLogs,
                    primaryError,
                    timestamp
            ));
        }

        return result;
    }

    private String findPrimaryError(final List<SpanDetail> errorSpans) {
        // Return the first non-empty error message
        for (final SpanDetail span : errorSpans) {
            final String summary = span.errorSummary();
            if (!summary.isBlank()) {
                return summary;
            }
        }
        return "";
    }
}
