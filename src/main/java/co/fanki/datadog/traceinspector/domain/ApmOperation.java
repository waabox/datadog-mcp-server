package co.fanki.datadog.traceinspector.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * The APM operation name used to build trace metric names, such as
 * {@code servlet.request} for Java/Spring or {@code next.request} for Next.js.
 *
 * @param name the operation name, never blank
 * @param source whether the name was provided by the caller or detected from a span
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public record ApmOperation(String name, Source source) {

    /** Where the operation name came from. */
    public enum Source {
        /** Detected from a recent entry span. */
        DETECTED,
        /** Passed explicitly by the caller. */
        PROVIDED
    }

    /**
     * Creates a new ApmOperation.
     *
     * @param name the operation name, must not be null or blank
     * @param source the source, must not be null
     *
     * @throws IllegalArgumentException if name is blank
     */
    public ApmOperation {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(source, "source must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("operation name must not be blank");
        }
    }

    /**
     * Creates an operation passed explicitly by the caller.
     *
     * @param name the operation name
     *
     * @return a PROVIDED operation
     */
    public static ApmOperation provided(final String name) {
        return new ApmOperation(name, Source.PROVIDED);
    }

    /**
     * Creates an operation detected from an entry span.
     *
     * @param name the operation name
     *
     * @return a DETECTED operation
     */
    public static ApmOperation detected(final String name) {
        return new ApmOperation(name, Source.DETECTED);
    }

    /**
     * Returns the source as a lowercase label for tool output.
     *
     * @return {@code "detected"} or {@code "provided"}
     */
    public String sourceLabel() {
        return source.name().toLowerCase(Locale.ROOT);
    }
}
