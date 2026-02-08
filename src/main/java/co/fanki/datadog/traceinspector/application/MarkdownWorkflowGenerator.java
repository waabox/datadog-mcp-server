package co.fanki.datadog.traceinspector.application;

import co.fanki.datadog.traceinspector.domain.DiagnosticResult;
import co.fanki.datadog.traceinspector.domain.ServiceErrorView;
import co.fanki.datadog.traceinspector.domain.SpanDetail;
import co.fanki.datadog.traceinspector.domain.TraceDetail;

import java.util.List;
import java.util.Objects;

/**
 * Generates actionable markdown workflow documentation from diagnostic results.
 *
 * <p>This class transforms trace diagnostic data into a structured markdown
 * document that guides developers through debugging steps, provides context
 * about the error, and suggests actionable next steps.</p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public final class MarkdownWorkflowGenerator {

    /**
     * Generates a complete diagnostic workflow markdown document.
     *
     * @param result the diagnostic result containing trace and error data
     *
     * @return a formatted markdown string
     */
    public String generate(final DiagnosticResult result) {
        Objects.requireNonNull(result, "result must not be null");

        final StringBuilder md = new StringBuilder();

        appendHeader(md, result);
        appendSummary(md, result);
        appendServicesInvolved(md, result);
        appendErrorDetails(md, result);
        appendSpanTimeline(md, result.traceDetail());
        appendLogsSection(md, result.serviceErrors());
        appendActionableSteps(md, result);
        appendDatadogLinks(md, result);

        return md.toString();
    }

    private void appendHeader(final StringBuilder md, final DiagnosticResult result) {
        md.append("# Error Trace Diagnostic Report\n\n");
        md.append("**Trace ID:** `").append(result.traceId()).append("`\n");
        md.append("**Service:** ").append(result.service()).append("\n");
        md.append("**Generated:** ").append(result.generatedAt()).append("\n\n");
    }

    private void appendSummary(final StringBuilder md, final DiagnosticResult result) {
        md.append("## Summary\n\n");

        final TraceDetail trace = result.traceDetail();
        md.append("- **Duration:** ").append(trace.formattedDuration()).append("\n");
        md.append("- **Total Spans:** ").append(trace.spanCount()).append("\n");
        md.append("- **Error Spans:** ").append(result.totalErrorCount()).append("\n");
        md.append("- **Services Involved:** ").append(result.involvedServices().size())
                .append("\n");

        if (result.isDistributedError()) {
            md.append("- **Type:** Distributed error across multiple services\n");
        } else {
            md.append("- **Type:** Single-service error\n");
        }
        md.append("\n");
    }

    private void appendServicesInvolved(final StringBuilder md, final DiagnosticResult result) {
        md.append("## Services Involved\n\n");

        for (final String service : result.involvedServices()) {
            final boolean hasError = result.serviceErrors().stream()
                    .anyMatch(se -> se.serviceName().equals(service));
            final String status = hasError ? "ERROR" : "OK";
            md.append("- **").append(service).append("**: ").append(status).append("\n");
        }
        md.append("\n");
    }

    private void appendErrorDetails(final StringBuilder md, final DiagnosticResult result) {
        md.append("## Error Details\n\n");

        for (final ServiceErrorView serviceError : result.serviceErrors()) {
            md.append("### ").append(serviceError.serviceName()).append("\n\n");

            if (!serviceError.primaryError().isBlank()) {
                md.append("**Primary Error:** ").append(serviceError.primaryError())
                        .append("\n\n");
            }

            md.append("**Error Count:** ").append(serviceError.errorCount()).append("\n\n");

            final List<String> errorTypes = serviceError.uniqueErrorTypes();
            if (!errorTypes.isEmpty()) {
                md.append("**Error Types:**\n");
                for (final String errorType : errorTypes) {
                    md.append("- `").append(errorType).append("`\n");
                }
                md.append("\n");
            }

            for (final SpanDetail errorSpan : serviceError.errorSpans()) {
                md.append("#### Span: ").append(errorSpan.operationName()).append("\n\n");
                md.append("- **Resource:** ").append(errorSpan.resourceName()).append("\n");
                md.append("- **Duration:** ").append(errorSpan.formattedDuration()).append("\n");

                if (!errorSpan.errorType().isBlank()) {
                    md.append("- **Exception:** `").append(errorSpan.errorType()).append("`\n");
                }

                if (!errorSpan.errorMessage().isBlank()) {
                    md.append("- **Message:** ").append(errorSpan.errorMessage()).append("\n");
                }

                if (!errorSpan.errorStack().isBlank()) {
                    md.append("\n**Stack Trace:**\n```\n");
                    md.append(truncateStackTrace(errorSpan.errorStack(), 20));
                    md.append("\n```\n");
                }
                md.append("\n");
            }
        }
    }

    private void appendSpanTimeline(final StringBuilder md, final TraceDetail trace) {
        md.append("## Span Timeline\n\n");
        md.append("| Service | Operation | Duration | Status |\n");
        md.append("|---------|-----------|----------|--------|\n");

        for (final SpanDetail span : trace.spans()) {
            final String status = span.isError() ? "ERROR" : "OK";
            md.append("| ").append(span.service());
            md.append(" | ").append(span.operationName());
            md.append(" | ").append(span.formattedDuration());
            md.append(" | ").append(status);
            md.append(" |\n");
        }
        md.append("\n");
    }

    private void appendLogsSection(
            final StringBuilder md,
            final List<ServiceErrorView> serviceErrors
    ) {
        final boolean hasLogs = serviceErrors.stream()
                .anyMatch(ServiceErrorView::hasLogs);

        if (!hasLogs) {
            return;
        }

        md.append("## Related Logs\n\n");

        for (final ServiceErrorView serviceError : serviceErrors) {
            if (!serviceError.hasLogs()) {
                continue;
            }

            md.append("### ").append(serviceError.serviceName()).append(" Logs\n\n");

            for (final ServiceErrorView.LogEntry log : serviceError.relatedLogs()) {
                md.append("**[").append(log.level()).append("]** ");
                md.append("_").append(log.timestamp()).append("_\n");
                md.append("```\n").append(log.message()).append("\n```\n\n");
            }
        }
    }

    private void appendActionableSteps(final StringBuilder md, final DiagnosticResult result) {
        md.append("## Recommended Actions\n\n");

        int step = 1;

        // Analyze the errors and provide specific recommendations
        for (final ServiceErrorView serviceError : result.serviceErrors()) {
            md.append(step++).append(". **Investigate ")
                    .append(serviceError.serviceName()).append(":**\n");

            for (final String errorType : serviceError.uniqueErrorTypes()) {
                md.append("   - Check for `").append(errorType).append("` root cause\n");
            }

            if (!serviceError.primaryError().isBlank()) {
                md.append("   - Primary error: \"").append(serviceError.primaryError())
                        .append("\"\n");
            }

            md.append("\n");
        }

        // Generic steps
        md.append(step++).append(". **Review recent deployments** to ")
                .append("identify potential causes\n\n");

        if (result.isDistributedError()) {
            md.append(step++).append(". **Check service communication** between:\n");
            for (final String service : result.involvedServices()) {
                md.append("   - ").append(service).append("\n");
            }
            md.append("\n");
        }

        md.append(step).append(". **Monitor for recurrence** after applying fixes\n\n");
    }

    private void appendDatadogLinks(final StringBuilder md, final DiagnosticResult result) {
        md.append("## Datadog Links\n\n");
        md.append("- [View Trace in Datadog](https://app.datadoghq.com/apm/trace/")
                .append(result.traceId()).append(")\n");
        md.append("- [Service Dashboard](https://app.datadoghq.com/apm/service/")
                .append(result.service()).append(")\n\n");
    }

    private String truncateStackTrace(final String stack, final int maxLines) {
        final String[] lines = stack.split("\n");
        if (lines.length <= maxLines) {
            return stack;
        }

        final StringBuilder truncated = new StringBuilder();
        for (int i = 0; i < maxLines; i++) {
            truncated.append(lines[i]).append("\n");
        }
        truncated.append("... (").append(lines.length - maxLines)
                .append(" more lines truncated)");
        return truncated.toString();
    }
}
