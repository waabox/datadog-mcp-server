package co.fanki.datadog.traceinspector.domain;

/**
 * A metric value in the current window compared with the baseline window.
 *
 * @param current the value in the current window, may be null
 * @param baseline the value in the baseline window, may be null
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public record MetricComparison(Double current, Double baseline) {

    /**
     * Returns the change from baseline to current as a percentage (BR-11).
     *
     * @return {@code (current - baseline) / baseline * 100}, or null when either value is null or
     *     the baseline is zero
     */
    public Double deltaPct() {
        if (current == null || baseline == null || baseline == 0.0) {
            return null;
        }
        return (current - baseline) / baseline * 100.0;
    }
}
