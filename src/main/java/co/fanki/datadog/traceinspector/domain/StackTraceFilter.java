package co.fanki.datadog.traceinspector.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filters stack traces to show only relevant application frames.
 *
 * <p>This filter removes framework noise (Spring, Tomcat, etc.) and keeps
 * only frames from specified packages, making stack traces more readable
 * and reducing context usage.</p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public final class StackTraceFilter {

    /** Pattern to match stack trace lines: "at package.Class.method(File.java:123)" */
    private static final Pattern STACK_FRAME_PATTERN = Pattern.compile(
            "^\\s*at\\s+([a-zA-Z0-9_$.]+)\\.([a-zA-Z0-9_$<>]+)\\((.+)\\)\\s*$"
    );

    /** Pattern to match "Caused by:" lines */
    private static final Pattern CAUSED_BY_PATTERN = Pattern.compile(
            "^\\s*Caused by:\\s*(.+)$"
    );

    /** Common framework packages to identify as "noise" */
    private static final Set<String> FRAMEWORK_PACKAGES = Set.of(
            "java.",
            "javax.",
            "jakarta.",
            "sun.",
            "jdk.",
            "org.springframework.",
            "org.apache.",
            "org.hibernate.",
            "org.eclipse.",
            "com.zaxxer.",
            "com.fasterxml.",
            "io.netty.",
            "reactor.",
            "feign.",
            "datadog.trace."
    );

    private final List<String> relevantPackages;

    /**
     * Creates a StackTraceFilter with the specified relevant packages.
     *
     * @param relevantPackages list of package prefixes to keep (e.g., "co.fanki")
     */
    public StackTraceFilter(final List<String> relevantPackages) {
        Objects.requireNonNull(relevantPackages, "relevantPackages must not be null");
        this.relevantPackages = List.copyOf(relevantPackages);
    }

    /**
     * Filters a stack trace string based on the configured relevant packages.
     *
     * @param stackTrace the full stack trace string
     * @param detail the level of detail to include
     *
     * @return the filtered stack trace
     */
    public String filter(final String stackTrace, final StackTraceDetail detail) {
        if (stackTrace == null || stackTrace.isBlank()) {
            return stackTrace;
        }

        if (detail == StackTraceDetail.FULL || relevantPackages.isEmpty()) {
            return stackTrace;
        }

        if (detail == StackTraceDetail.MINIMAL) {
            return extractMinimal(stackTrace);
        }

        return extractRelevant(stackTrace);
    }

    /**
     * Extracts only the exception message and root cause.
     */
    private String extractMinimal(final String stackTrace) {
        final String[] lines = stackTrace.split("\n");
        final StringBuilder result = new StringBuilder();

        // First line is usually the exception message
        if (lines.length > 0) {
            result.append(lines[0]);
        }

        // Find "Caused by:" lines
        for (final String line : lines) {
            final Matcher causedBy = CAUSED_BY_PATTERN.matcher(line);
            if (causedBy.matches()) {
                result.append("\n").append(line);
            }
        }

        return result.toString();
    }

    /**
     * Extracts relevant frames and summarizes omitted ones.
     */
    private String extractRelevant(final String stackTrace) {
        final String[] lines = stackTrace.split("\n");
        final StringBuilder result = new StringBuilder();
        final List<String> omittedFrameworks = new ArrayList<>();
        int omittedCount = 0;

        for (final String line : lines) {
            // Always keep exception/error lines and "Caused by:" lines
            if (!line.trim().startsWith("at ") && !line.trim().startsWith("...")) {
                // Flush any pending omitted count
                if (omittedCount > 0) {
                    result.append(formatOmittedSummary(omittedCount, omittedFrameworks));
                    omittedCount = 0;
                    omittedFrameworks.clear();
                }
                result.append(line).append("\n");
                continue;
            }

            // Check if this is a relevant frame
            final Matcher frameMatcher = STACK_FRAME_PATTERN.matcher(line);
            if (frameMatcher.matches()) {
                final String fullClassName = frameMatcher.group(1);

                if (isRelevantPackage(fullClassName)) {
                    // Flush any pending omitted count
                    if (omittedCount > 0) {
                        result.append(formatOmittedSummary(omittedCount, omittedFrameworks));
                        omittedCount = 0;
                        omittedFrameworks.clear();
                    }
                    // Simplify the frame: remove package prefix for readability
                    final String simplified = simplifyFrame(line, fullClassName);
                    result.append(simplified).append("\n");
                } else {
                    omittedCount++;
                    trackFramework(fullClassName, omittedFrameworks);
                }
            } else if (line.trim().startsWith("...")) {
                // Keep "... X more" lines
                if (omittedCount > 0) {
                    result.append(formatOmittedSummary(omittedCount, omittedFrameworks));
                    omittedCount = 0;
                    omittedFrameworks.clear();
                }
                result.append(line).append("\n");
            } else {
                omittedCount++;
            }
        }

        // Flush any remaining omitted count
        if (omittedCount > 0) {
            result.append(formatOmittedSummary(omittedCount, omittedFrameworks));
        }

        return result.toString().trim();
    }

    /**
     * Checks if a class belongs to a relevant package.
     */
    private boolean isRelevantPackage(final String fullClassName) {
        for (final String pkg : relevantPackages) {
            if (fullClassName.startsWith(pkg)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Simplifies a stack frame by extracting just the class name (no package).
     */
    private String simplifyFrame(final String line, final String fullClassName) {
        // Extract simple class name
        final int lastDot = fullClassName.lastIndexOf('.');
        final String simpleClassName = lastDot > 0
                ? fullClassName.substring(lastDot + 1)
                : fullClassName;

        // Replace full class name with simple name in the line
        return line.replace(fullClassName + ".", simpleClassName + ".");
    }

    /**
     * Tracks which framework a class belongs to.
     */
    private void trackFramework(final String fullClassName, final List<String> frameworks) {
        for (final String framework : FRAMEWORK_PACKAGES) {
            if (fullClassName.startsWith(framework)) {
                final String name = getFrameworkName(framework);
                if (!frameworks.contains(name)) {
                    frameworks.add(name);
                }
                return;
            }
        }
        // Unknown framework
        if (!frameworks.contains("other")) {
            frameworks.add("other");
        }
    }

    /**
     * Gets a human-readable name for a framework package.
     */
    private String getFrameworkName(final String packagePrefix) {
        return switch (packagePrefix) {
            case "java.", "javax.", "jakarta.", "sun.", "jdk." -> "java";
            case "org.springframework." -> "spring";
            case "org.apache." -> "apache";
            case "org.hibernate." -> "hibernate";
            case "com.fasterxml." -> "jackson";
            case "feign." -> "feign";
            case "datadog.trace." -> "datadog";
            default -> packagePrefix.replace(".", "");
        };
    }

    /**
     * Formats the omitted frames summary.
     */
    private String formatOmittedSummary(final int count, final List<String> frameworks) {
        final String frameworkList = frameworks.isEmpty()
                ? ""
                : " (" + String.join(", ", frameworks) + ")";
        return "  ... " + count + " framework frames omitted" + frameworkList + "\n";
    }

    /**
     * Filters stack traces within log attributes map.
     *
     * <p>Looks for "stack_trace" key in attributes and filters it.</p>
     *
     * @param attributes the log attributes map
     * @param detail the level of detail to include
     * @param <V> the value type of the map
     *
     * @return a new map with filtered stack trace, or the original if no stack trace found
     */
    @SuppressWarnings("unchecked")
    public <V> java.util.Map<String, V> filterAttributes(
            final java.util.Map<String, V> attributes,
            final StackTraceDetail detail
    ) {
        if (attributes == null || !attributes.containsKey("stack_trace")) {
            return attributes;
        }

        final Object stackTraceObj = attributes.get("stack_trace");
        if (!(stackTraceObj instanceof String stackTrace)) {
            return attributes;
        }

        final String filtered = filter(stackTrace, detail);

        // Create a new map with the filtered stack trace
        final java.util.Map<String, V> result = new java.util.LinkedHashMap<>(attributes);
        result.put("stack_trace", (V) filtered);
        return result;
    }

    /**
     * Enum representing the level of stack trace detail to include.
     */
    public enum StackTraceDetail {
        /** Include the full stack trace as-is */
        FULL,
        /** Include only frames from relevant packages */
        RELEVANT,
        /** Include only exception message and root cause */
        MINIMAL
    }
}
