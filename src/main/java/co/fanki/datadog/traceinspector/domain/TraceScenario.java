package co.fanki.datadog.traceinspector.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a structured scenario extracted from a trace.
 *
 * <p>This record provides all the information Claude needs to understand
 * what happened during a request, identify the error, and generate a
 * unit test that replicates the scenario.</p>
 *
 * @param traceId the trace identifier
 * @param entryPoint the HTTP entry point of the request
 * @param executionFlow the ordered list of execution steps
 * @param errorContext the error context if an error occurred
 * @param relevantData key-value pairs of relevant data extracted from spans
 * @param involvedServices list of services involved in this trace
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public record TraceScenario(
        String traceId,
        EntryPoint entryPoint,
        List<ExecutionStep> executionFlow,
        ErrorContext errorContext,
        Map<String, String> relevantData,
        List<String> involvedServices
) {

    /**
     * Creates a TraceScenario with validated parameters.
     */
    public TraceScenario {
        Objects.requireNonNull(traceId, "traceId must not be null");

        if (traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }

        entryPoint = entryPoint != null ? entryPoint : EntryPoint.empty();
        executionFlow = executionFlow != null ? List.copyOf(executionFlow) : List.of();
        errorContext = errorContext != null ? errorContext : ErrorContext.empty();
        relevantData = relevantData != null ? Map.copyOf(relevantData) : Map.of();
        involvedServices = involvedServices != null ? List.copyOf(involvedServices) : List.of();
    }

    /**
     * Returns whether this scenario has an error.
     *
     * @return true if there is an error context with error information
     */
    public boolean hasError() {
        return errorContext.hasError();
    }

    /**
     * Returns whether this scenario has a valid entry point.
     *
     * @return true if the entry point has HTTP information
     */
    public boolean hasEntryPoint() {
        return entryPoint.isValid();
    }

    /**
     * Returns the error step from the execution flow.
     *
     * @return the first error step, or null if none
     */
    public ExecutionStep errorStep() {
        return executionFlow.stream()
                .filter(ExecutionStep::isError)
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns the total duration of the trace in milliseconds.
     *
     * @return the total duration
     */
    public long totalDurationMs() {
        return executionFlow.stream()
                .filter(ExecutionStep::isRoot)
                .mapToLong(ExecutionStep::durationMs)
                .findFirst()
                .orElse(0L);
    }

    /**
     * Returns the number of steps in the execution flow.
     *
     * @return the step count
     */
    public int stepCount() {
        return executionFlow.size();
    }

    /**
     * Generates a suggested test scenario in Given/When/Then format.
     *
     * @return a map with "given", "when", and "then" keys
     */
    public Map<String, String> suggestedTestScenario() {
        if (!hasError()) {
            return Map.of();
        }

        final StringBuilder given = new StringBuilder();
        final StringBuilder when = new StringBuilder();
        final StringBuilder then = new StringBuilder();

        // Build "given" from relevant data
        if (!relevantData.isEmpty()) {
            given.append("Data: ");
            relevantData.forEach((key, value) ->
                    given.append("%s=%s, ".formatted(key, value)));
            // Remove trailing comma
            if (given.length() > 2) {
                given.setLength(given.length() - 2);
            }
        }

        // Build "when" from entry point
        if (hasEntryPoint()) {
            when.append("Request: %s".formatted(entryPoint.toRequestLine()));
            if (entryPoint.hasBody()) {
                when.append(" with body");
            }
        } else if (errorContext.hasError()) {
            when.append("Calling %s.%s".formatted(
                    errorContext.service(), errorContext.operation()));
        }

        // Build "then" from error context
        if (errorContext.hasError()) {
            then.append("%s is thrown".formatted(errorContext.simpleExceptionType()));
            if (!errorContext.message().isBlank()) {
                then.append(" with message '%s'".formatted(errorContext.message()));
            }
        }

        return Map.of(
                "given", given.toString(),
                "when", when.toString(),
                "then", then.toString()
        );
    }
}
