# APM Service Health and Top Resources Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add two MCP tools, `apm.service_health` and `apm.top_resources`, that read Datadog APM trace metrics to say whether a service is degraded compared with the previous window and which resources hurt the most.

**Architecture:** A new `ApmMetricsClient` (separate from `DatadogClient`) wraps the Metrics API v2 scalar endpoint and a one-span lookup to detect the operation name. Rich domain records hold every rule (windows, deltas, degraded verdict, ranking). `ApmHealthService` only orchestrates. Two `McpTool` classes parse arguments and serialize domain objects.

**Tech Stack:** Java 21 (`--enable-preview`), Datadog API client SDK 2.50.0 (`com.datadog.api.client.v2.api.MetricsApi#queryScalarData`, `SpansApi#listSpans`), Jackson, JUnit 5. No Mockito: tests use hand-written stubs, like the rest of the repo.

**Spec:** `.claude/docs/use-cases/apm-service-health.md` (business rules BR-1 to BR-21 and BR-11a are referenced by number below).

## Global Constraints

- Datadog API client SDK stays at `2.50.0`. Do not change `pom.xml` dependencies.
- Never use `var`. Always use explicit types (existing code has some `var`; do not copy it).
- Never use Lombok. Domain objects are immutable records with validation in compact constructors.
- Parameters and local variables are `final`.
- 4-space indentation, 120-column max, matching existing files.
- Every public class and method has JavaDoc (purpose, rules, params, returns, exceptions). Author tag: `@author waabox(emiliano[at]fanki[dot]co)`.
- Test names: `whenDoingSomething_givenSomeScenario_shouldDoOrHappenSomething()`.
- No Mockito. Stub SDK classes by subclassing (`MetricsApi`, `SpansApi`), as `DatadogClientIntegrationTest` does.
- All Datadog calls go through `RetryExecutor.execute(callable, operationName)`.
- Do not modify `DatadogClient` or `DatadogClientImpl`.
- Commits: show the message and ask "Do you approve this commit message?" before every commit. No `Co-Authored-By` line. No mention of Claude. Do not push.
- Branch: `feature/apm-service-health` (already created).
- Run tests with `mvn test -Dtest=<Class>`; full suite with `mvn test`.

## File Structure

```
src/main/java/co/fanki/datadog/traceinspector/
  domain/
    TimeWindow.java             [new] half-open window, previous()
    ApmOperation.java           [new] operation name + DETECTED/PROVIDED
    ApmMetrics.java             [new] hits, errors, p50/p95/p99 (ms), errorRate()
    MetricComparison.java       [new] current vs baseline, deltaPct()
    ServiceHealth.java          [new] degraded rules, signals, notes
    ResourceStats.java          [new] resource name + ApmMetrics
    ResourceSortCriteria.java   [new] sortBy enum, ranking, limit validation
    TopResources.java           [new] ranked result + notes
  datadog/
    ApmMetricsClient.java       [new] interface
    ApmMetricsClientImpl.java   [new] MetricsApi + SpansApi implementation
  application/
    ApmHealthService.java       [new] resolves operation, orchestrates queries
  mcp/
    ApmServiceHealthTool.java   [new] apm.service_health
    ApmTopResourcesTool.java    [new] apm.top_resources
  DatadogMcpServer.java         [modify] wiring
src/test/java/co/fanki/datadog/traceinspector/
  domain/  TimeWindowTest, ApmOperationTest, ApmMetricsTest, MetricComparisonTest,
           ServiceHealthTest, ResourceSortCriteriaTest, TopResourcesTest
  datadog/ ApmMetricsClientImplTest, FakeApmMetricsClient (shared test fixture)
  application/ ApmHealthServiceTest
  mcp/     ApmServiceHealthToolTest, ApmTopResourcesToolTest
CLAUDE.md, README.md            [modify] docs
```

---

### Task 1: Value objects (TimeWindow, ApmOperation, ApmMetrics, MetricComparison)

Implements BR-2, BR-3 (operation source), BR-5, BR-6, BR-9, BR-11.

**Files:**
- Create: `src/main/java/co/fanki/datadog/traceinspector/domain/TimeWindow.java`
- Create: `src/main/java/co/fanki/datadog/traceinspector/domain/ApmOperation.java`
- Create: `src/main/java/co/fanki/datadog/traceinspector/domain/ApmMetrics.java`
- Create: `src/main/java/co/fanki/datadog/traceinspector/domain/MetricComparison.java`
- Test: `src/test/java/co/fanki/datadog/traceinspector/domain/TimeWindowTest.java`
- Test: `src/test/java/co/fanki/datadog/traceinspector/domain/ApmOperationTest.java`
- Test: `src/test/java/co/fanki/datadog/traceinspector/domain/ApmMetricsTest.java`
- Test: `src/test/java/co/fanki/datadog/traceinspector/domain/MetricComparisonTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `record TimeWindow(Instant from, Instant to)`; `Duration length()`; `TimeWindow previous()`
  - `record ApmOperation(String name, ApmOperation.Source source)`; `enum Source { DETECTED, PROVIDED }`; `static ApmOperation provided(String)`; `static ApmOperation detected(String)`; `String sourceLabel()` returns `"detected"` / `"provided"`
  - `record ApmMetrics(long hits, long errors, Double latencyP50, Double latencyP95, Double latencyP99)` (latency in ms, nullable); `static ApmMetrics empty()`; `double errorRate()` (percent); `boolean hasLatency()`
  - `record MetricComparison(Double current, Double baseline)`; `Double deltaPct()`

- [ ] **Step 1: Write the failing tests**

`src/test/java/co/fanki/datadog/traceinspector/domain/TimeWindowTest.java`:

```java
package co.fanki.datadog.traceinspector.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for TimeWindow.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class TimeWindowTest {

    private static final Instant T13 = Instant.parse("2026-09-23T13:00:00Z");
    private static final Instant T14 = Instant.parse("2026-09-23T14:00:00Z");

    @Test
    void whenGettingLength_givenOneHourWindow_shouldReturnOneHour() {
        assertEquals(Duration.ofHours(1), new TimeWindow(T13, T14).length());
    }

    @Test
    void whenGettingPrevious_givenOneHourWindow_shouldReturnPrecedingHour() {
        final TimeWindow previous = new TimeWindow(T13, T14).previous();

        assertEquals(Instant.parse("2026-09-23T12:00:00Z"), previous.from());
        assertEquals(T13, previous.to());
    }

    @Test
    void whenCreating_givenFromEqualToTo_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> new TimeWindow(T13, T13));
    }

    @Test
    void whenCreating_givenFromAfterTo_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> new TimeWindow(T14, T13));
    }

    @Test
    void whenCreating_givenNullFrom_shouldThrow() {
        assertThrows(NullPointerException.class, () -> new TimeWindow(null, T14));
    }
}
```

`src/test/java/co/fanki/datadog/traceinspector/domain/ApmOperationTest.java`:

```java
package co.fanki.datadog.traceinspector.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for ApmOperation.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class ApmOperationTest {

    @Test
    void whenCreatingProvided_givenName_shouldHaveProvidedSource() {
        final ApmOperation operation = ApmOperation.provided("servlet.request");

        assertEquals("servlet.request", operation.name());
        assertEquals(ApmOperation.Source.PROVIDED, operation.source());
        assertEquals("provided", operation.sourceLabel());
    }

    @Test
    void whenCreatingDetected_givenName_shouldHaveDetectedLabel() {
        assertEquals("detected", ApmOperation.detected("next.request").sourceLabel());
    }

    @Test
    void whenCreating_givenBlankName_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> ApmOperation.provided("  "));
    }

    @Test
    void whenCreating_givenNullName_shouldThrow() {
        assertThrows(NullPointerException.class, () -> ApmOperation.detected(null));
    }
}
```

`src/test/java/co/fanki/datadog/traceinspector/domain/ApmMetricsTest.java`:

```java
package co.fanki.datadog.traceinspector.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for ApmMetrics.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class ApmMetricsTest {

    @Test
    void whenComputingErrorRate_givenZeroHits_shouldReturnZero() {
        assertEquals(0.0, ApmMetrics.empty().errorRate());
    }

    @Test
    void whenComputingErrorRate_givenHitsAndErrors_shouldReturnPercent() {
        final ApmMetrics metrics = new ApmMetrics(1000L, 25L, null, null, null);

        assertEquals(2.5, metrics.errorRate(), 0.0001);
    }

    @Test
    void whenCreatingEmpty_shouldHaveZeroCountsAndNoLatency() {
        final ApmMetrics empty = ApmMetrics.empty();

        assertEquals(0L, empty.hits());
        assertEquals(0L, empty.errors());
        assertNull(empty.latencyP95());
        assertFalse(empty.hasLatency());
    }

    @Test
    void whenCheckingLatency_givenOnlyP95_shouldReportLatency() {
        assertTrue(new ApmMetrics(10L, 0L, null, 120.0, null).hasLatency());
    }

    @Test
    void whenCreating_givenNegativeHits_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> new ApmMetrics(-1L, 0L, null, null, null));
    }

    @Test
    void whenCreating_givenNegativeErrors_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> new ApmMetrics(0L, -1L, null, null, null));
    }
}
```

`src/test/java/co/fanki/datadog/traceinspector/domain/MetricComparisonTest.java`:

```java
package co.fanki.datadog.traceinspector.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for MetricComparison.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class MetricComparisonTest {

    @Test
    void whenComputingDelta_givenIncrease_shouldReturnPositivePercent() {
        assertEquals(50.0, new MetricComparison(150.0, 100.0).deltaPct(), 0.0001);
    }

    @Test
    void whenComputingDelta_givenDecrease_shouldReturnNegativePercent() {
        assertEquals(-50.0, new MetricComparison(100.0, 200.0).deltaPct(), 0.0001);
    }

    @Test
    void whenComputingDelta_givenZeroBaseline_shouldReturnNull() {
        assertNull(new MetricComparison(10.0, 0.0).deltaPct());
    }

    @Test
    void whenComputingDelta_givenNullCurrent_shouldReturnNull() {
        assertNull(new MetricComparison(null, 10.0).deltaPct());
    }

    @Test
    void whenComputingDelta_givenNullBaseline_shouldReturnNull() {
        assertNull(new MetricComparison(10.0, null).deltaPct());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest='TimeWindowTest,ApmOperationTest,ApmMetricsTest,MetricComparisonTest'`
Expected: compilation failure (`cannot find symbol: class TimeWindow`, etc.).

- [ ] **Step 3: Implement**

`src/main/java/co/fanki/datadog/traceinspector/domain/TimeWindow.java`:

```java
package co.fanki.datadog.traceinspector.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A half-open time range {@code [from, to)} used to query APM metrics.
 *
 * @param from the inclusive start of the window
 * @param to the exclusive end of the window
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public record TimeWindow(Instant from, Instant to) {

    /**
     * Creates a new TimeWindow.
     *
     * @param from the start, must not be null
     * @param to the end, must not be null and must be after from
     *
     * @throws IllegalArgumentException if from is not before to
     */
    public TimeWindow {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
    }

    /**
     * Returns the length of this window.
     *
     * @return the duration between from and to
     */
    public Duration length() {
        return Duration.between(from, to);
    }

    /**
     * Returns the window of the same length that ends where this one starts.
     *
     * <p>Used as the baseline when comparing APM metrics (BR-6).</p>
     *
     * @return the previous window {@code [from - length, from)}
     */
    public TimeWindow previous() {
        return new TimeWindow(from.minus(length()), from);
    }
}
```

`src/main/java/co/fanki/datadog/traceinspector/domain/ApmOperation.java`:

```java
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
```

`src/main/java/co/fanki/datadog/traceinspector/domain/ApmMetrics.java`:

```java
package co.fanki.datadog.traceinspector.domain;

/**
 * APM trace metrics for one service (or one resource) over one time window.
 *
 * @param hits the number of requests, never negative
 * @param errors the number of errored requests, never negative
 * @param latencyP50 the p50 latency in milliseconds, or null when not available
 * @param latencyP95 the p95 latency in milliseconds, or null when not available
 * @param latencyP99 the p99 latency in milliseconds, or null when not available
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public record ApmMetrics(long hits, long errors, Double latencyP50, Double latencyP95, Double latencyP99) {

    /**
     * Creates new ApmMetrics.
     *
     * @throws IllegalArgumentException if hits or errors are negative
     */
    public ApmMetrics {
        if (hits < 0) {
            throw new IllegalArgumentException("hits must not be negative");
        }
        if (errors < 0) {
            throw new IllegalArgumentException("errors must not be negative");
        }
    }

    /**
     * Returns metrics with no traffic and no latency data.
     *
     * @return empty metrics
     */
    public static ApmMetrics empty() {
        return new ApmMetrics(0L, 0L, null, null, null);
    }

    /**
     * Returns the error rate as a percentage (BR-9).
     *
     * @return {@code errors / hits * 100}, or 0 when there are no hits
     */
    public double errorRate() {
        if (hits == 0) {
            return 0.0;
        }
        return errors * 100.0 / hits;
    }

    /**
     * Tells whether any latency percentile is available.
     *
     * @return true if at least one of p50, p95 or p99 is not null
     */
    public boolean hasLatency() {
        return latencyP50 != null || latencyP95 != null || latencyP99 != null;
    }
}
```

`src/main/java/co/fanki/datadog/traceinspector/domain/MetricComparison.java`:

```java
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest='TimeWindowTest,ApmOperationTest,ApmMetricsTest,MetricComparisonTest'`
Expected: `Tests run: 20, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit** (ask for approval of the message first)

```bash
git add src/main/java/co/fanki/datadog/traceinspector/domain/{TimeWindow,ApmOperation,ApmMetrics,MetricComparison}.java \
        src/test/java/co/fanki/datadog/traceinspector/domain/{TimeWindow,ApmOperation,ApmMetrics,MetricComparison}Test.java
git commit -m "Add APM value objects for time windows, operations and metrics"
```

---

### Task 2: ServiceHealth (degraded verdict)

Implements BR-11a, BR-12, BR-13, BR-14, BR-21 (notes).

**Files:**
- Create: `src/main/java/co/fanki/datadog/traceinspector/domain/ServiceHealth.java`
- Test: `src/test/java/co/fanki/datadog/traceinspector/domain/ServiceHealthTest.java`

**Interfaces:**
- Consumes (Task 1): `TimeWindow`, `ApmOperation`, `ApmMetrics`, `MetricComparison`.
- Produces:
  - `record ServiceHealth(String service, String env, ApmOperation operation, TimeWindow window, ApmMetrics current, ApmMetrics baseline)`
  - Constants: `MIN_BASELINE_HITS = 100L`, `ERROR_RATE_MULTIPLIER = 2.0`, `ERROR_RATE_MIN_INCREASE_POINTS = 1.0`, `LATENCY_P95_MULTIPLIER = 1.5`, `TRAFFIC_DROP_RATIO = 0.5`
  - `TimeWindow baselineWindow()`
  - `MetricComparison hits()`, `errors()`, `errorRate()`, `latencyP50()`, `latencyP95()`, `latencyP99()`
  - `boolean hasSufficientTraffic()`, `List<String> signals()`, `boolean isDegraded()`, `List<String> notes()`

- [ ] **Step 1: Write the failing test**

`src/test/java/co/fanki/datadog/traceinspector/domain/ServiceHealthTest.java`:

```java
package co.fanki.datadog.traceinspector.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for ServiceHealth degraded rules.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class ServiceHealthTest {

    private static final TimeWindow WINDOW = new TimeWindow(
            Instant.parse("2026-09-23T13:00:00Z"),
            Instant.parse("2026-09-23T14:00:00Z")
    );

    private static ServiceHealth health(final ApmMetrics current, final ApmMetrics baseline) {
        final ApmOperation operation = ApmOperation.provided("servlet.request");
        return new ServiceHealth("payments", "prod", operation, WINDOW, current, baseline);
    }

    private static ApmMetrics metrics(final long hits, final long errors, final Double p95) {
        return new ApmMetrics(hits, errors, 10.0, p95, 500.0);
    }

    @Test
    void whenGettingBaselineWindow_shouldReturnPreviousWindow() {
        final ServiceHealth health = health(metrics(1000, 0, 100.0), metrics(1000, 0, 100.0));

        assertEquals(Instant.parse("2026-09-23T12:00:00Z"), health.baselineWindow().from());
    }

    @Test
    void whenEvaluating_givenStableMetrics_shouldNotBeDegraded() {
        final ServiceHealth health = health(metrics(1000, 10, 100.0), metrics(1000, 10, 100.0));

        assertFalse(health.isDegraded());
        assertTrue(health.signals().isEmpty());
    }

    @Test
    void whenEvaluating_givenErrorRateDoubledAndOnePointHigher_shouldBeDegraded() {
        final ServiceHealth health = health(metrics(1000, 20, 100.0), metrics(1000, 10, 100.0));

        assertTrue(health.isDegraded());
        assertEquals(List.of("error rate 1.00% -> 2.00%"), health.signals());
    }

    @Test
    void whenEvaluating_givenErrorRateJustBelowDouble_shouldNotBeDegraded() {
        final ServiceHealth health = health(metrics(1000, 19, 100.0), metrics(1000, 10, 100.0));

        assertFalse(health.isDegraded());
    }

    @Test
    void whenEvaluating_givenErrorRateTripledButLessThanOnePoint_shouldNotBeDegraded() {
        final ServiceHealth health = health(metrics(1000, 9, 100.0), metrics(1000, 1, 100.0));

        assertFalse(health.isDegraded());
    }

    @Test
    void whenEvaluating_givenZeroBaselineErrorsAndOnePointIncrease_shouldBeDegraded() {
        final ServiceHealth health = health(metrics(1000, 10, 100.0), metrics(1000, 0, 100.0));

        assertTrue(health.isDegraded());
    }

    @Test
    void whenEvaluating_givenP95AtOnePointFiveTimes_shouldBeDegraded() {
        final ServiceHealth health = health(metrics(1000, 0, 300.0), metrics(1000, 0, 200.0));

        assertTrue(health.isDegraded());
        assertEquals(List.of("p95 latency 200.0ms -> 300.0ms"), health.signals());
    }

    @Test
    void whenEvaluating_givenP95JustBelowThreshold_shouldNotBeDegraded() {
        final ServiceHealth health = health(metrics(1000, 0, 299.0), metrics(1000, 0, 200.0));

        assertFalse(health.isDegraded());
    }

    @Test
    void whenEvaluating_givenMissingBaselineP95_shouldIgnoreLatencyRule() {
        final ServiceHealth health = health(metrics(1000, 0, 900.0), metrics(1000, 0, null));

        assertFalse(health.isDegraded());
    }

    @Test
    void whenEvaluating_givenHitsDroppedByHalf_shouldBeDegraded() {
        final ServiceHealth health = health(metrics(500, 0, 100.0), metrics(1000, 0, 100.0));

        assertTrue(health.isDegraded());
        assertEquals(List.of("hits 1000 -> 500"), health.signals());
    }

    @Test
    void whenEvaluating_givenHitsDroppedJustUnderHalf_shouldNotBeDegraded() {
        final ServiceHealth health = health(metrics(501, 0, 100.0), metrics(1000, 0, 100.0));

        assertFalse(health.isDegraded());
    }

    @Test
    void whenEvaluating_givenSeveralRulesMatch_shouldReportAllSignals() {
        final ServiceHealth health = health(metrics(400, 40, 900.0), metrics(1000, 0, 100.0));

        assertEquals(3, health.signals().size());
    }

    @Test
    void whenEvaluating_givenBaselineWith99Hits_shouldSkipRulesAndAddNote() {
        final ServiceHealth health = health(metrics(99, 99, 900.0), metrics(99, 0, 100.0));

        assertFalse(health.hasSufficientTraffic());
        assertFalse(health.isDegraded());
        assertTrue(health.signals().isEmpty());
        assertTrue(health.notes().contains("insufficient traffic in baseline window (99 hits < 100)"));
    }

    @Test
    void whenEvaluating_givenBaselineWith100Hits_shouldEvaluateRules() {
        final ServiceHealth health = health(metrics(100, 50, 100.0), metrics(100, 0, 100.0));

        assertTrue(health.hasSufficientTraffic());
        assertTrue(health.isDegraded());
    }

    @Test
    void whenGettingNotes_givenNoTrafficInEitherWindow_shouldExplainMissingMetrics() {
        final ServiceHealth health = health(ApmMetrics.empty(), ApmMetrics.empty());

        assertTrue(health.notes().contains(
                "no trace metrics found for operation servlet.request in either window"));
    }

    @Test
    void whenGettingNotes_givenNoCurrentLatency_shouldExplainMissingLatency() {
        final ServiceHealth health = health(new ApmMetrics(1000, 0, null, null, null), metrics(1000, 0, 100.0));

        assertTrue(health.notes().contains(
                "latency distribution metric trace.servlet.request returned no data"));
    }

    @Test
    void whenComparingErrorRate_shouldUsePercentages() {
        final ServiceHealth health = health(metrics(1000, 20, 100.0), metrics(1000, 10, 100.0));

        assertEquals(2.0, health.errorRate().current(), 0.0001);
        assertEquals(1.0, health.errorRate().baseline(), 0.0001);
        assertEquals(100.0, health.errorRate().deltaPct(), 0.0001);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ServiceHealthTest`
Expected: compilation failure (`cannot find symbol: class ServiceHealth`).

- [ ] **Step 3: Implement**

`src/main/java/co/fanki/datadog/traceinspector/domain/ServiceHealth.java`:

```java
package co.fanki.datadog.traceinspector.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Health of one service in a time window compared with the previous window of the same length.
 *
 * <p>Holds the degraded rules from the APM service health use case. The rules are only evaluated
 * when the baseline window has at least {@link #MIN_BASELINE_HITS} hits (BR-11a). The service is
 * degraded when any of these holds:</p>
 * <ul>
 *   <li>BR-12: error rate at least doubled and rose by at least 1 percentage point.</li>
 *   <li>BR-13: p95 latency is at least 1.5 times the baseline.</li>
 *   <li>BR-14: hits dropped to half the baseline or less.</li>
 * </ul>
 *
 * @param service the service name
 * @param env the environment
 * @param operation the APM operation the metrics belong to
 * @param window the current window
 * @param current the metrics in the current window
 * @param baseline the metrics in the previous window
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public record ServiceHealth(
        String service,
        String env,
        ApmOperation operation,
        TimeWindow window,
        ApmMetrics current,
        ApmMetrics baseline
) {

    /** Minimum hits in the baseline window before the degraded rules are evaluated. */
    public static final long MIN_BASELINE_HITS = 100L;

    /** The current error rate must be at least this many times the baseline. */
    public static final double ERROR_RATE_MULTIPLIER = 2.0;

    /** The current error rate must exceed the baseline by at least this many percentage points. */
    public static final double ERROR_RATE_MIN_INCREASE_POINTS = 1.0;

    /** The current p95 must be at least this many times the baseline p95. */
    public static final double LATENCY_P95_MULTIPLIER = 1.5;

    /** Current hits at or below this fraction of the baseline count as a traffic drop. */
    public static final double TRAFFIC_DROP_RATIO = 0.5;

    /**
     * Creates a new ServiceHealth.
     *
     * @throws NullPointerException if any argument is null
     */
    public ServiceHealth {
        Objects.requireNonNull(service, "service must not be null");
        Objects.requireNonNull(env, "env must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(window, "window must not be null");
        Objects.requireNonNull(current, "current must not be null");
        Objects.requireNonNull(baseline, "baseline must not be null");
    }

    /**
     * Returns the baseline window, which is the previous window of the same length.
     *
     * @return the baseline window
     */
    public TimeWindow baselineWindow() {
        return window.previous();
    }

    /**
     * Compares hits.
     *
     * @return current vs baseline hits
     */
    public MetricComparison hits() {
        return new MetricComparison((double) current.hits(), (double) baseline.hits());
    }

    /**
     * Compares errors.
     *
     * @return current vs baseline errors
     */
    public MetricComparison errors() {
        return new MetricComparison((double) current.errors(), (double) baseline.errors());
    }

    /**
     * Compares the error rate, in percent.
     *
     * @return current vs baseline error rate
     */
    public MetricComparison errorRate() {
        return new MetricComparison(current.errorRate(), baseline.errorRate());
    }

    /**
     * Compares p50 latency, in milliseconds.
     *
     * @return current vs baseline p50
     */
    public MetricComparison latencyP50() {
        return new MetricComparison(current.latencyP50(), baseline.latencyP50());
    }

    /**
     * Compares p95 latency, in milliseconds.
     *
     * @return current vs baseline p95
     */
    public MetricComparison latencyP95() {
        return new MetricComparison(current.latencyP95(), baseline.latencyP95());
    }

    /**
     * Compares p99 latency, in milliseconds.
     *
     * @return current vs baseline p99
     */
    public MetricComparison latencyP99() {
        return new MetricComparison(current.latencyP99(), baseline.latencyP99());
    }

    /**
     * Tells whether the baseline has enough traffic to evaluate the degraded rules (BR-11a).
     *
     * @return true if the baseline has at least {@link #MIN_BASELINE_HITS} hits
     */
    public boolean hasSufficientTraffic() {
        return baseline.hits() >= MIN_BASELINE_HITS;
    }

    /**
     * Returns one human-readable entry per degraded rule that holds (BR-12, BR-13, BR-14).
     *
     * @return the signals, empty when not degraded or when traffic is insufficient
     */
    public List<String> signals() {
        if (!hasSufficientTraffic()) {
            return List.of();
        }

        final List<String> signals = new ArrayList<>();

        final double currentRate = current.errorRate();
        final double baselineRate = baseline.errorRate();
        if (currentRate >= ERROR_RATE_MULTIPLIER * baselineRate
                && currentRate - baselineRate >= ERROR_RATE_MIN_INCREASE_POINTS) {
            signals.add(String.format(Locale.ROOT, "error rate %.2f%% -> %.2f%%", baselineRate, currentRate));
        }

        final Double currentP95 = current.latencyP95();
        final Double baselineP95 = baseline.latencyP95();
        if (currentP95 != null && baselineP95 != null && baselineP95 > 0
                && currentP95 >= LATENCY_P95_MULTIPLIER * baselineP95) {
            signals.add(String.format(Locale.ROOT, "p95 latency %.1fms -> %.1fms", baselineP95, currentP95));
        }

        if (current.hits() <= TRAFFIC_DROP_RATIO * baseline.hits()) {
            signals.add(String.format(Locale.ROOT, "hits %d -> %d", baseline.hits(), current.hits()));
        }

        return List.copyOf(signals);
    }

    /**
     * Tells whether the service is degraded.
     *
     * @return true if at least one degraded rule holds
     */
    public boolean isDegraded() {
        return !signals().isEmpty();
    }

    /**
     * Returns explanations about missing or insufficient data (BR-10, BR-11a, BR-21).
     *
     * @return the notes, possibly empty
     */
    public List<String> notes() {
        final List<String> notes = new ArrayList<>();
        if (current.hits() == 0 && baseline.hits() == 0) {
            notes.add("no trace metrics found for operation " + operation.name() + " in either window");
        }
        if (!hasSufficientTraffic()) {
            notes.add(String.format(Locale.ROOT, "insufficient traffic in baseline window (%d hits < %d)",
                    baseline.hits(), MIN_BASELINE_HITS));
        }
        if (!current.hasLatency()) {
            notes.add("latency distribution metric trace." + operation.name() + " returned no data");
        }
        return List.copyOf(notes);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ServiceHealthTest`
Expected: `Tests run: 17, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit** (ask for approval of the message first)

```bash
git add src/main/java/co/fanki/datadog/traceinspector/domain/ServiceHealth.java \
        src/test/java/co/fanki/datadog/traceinspector/domain/ServiceHealthTest.java
git commit -m "Add ServiceHealth with degraded rules for APM comparisons"
```

---

### Task 3: Resource ranking (ResourceStats, ResourceSortCriteria, TopResources)

Implements BR-16 to BR-20 and the empty-data note from BR-21.

**Files:**
- Create: `src/main/java/co/fanki/datadog/traceinspector/domain/ResourceStats.java`
- Create: `src/main/java/co/fanki/datadog/traceinspector/domain/ResourceSortCriteria.java`
- Create: `src/main/java/co/fanki/datadog/traceinspector/domain/TopResources.java`
- Test: `src/test/java/co/fanki/datadog/traceinspector/domain/ResourceSortCriteriaTest.java`
- Test: `src/test/java/co/fanki/datadog/traceinspector/domain/TopResourcesTest.java`

**Interfaces:**
- Consumes (Task 1): `ApmMetrics`, `ApmOperation`, `TimeWindow`.
- Produces:
  - `record ResourceStats(String resource, ApmMetrics metrics)`
  - `enum ResourceSortCriteria { ERRORS, ERROR_RATE, LATENCY_P95, HITS }`; constants `MIN_HITS_FOR_ERROR_RATE = 20L`, `DEFAULT_LIMIT = 10`, `MAX_LIMIT = 50`; `String key()`; `static ResourceSortCriteria fromKey(String)`; `static void requireValidLimit(int)`; `List<ResourceStats> rank(List<ResourceStats>, int limit)`
  - `record TopResources(String service, String env, ApmOperation operation, TimeWindow window, ResourceSortCriteria sortBy, List<ResourceStats> resources)`; `List<String> notes()`

- [ ] **Step 1: Write the failing tests**

`src/test/java/co/fanki/datadog/traceinspector/domain/ResourceSortCriteriaTest.java`:

```java
package co.fanki.datadog.traceinspector.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for ResourceSortCriteria ranking.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class ResourceSortCriteriaTest {

    private static ResourceStats stats(final String name, final long hits, final long errors, final Double p95) {
        return new ResourceStats(name, new ApmMetrics(hits, errors, null, p95, null));
    }

    private static List<String> names(final List<ResourceStats> ranked) {
        return ranked.stream().map(ResourceStats::resource).toList();
    }

    @Test
    void whenParsingKey_givenErrorRate_shouldReturnErrorRate() {
        assertEquals(ResourceSortCriteria.ERROR_RATE, ResourceSortCriteria.fromKey("errorRate"));
    }

    @Test
    void whenParsingKey_givenUnknownKey_shouldThrowWithExpectedValues() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ResourceSortCriteria.fromKey("bogus"));

        assertTrue(e.getMessage().contains("errors, errorRate, latencyP95, hits"));
    }

    @Test
    void whenRankingByErrors_givenTie_shouldBreakTieByHits() {
        final List<ResourceStats> input = List.of(
                stats("a", 100, 5, null), stats("c", 50, 10, null), stats("b", 200, 10, null));

        assertEquals(List.of("b", "c", "a"), names(ResourceSortCriteria.ERRORS.rank(input, 10)));
    }

    @Test
    void whenRankingByErrorRate_givenLowTrafficResource_shouldExcludeIt() {
        final List<ResourceStats> input = List.of(
                stats("tiny", 1, 1, null), stats("mid", 20, 1, null), stats("big", 100, 10, null));

        assertEquals(List.of("big", "mid"), names(ResourceSortCriteria.ERROR_RATE.rank(input, 10)));
    }

    @Test
    void whenRankingByLatency_givenNullLatency_shouldPlaceItLast() {
        final List<ResourceStats> input = List.of(
                stats("x", 1000, 0, null), stats("y", 10, 0, 500.0), stats("z", 10, 0, 900.0));

        assertEquals(List.of("z", "y", "x"), names(ResourceSortCriteria.LATENCY_P95.rank(input, 10)));
    }

    @Test
    void whenRankingByHits_shouldOrderDescending() {
        final List<ResourceStats> input = List.of(
                stats("low", 10, 0, null), stats("high", 1000, 0, null), stats("mid", 100, 0, null));

        assertEquals(List.of("high", "mid", "low"), names(ResourceSortCriteria.HITS.rank(input, 10)));
    }

    @Test
    void whenRanking_givenLimitSmallerThanInput_shouldTruncate() {
        final List<ResourceStats> input = List.of(
                stats("a", 3, 0, null), stats("b", 2, 0, null), stats("c", 1, 0, null));

        assertEquals(List.of("a", "b"), names(ResourceSortCriteria.HITS.rank(input, 2)));
    }

    @Test
    void whenValidatingLimit_givenZero_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> ResourceSortCriteria.requireValidLimit(0));
    }

    @Test
    void whenValidatingLimit_given51_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> ResourceSortCriteria.requireValidLimit(51));
    }

    @Test
    void whenCreatingStats_givenBlankResource_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> stats(" ", 1, 0, null));
    }
}
```

`src/test/java/co/fanki/datadog/traceinspector/domain/TopResourcesTest.java`:

```java
package co.fanki.datadog.traceinspector.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for TopResources.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class TopResourcesTest {

    private static final TimeWindow WINDOW = new TimeWindow(
            Instant.parse("2026-09-23T13:00:00Z"),
            Instant.parse("2026-09-23T14:00:00Z")
    );

    @Test
    void whenGettingNotes_givenNoResources_shouldExplainMissingMetrics() {
        final TopResources top = new TopResources("payments", "prod", ApmOperation.detected("servlet.request"),
                WINDOW, ResourceSortCriteria.ERRORS, List.of());

        assertEquals(List.of("no trace metrics found for operation servlet.request"), top.notes());
    }

    @Test
    void whenGettingNotes_givenResources_shouldBeEmpty() {
        final ResourceStats stats = new ResourceStats("GET /a", new ApmMetrics(1, 0, null, null, null));
        final TopResources top = new TopResources("payments", "prod", ApmOperation.detected("servlet.request"),
                WINDOW, ResourceSortCriteria.ERRORS, List.of(stats));

        assertTrue(top.notes().isEmpty());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest='ResourceSortCriteriaTest,TopResourcesTest'`
Expected: compilation failure (`cannot find symbol: class ResourceStats`).

- [ ] **Step 3: Implement**

`src/main/java/co/fanki/datadog/traceinspector/domain/ResourceStats.java`:

```java
package co.fanki.datadog.traceinspector.domain;

import java.util.Objects;

/**
 * APM metrics for one resource (endpoint) of a service.
 *
 * @param resource the resource name, such as {@code POST /api/checkout}; never blank
 * @param metrics the metrics for this resource
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public record ResourceStats(String resource, ApmMetrics metrics) {

    /**
     * Creates new ResourceStats.
     *
     * @throws IllegalArgumentException if resource is blank
     */
    public ResourceStats {
        Objects.requireNonNull(resource, "resource must not be null");
        Objects.requireNonNull(metrics, "metrics must not be null");
        if (resource.isBlank()) {
            throw new IllegalArgumentException("resource must not be blank");
        }
    }
}
```

`src/main/java/co/fanki/datadog/traceinspector/domain/ResourceSortCriteria.java`:

```java
package co.fanki.datadog.traceinspector.domain;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * How to rank a service's resources (BR-16 to BR-20).
 *
 * <p>Ranking is always descending. Ties are broken by hits, descending. Resources with null
 * latency go last when ranking by latency. When ranking by error rate, resources with fewer than
 * {@link #MIN_HITS_FOR_ERROR_RATE} hits are excluded.</p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public enum ResourceSortCriteria {

    /** By error count. */
    ERRORS("errors"),
    /** By error rate. */
    ERROR_RATE("errorRate"),
    /** By p95 latency. */
    LATENCY_P95("latencyP95"),
    /** By hit count. */
    HITS("hits");

    /** Minimum hits for a resource to be ranked by error rate (BR-17). */
    public static final long MIN_HITS_FOR_ERROR_RATE = 20L;

    /** Default number of resources returned (BR-20). */
    public static final int DEFAULT_LIMIT = 10;

    /** Maximum number of resources returned (BR-20). */
    public static final int MAX_LIMIT = 50;

    private final String key;

    ResourceSortCriteria(final String key) {
        this.key = key;
    }

    /**
     * Returns the key used in tool input.
     *
     * @return the key, such as {@code errorRate}
     */
    public String key() {
        return key;
    }

    /**
     * Parses a tool input key.
     *
     * @param key the key, must not be null
     *
     * @return the matching criteria
     *
     * @throws IllegalArgumentException if the key is unknown
     */
    public static ResourceSortCriteria fromKey(final String key) {
        Objects.requireNonNull(key, "key must not be null");
        for (final ResourceSortCriteria criteria : values()) {
            if (criteria.key.equals(key)) {
                return criteria;
            }
        }
        final String expected = Arrays.stream(values())
                .map(ResourceSortCriteria::key)
                .collect(Collectors.joining(", "));
        throw new IllegalArgumentException("Unknown sortBy: " + key + ". Expected one of: " + expected);
    }

    /**
     * Validates the number of resources to return.
     *
     * @param limit the requested limit
     *
     * @throws IllegalArgumentException if limit is not between 1 and {@link #MAX_LIMIT}
     */
    public static void requireValidLimit(final int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
    }

    /**
     * Ranks resources by this criteria.
     *
     * @param resources the resources to rank, must not be null
     * @param limit the maximum number of resources to return
     *
     * @return the ranked resources, at most {@code limit}
     *
     * @throws IllegalArgumentException if limit is out of range
     */
    public List<ResourceStats> rank(final List<ResourceStats> resources, final int limit) {
        Objects.requireNonNull(resources, "resources must not be null");
        requireValidLimit(limit);
        return resources.stream()
                .filter(this::isEligible)
                .sorted(comparator())
                .limit(limit)
                .toList();
    }

    private boolean isEligible(final ResourceStats stats) {
        return this != ERROR_RATE || stats.metrics().hits() >= MIN_HITS_FOR_ERROR_RATE;
    }

    private Comparator<ResourceStats> comparator() {
        final Comparator<ResourceStats> byHitsDesc =
                Comparator.comparingLong((ResourceStats r) -> r.metrics().hits()).reversed();
        final Comparator<ResourceStats> primary = switch (this) {
            case ERRORS -> Comparator.comparingLong((ResourceStats r) -> r.metrics().errors()).reversed();
            case ERROR_RATE -> Comparator.comparingDouble((ResourceStats r) -> r.metrics().errorRate()).reversed();
            case LATENCY_P95 -> Comparator.comparing(
                    (ResourceStats r) -> r.metrics().latencyP95(), Comparator.nullsLast(Comparator.reverseOrder()));
            case HITS -> byHitsDesc;
        };
        return primary.thenComparing(byHitsDesc);
    }
}
```

`src/main/java/co/fanki/datadog/traceinspector/domain/TopResources.java`:

```java
package co.fanki.datadog.traceinspector.domain;

import java.util.List;
import java.util.Objects;

/**
 * A service's resources ranked by one criteria over one time window.
 *
 * @param service the service name
 * @param env the environment
 * @param operation the APM operation the metrics belong to
 * @param window the queried window
 * @param sortBy the ranking criteria
 * @param resources the ranked resources
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public record TopResources(
        String service,
        String env,
        ApmOperation operation,
        TimeWindow window,
        ResourceSortCriteria sortBy,
        List<ResourceStats> resources
) {

    /**
     * Creates new TopResources.
     *
     * @throws NullPointerException if any argument is null
     */
    public TopResources {
        Objects.requireNonNull(service, "service must not be null");
        Objects.requireNonNull(env, "env must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(window, "window must not be null");
        Objects.requireNonNull(sortBy, "sortBy must not be null");
        resources = List.copyOf(Objects.requireNonNull(resources, "resources must not be null"));
    }

    /**
     * Returns explanations about missing data (BR-21).
     *
     * @return the notes, possibly empty
     */
    public List<String> notes() {
        if (resources.isEmpty()) {
            return List.of("no trace metrics found for operation " + operation.name());
        }
        return List.of();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest='ResourceSortCriteriaTest,TopResourcesTest'`
Expected: `Tests run: 12, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit** (ask for approval of the message first)

```bash
git add src/main/java/co/fanki/datadog/traceinspector/domain/{ResourceStats,ResourceSortCriteria,TopResources}.java \
        src/test/java/co/fanki/datadog/traceinspector/domain/{ResourceSortCriteria,TopResources}Test.java
git commit -m "Add resource ranking for APM top resources"
```

---

### Task 4: ApmMetricsClient and implementation

Implements BR-1, BR-3 (detection), BR-7, BR-8, BR-10 (seconds to ms), BR-15 (grouped query), BR-19 (missing errors = 0).

**Files:**
- Create: `src/main/java/co/fanki/datadog/traceinspector/datadog/ApmMetricsClient.java`
- Create: `src/main/java/co/fanki/datadog/traceinspector/datadog/ApmMetricsClientImpl.java`
- Test: `src/test/java/co/fanki/datadog/traceinspector/datadog/ApmMetricsClientImplTest.java`

**Interfaces:**
- Consumes (Tasks 1, 3): `TimeWindow`, `ApmMetrics`, `ResourceStats`. Existing: `DatadogConfig#buildApiClient()`, `RetryExecutor(RetryConfig)`, `RetryConfig.defaults()`, `DatadogApiException(String)`.
- Produces:
  - `interface ApmMetricsClient`:
    - `Optional<String> detectEntryOperation(String service, String env, TimeWindow window)`
    - `ApmMetrics queryServiceMetrics(String service, String env, String operation, TimeWindow window)`
    - `List<ResourceStats> queryResourceMetrics(String service, String env, String operation, TimeWindow window)`
  - `ApmMetricsClientImpl(DatadogConfig)` and `ApmMetricsClientImpl(MetricsApi, SpansApi, RetryConfig)`

**Query shapes** (the scope is `{service:<s>,env:<e>}`, plus ` by {resource_name}` for resources):

| name     | query                                             | aggregator   |
|----------|---------------------------------------------------|--------------|
| `hits`   | `sum:trace.<op>.hits<scope>.as_count()`           | `SUM`        |
| `errors` | `sum:trace.<op>.errors<scope>.as_count()`         | `SUM`        |
| `p50`    | `p50:trace.<op><scope>`                           | `PERCENTILE` |
| `p95`    | `p95:trace.<op><scope>`                           | `PERCENTILE` |
| `p99`    | `p99:trace.<op><scope>`                           | `PERCENTILE` |

Response columns are matched by name (`hits`, `errors`, ...). If Datadog names them differently, data columns are matched by position in the order above. Latency values are seconds and are converted to milliseconds.

- [ ] **Step 1: Write the failing test**

`src/test/java/co/fanki/datadog/traceinspector/datadog/ApmMetricsClientImplTest.java`:

```java
package co.fanki.datadog.traceinspector.datadog;

import co.fanki.datadog.traceinspector.domain.ApmMetrics;
import co.fanki.datadog.traceinspector.domain.ResourceStats;
import co.fanki.datadog.traceinspector.domain.TimeWindow;
import com.datadog.api.client.v2.api.MetricsApi;
import com.datadog.api.client.v2.api.SpansApi;
import com.datadog.api.client.v2.model.DataScalarColumn;
import com.datadog.api.client.v2.model.GroupScalarColumn;
import com.datadog.api.client.v2.model.MetricsAggregator;
import com.datadog.api.client.v2.model.MetricsScalarQuery;
import com.datadog.api.client.v2.model.ScalarColumn;
import com.datadog.api.client.v2.model.ScalarFormulaQueryRequest;
import com.datadog.api.client.v2.model.ScalarFormulaQueryResponse;
import com.datadog.api.client.v2.model.ScalarFormulaRequestAttributes;
import com.datadog.api.client.v2.model.ScalarFormulaResponseAtrributes;
import com.datadog.api.client.v2.model.ScalarQuery;
import com.datadog.api.client.v2.model.ScalarResponse;
import com.datadog.api.client.v2.model.Span;
import com.datadog.api.client.v2.model.SpansAttributes;
import com.datadog.api.client.v2.model.SpansListRequest;
import com.datadog.api.client.v2.model.SpansListResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for ApmMetricsClientImpl using stubbed SDK APIs.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class ApmMetricsClientImplTest {

    private static final TimeWindow WINDOW = new TimeWindow(
            Instant.parse("2026-09-23T13:00:00Z"),
            Instant.parse("2026-09-23T14:00:00Z")
    );

    private StubMetricsApi metricsApi;
    private StubSpansApi spansApi;
    private ApmMetricsClient client;

    @BeforeEach
    void setUp() {
        metricsApi = new StubMetricsApi();
        spansApi = new StubSpansApi();
        client = new ApmMetricsClientImpl(metricsApi, spansApi, RetryConfig.defaults());
    }

    @Test
    void whenQueryingServiceMetrics_givenFullResponse_shouldMapCountsAndConvertLatencyToMillis() {
        metricsApi.response = response(
                data("hits", 12000.0), data("errors", 340.0),
                data("p50", 0.045), data("p95", 0.31), data("p99", 0.9));

        final ApmMetrics metrics = client.queryServiceMetrics("payments", "prod", "servlet.request", WINDOW);

        assertEquals(12000L, metrics.hits());
        assertEquals(340L, metrics.errors());
        assertEquals(45.0, metrics.latencyP50(), 0.0001);
        assertEquals(310.0, metrics.latencyP95(), 0.0001);
        assertEquals(900.0, metrics.latencyP99(), 0.0001);
    }

    @Test
    void whenQueryingServiceMetrics_shouldBuildTraceMetricQueries() {
        metricsApi.response = response();

        client.queryServiceMetrics("payments", "prod", "servlet.request", WINDOW);

        final ScalarFormulaRequestAttributes attributes = metricsApi.lastRequest.getData().getAttributes();
        assertEquals(WINDOW.from().toEpochMilli(), attributes.getFrom());
        assertEquals(WINDOW.to().toEpochMilli(), attributes.getTo());

        final List<MetricsScalarQuery> queries = attributes.getQueries().stream()
                .map(ScalarQuery::getMetricsScalarQuery)
                .toList();
        assertEquals(List.of("hits", "errors", "p50", "p95", "p99"),
                queries.stream().map(MetricsScalarQuery::getName).toList());
        assertEquals("sum:trace.servlet.request.hits{service:payments,env:prod}.as_count()",
                queries.get(0).getQuery());
        assertEquals("sum:trace.servlet.request.errors{service:payments,env:prod}.as_count()",
                queries.get(1).getQuery());
        assertEquals("p95:trace.servlet.request{service:payments,env:prod}", queries.get(3).getQuery());
        assertEquals(MetricsAggregator.SUM, queries.get(0).getAggregator());
        assertEquals(MetricsAggregator.PERCENTILE, queries.get(3).getAggregator());
    }

    @Test
    void whenQueryingServiceMetrics_givenNoLatencyColumns_shouldReturnNullLatency() {
        metricsApi.response = response(data("hits", 10.0), data("errors", 1.0));

        final ApmMetrics metrics = client.queryServiceMetrics("payments", "prod", "servlet.request", WINDOW);

        assertEquals(10L, metrics.hits());
        assertNull(metrics.latencyP95());
    }

    @Test
    void whenQueryingServiceMetrics_givenEmptyResponse_shouldReturnEmptyMetrics() {
        metricsApi.response = new ScalarFormulaQueryResponse();

        final ApmMetrics metrics = client.queryServiceMetrics("payments", "prod", "servlet.request", WINDOW);

        assertEquals(ApmMetrics.empty(), metrics);
    }

    @Test
    void whenQueryingServiceMetrics_givenUnnamedColumns_shouldMapByPosition() {
        metricsApi.response = response(
                data("query1", 100.0), data("query2", 5.0),
                data("query3", 0.01), data("query4", 0.02), data("query5", 0.03));

        final ApmMetrics metrics = client.queryServiceMetrics("payments", "prod", "servlet.request", WINDOW);

        assertEquals(100L, metrics.hits());
        assertEquals(5L, metrics.errors());
        assertEquals(20.0, metrics.latencyP95(), 0.0001);
    }

    @Test
    void whenQueryingResourceMetrics_shouldGroupByResourceName() {
        metricsApi.response = response();

        client.queryResourceMetrics("payments", "prod", "servlet.request", WINDOW);

        final MetricsScalarQuery hits = metricsApi.lastRequest.getData().getAttributes().getQueries().get(0)
                .getMetricsScalarQuery();
        assertEquals("sum:trace.servlet.request.hits{service:payments,env:prod} by {resource_name}.as_count()",
                hits.getQuery());
    }

    @Test
    void whenQueryingResourceMetrics_givenGroupedResponse_shouldMapOneStatsPerRow() {
        metricsApi.response = response(
                group("GET /a", "resource_name:POST /b"),
                data("hits", 100.0, 50.0),
                data("errors", 5.0, null),
                data("p95", 0.2, null));

        final List<ResourceStats> stats = client.queryResourceMetrics("payments", "prod", "servlet.request", WINDOW);

        assertEquals(2, stats.size());
        assertEquals("GET /a", stats.get(0).resource());
        assertEquals(5L, stats.get(0).metrics().errors());
        assertEquals(200.0, stats.get(0).metrics().latencyP95(), 0.0001);
        assertEquals("POST /b", stats.get(1).resource());
        assertEquals(0L, stats.get(1).metrics().errors());
        assertNull(stats.get(1).metrics().latencyP95());
    }

    @Test
    void whenQueryingResourceMetrics_givenEmptyResponse_shouldReturnEmptyList() {
        metricsApi.response = new ScalarFormulaQueryResponse();

        assertTrue(client.queryResourceMetrics("payments", "prod", "servlet.request", WINDOW).isEmpty());
    }

    @Test
    void whenDetectingOperation_givenSpanWithOperationName_shouldReturnIt() {
        spansApi.response = spans(new SpansAttributes().attributes(Map.of("operation_name", "servlet.request")));

        final Optional<String> operation = client.detectEntryOperation("payments", "prod", WINDOW);

        assertEquals(Optional.of("servlet.request"), operation);
        assertEquals("service:payments env:prod @span.kind:server",
                spansApi.lastRequest.getData().getAttributes().getFilter().getQuery());
        assertEquals(1, spansApi.lastRequest.getData().getAttributes().getPage().getLimit());
    }

    @Test
    void whenDetectingOperation_givenOperationNameInCustomAttributes_shouldReturnIt() {
        spansApi.response = spans(new SpansAttributes().custom(Map.of("operation_name", "next.request")));

        assertEquals(Optional.of("next.request"), client.detectEntryOperation("portal", "prod", WINDOW));
    }

    @Test
    void whenDetectingOperation_givenNoSpans_shouldReturnEmpty() {
        spansApi.response = new SpansListResponse().data(List.of());

        assertEquals(Optional.empty(), client.detectEntryOperation("payments", "prod", WINDOW));
    }

    private static ScalarColumn data(final String name, final Double... values) {
        return new ScalarColumn(new DataScalarColumn().name(name).values(Arrays.asList(values)));
    }

    private static ScalarColumn group(final String... values) {
        final List<List<String>> rows = new ArrayList<>();
        for (final String value : values) {
            rows.add(List.of(value));
        }
        return new ScalarColumn(new GroupScalarColumn().name("resource_name").values(rows));
    }

    private static ScalarFormulaQueryResponse response(final ScalarColumn... columns) {
        return new ScalarFormulaQueryResponse().data(new ScalarResponse()
                .attributes(new ScalarFormulaResponseAtrributes().columns(List.of(columns))));
    }

    private static SpansListResponse spans(final SpansAttributes attributes) {
        return new SpansListResponse().data(List.of(new Span().attributes(attributes)));
    }

    private static final class StubMetricsApi extends MetricsApi {
        private ScalarFormulaQueryResponse response = new ScalarFormulaQueryResponse();
        private ScalarFormulaQueryRequest lastRequest;

        @Override
        public ScalarFormulaQueryResponse queryScalarData(final ScalarFormulaQueryRequest body) {
            lastRequest = body;
            return response;
        }
    }

    private static final class StubSpansApi extends SpansApi {
        private SpansListResponse response = new SpansListResponse();
        private SpansListRequest lastRequest;

        @Override
        public SpansListResponse listSpans(final SpansListRequest body) {
            lastRequest = body;
            return response;
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ApmMetricsClientImplTest`
Expected: compilation failure (`cannot find symbol: class ApmMetricsClient`).

- [ ] **Step 3: Implement the interface**

`src/main/java/co/fanki/datadog/traceinspector/datadog/ApmMetricsClient.java`:

```java
package co.fanki.datadog.traceinspector.datadog;

import co.fanki.datadog.traceinspector.domain.ApmMetrics;
import co.fanki.datadog.traceinspector.domain.ResourceStats;
import co.fanki.datadog.traceinspector.domain.TimeWindow;

import java.util.List;
import java.util.Optional;

/**
 * Reads APM trace metrics from Datadog.
 *
 * <p>Numbers always come from trace metrics ({@code trace.<operation>.*}), which Datadog computes
 * on 100% of ingested traffic. Indexed spans are only used to detect the operation name.</p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public interface ApmMetricsClient {

    /**
     * Detects the operation name of a service's entry spans.
     *
     * <p>Looks up the most recent span with {@code span.kind:server} in the window.</p>
     *
     * @param service the service name
     * @param env the environment
     * @param window the window to search in
     *
     * @return the operation name, or empty if no entry span exists in the window
     *
     * @throws DatadogApiException if the API call fails after retries
     */
    Optional<String> detectEntryOperation(String service, String env, TimeWindow window);

    /**
     * Queries hits, errors and latency percentiles for a service.
     *
     * @param service the service name
     * @param env the environment
     * @param operation the operation name, such as {@code servlet.request}
     * @param window the window to aggregate over
     *
     * @return the metrics; zero counts and null latency when there is no data
     *
     * @throws DatadogApiException if the API call fails after retries
     */
    ApmMetrics queryServiceMetrics(String service, String env, String operation, TimeWindow window);

    /**
     * Queries hits, errors and latency percentiles for each resource of a service.
     *
     * @param service the service name
     * @param env the environment
     * @param operation the operation name
     * @param window the window to aggregate over
     *
     * @return one entry per resource, in the order returned by Datadog; empty when there is no data
     *
     * @throws DatadogApiException if the API call fails after retries
     */
    List<ResourceStats> queryResourceMetrics(String service, String env, String operation, TimeWindow window);
}
```

- [ ] **Step 4: Implement the client**

`src/main/java/co/fanki/datadog/traceinspector/datadog/ApmMetricsClientImpl.java`:

```java
package co.fanki.datadog.traceinspector.datadog;

import co.fanki.datadog.traceinspector.config.DatadogConfig;
import co.fanki.datadog.traceinspector.domain.ApmMetrics;
import co.fanki.datadog.traceinspector.domain.ResourceStats;
import co.fanki.datadog.traceinspector.domain.TimeWindow;
import com.datadog.api.client.ApiClient;
import com.datadog.api.client.v2.api.MetricsApi;
import com.datadog.api.client.v2.api.SpansApi;
import com.datadog.api.client.v2.model.DataScalarColumn;
import com.datadog.api.client.v2.model.GroupScalarColumn;
import com.datadog.api.client.v2.model.MetricsAggregator;
import com.datadog.api.client.v2.model.MetricsDataSource;
import com.datadog.api.client.v2.model.MetricsScalarQuery;
import com.datadog.api.client.v2.model.ScalarColumn;
import com.datadog.api.client.v2.model.ScalarFormulaQueryRequest;
import com.datadog.api.client.v2.model.ScalarFormulaQueryResponse;
import com.datadog.api.client.v2.model.ScalarFormulaRequest;
import com.datadog.api.client.v2.model.ScalarFormulaRequestAttributes;
import com.datadog.api.client.v2.model.ScalarFormulaRequestType;
import com.datadog.api.client.v2.model.ScalarQuery;
import com.datadog.api.client.v2.model.SpansAttributes;
import com.datadog.api.client.v2.model.SpansListRequest;
import com.datadog.api.client.v2.model.SpansListRequestAttributes;
import com.datadog.api.client.v2.model.SpansListRequestData;
import com.datadog.api.client.v2.model.SpansListRequestPage;
import com.datadog.api.client.v2.model.SpansListRequestType;
import com.datadog.api.client.v2.model.SpansListResponse;
import com.datadog.api.client.v2.model.SpansQueryFilter;
import com.datadog.api.client.v2.model.SpansSort;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * SDK-based implementation of {@link ApmMetricsClient}.
 *
 * <p>Uses the Metrics API v2 scalar endpoint for numbers and the Spans API to detect the
 * operation name. All calls go through {@link RetryExecutor}.</p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public final class ApmMetricsClientImpl implements ApmMetricsClient {

    private static final String HITS = "hits";
    private static final String ERRORS = "errors";
    private static final String P50 = "p50";
    private static final String P95 = "p95";
    private static final String P99 = "p99";
    private static final List<String> QUERY_NAMES = List.of(HITS, ERRORS, P50, P95, P99);

    private static final String RESOURCE_TAG = "resource_name";
    private static final String OPERATION_NAME = "operation_name";
    private static final double SECONDS_TO_MILLIS = 1000.0;
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final MetricsApi metricsApi;
    private final SpansApi spansApi;
    private final RetryExecutor retryExecutor;

    /**
     * Creates a new ApmMetricsClientImpl with the given configuration.
     *
     * @param config the Datadog configuration
     */
    public ApmMetricsClientImpl(final DatadogConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        final ApiClient apiClient = config.buildApiClient();
        this.metricsApi = new MetricsApi(apiClient);
        this.spansApi = new SpansApi(apiClient);
        this.retryExecutor = new RetryExecutor(RetryConfig.defaults());
    }

    /**
     * Creates a new ApmMetricsClientImpl with custom API instances. Useful for testing.
     *
     * @param metricsApi the metrics API instance
     * @param spansApi the spans API instance
     * @param retryConfig the retry configuration
     */
    public ApmMetricsClientImpl(final MetricsApi metricsApi, final SpansApi spansApi, final RetryConfig retryConfig) {
        this.metricsApi = Objects.requireNonNull(metricsApi, "metricsApi must not be null");
        this.spansApi = Objects.requireNonNull(spansApi, "spansApi must not be null");
        this.retryExecutor = new RetryExecutor(Objects.requireNonNull(retryConfig, "retryConfig must not be null"));
    }

    @Override
    public Optional<String> detectEntryOperation(final String service, final String env, final TimeWindow window) {
        Objects.requireNonNull(service, "service must not be null");
        Objects.requireNonNull(env, "env must not be null");
        Objects.requireNonNull(window, "window must not be null");

        final SpansQueryFilter filter = new SpansQueryFilter()
                .query("service:" + service + " env:" + env + " @span.kind:server")
                .from(ISO_FORMATTER.format(window.from()))
                .to(ISO_FORMATTER.format(window.to()));

        final SpansListRequest request = new SpansListRequest()
                .data(new SpansListRequestData()
                        .type(SpansListRequestType.SEARCH_REQUEST)
                        .attributes(new SpansListRequestAttributes()
                                .filter(filter)
                                .page(new SpansListRequestPage().limit(1))
                                .sort(SpansSort.TIMESTAMP_DESCENDING)));

        final SpansListResponse response = retryExecutor.execute(
                () -> spansApi.listSpans(request),
                "detect entry operation"
        );

        if (response == null || response.getData() == null || response.getData().isEmpty()) {
            return Optional.empty();
        }
        final SpansAttributes attributes = response.getData().get(0).getAttributes();
        if (attributes == null) {
            return Optional.empty();
        }
        final Optional<String> fromAttributes = readString(attributes.getAttributes(), OPERATION_NAME);
        if (fromAttributes.isPresent()) {
            return fromAttributes;
        }
        return readString(attributes.getCustom(), OPERATION_NAME);
    }

    @Override
    public ApmMetrics queryServiceMetrics(
            final String service,
            final String env,
            final String operation,
            final TimeWindow window
    ) {
        final ScalarFormulaQueryResponse response = queryScalar(
                buildRequest(service, env, operation, window, ""),
                "query service metrics"
        );
        return toMetrics(dataColumns(response), 0);
    }

    @Override
    public List<ResourceStats> queryResourceMetrics(
            final String service,
            final String env,
            final String operation,
            final TimeWindow window
    ) {
        final ScalarFormulaQueryResponse response = queryScalar(
                buildRequest(service, env, operation, window, " by {" + RESOURCE_TAG + "}"),
                "query resource metrics"
        );
        final List<String> resources = groupValues(response);
        final Map<String, List<Double>> columns = dataColumns(response);

        final List<ResourceStats> stats = new ArrayList<>();
        for (int row = 0; row < resources.size(); row++) {
            final String resource = resources.get(row);
            if (!resource.isBlank()) {
                stats.add(new ResourceStats(resource, toMetrics(columns, row)));
            }
        }
        return List.copyOf(stats);
    }

    private ScalarFormulaQueryRequest buildRequest(
            final String service,
            final String env,
            final String operation,
            final TimeWindow window,
            final String groupBy
    ) {
        Objects.requireNonNull(service, "service must not be null");
        Objects.requireNonNull(env, "env must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(window, "window must not be null");

        final String scope = "{service:" + service + ",env:" + env + "}" + groupBy;
        final String metric = "trace." + operation;

        final List<ScalarQuery> queries = List.of(
                query(HITS, "sum:" + metric + ".hits" + scope + ".as_count()", MetricsAggregator.SUM),
                query(ERRORS, "sum:" + metric + ".errors" + scope + ".as_count()", MetricsAggregator.SUM),
                query(P50, "p50:" + metric + scope, MetricsAggregator.PERCENTILE),
                query(P95, "p95:" + metric + scope, MetricsAggregator.PERCENTILE),
                query(P99, "p99:" + metric + scope, MetricsAggregator.PERCENTILE)
        );

        return new ScalarFormulaQueryRequest()
                .data(new ScalarFormulaRequest()
                        .type(ScalarFormulaRequestType.SCALAR_REQUEST)
                        .attributes(new ScalarFormulaRequestAttributes()
                                .from(window.from().toEpochMilli())
                                .to(window.to().toEpochMilli())
                                .queries(queries)));
    }

    private static ScalarQuery query(final String name, final String query, final MetricsAggregator aggregator) {
        return new ScalarQuery(new MetricsScalarQuery()
                .name(name)
                .query(query)
                .aggregator(aggregator)
                .dataSource(MetricsDataSource.METRICS));
    }

    private ScalarFormulaQueryResponse queryScalar(
            final ScalarFormulaQueryRequest request,
            final String operationName
    ) {
        final ScalarFormulaQueryResponse response = retryExecutor.execute(
                () -> metricsApi.queryScalarData(request),
                operationName
        );
        if (response != null && response.getData() == null
                && response.getErrors() != null && !response.getErrors().isBlank()) {
            throw new DatadogApiException("Metrics query failed: " + response.getErrors());
        }
        return response;
    }

    private static List<ScalarColumn> columns(final ScalarFormulaQueryResponse response) {
        if (response == null || response.getData() == null || response.getData().getAttributes() == null
                || response.getData().getAttributes().getColumns() == null) {
            return List.of();
        }
        return response.getData().getAttributes().getColumns();
    }

    private static Map<String, List<Double>> dataColumns(final ScalarFormulaQueryResponse response) {
        final Map<String, List<Double>> byName = new HashMap<>();
        int position = 0;
        for (final ScalarColumn column : columns(response)) {
            if (column.getActualInstance() instanceof DataScalarColumn data) {
                final List<Double> values = data.getValues() != null ? data.getValues() : List.of();
                byName.put(resolveColumnName(data.getName(), position), values);
                position++;
            }
        }
        return byName;
    }

    private static String resolveColumnName(final String name, final int position) {
        if (name != null && QUERY_NAMES.contains(name)) {
            return name;
        }
        if (position < QUERY_NAMES.size()) {
            return QUERY_NAMES.get(position);
        }
        return name != null ? name : "column" + position;
    }

    private static List<String> groupValues(final ScalarFormulaQueryResponse response) {
        for (final ScalarColumn column : columns(response)) {
            if (column.getActualInstance() instanceof GroupScalarColumn group && group.getValues() != null) {
                final List<String> values = new ArrayList<>();
                for (final List<String> row : group.getValues()) {
                    values.add(row == null || row.isEmpty() || row.get(0) == null ? "" : stripTag(row.get(0)));
                }
                return values;
            }
        }
        return List.of();
    }

    private static String stripTag(final String value) {
        final String prefix = RESOURCE_TAG + ":";
        return value.startsWith(prefix) ? value.substring(prefix.length()) : value;
    }

    private static ApmMetrics toMetrics(final Map<String, List<Double>> columns, final int row) {
        return new ApmMetrics(
                toCount(value(columns, HITS, row)),
                toCount(value(columns, ERRORS, row)),
                toMillis(value(columns, P50, row)),
                toMillis(value(columns, P95, row)),
                toMillis(value(columns, P99, row))
        );
    }

    private static Double value(final Map<String, List<Double>> columns, final String name, final int row) {
        final List<Double> values = columns.get(name);
        if (values == null || row >= values.size()) {
            return null;
        }
        return values.get(row);
    }

    private static long toCount(final Double value) {
        return value == null ? 0L : Math.max(0L, Math.round(value));
    }

    private static Double toMillis(final Double seconds) {
        return seconds == null ? null : seconds * SECONDS_TO_MILLIS;
    }

    private static Optional<String> readString(final Map<String, Object> map, final String key) {
        if (map == null) {
            return Optional.empty();
        }
        final Object value = map.get(key);
        if (value == null || value.toString().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.toString());
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -Dtest=ApmMetricsClientImplTest`
Expected: `Tests run: 11, Failures: 0, Errors: 0`.

If `ScalarFormulaResponseAtrributes` has no `columns(List)` fluent setter, use `setColumns(List)` in the test helper instead.

- [ ] **Step 6: Commit** (ask for approval of the message first)

```bash
git add src/main/java/co/fanki/datadog/traceinspector/datadog/ApmMetricsClient.java \
        src/main/java/co/fanki/datadog/traceinspector/datadog/ApmMetricsClientImpl.java \
        src/test/java/co/fanki/datadog/traceinspector/datadog/ApmMetricsClientImplTest.java
git commit -m "Add APM metrics client backed by Datadog trace metrics"
```

---

### Task 5: ApmHealthService

Implements BR-2, BR-3, BR-4 (orchestration), BR-6 (querying the previous window).

**Files:**
- Create: `src/main/java/co/fanki/datadog/traceinspector/application/ApmHealthService.java`
- Create (test fixture, reused by Tasks 6 and 7): `src/test/java/co/fanki/datadog/traceinspector/datadog/FakeApmMetricsClient.java`
- Test: `src/test/java/co/fanki/datadog/traceinspector/application/ApmHealthServiceTest.java`

**Interfaces:**
- Consumes: `ApmMetricsClient` (Task 4), `ServiceHealth` (Task 2), `TopResources`, `ResourceSortCriteria`, `ResourceStats` (Task 3), `ApmOperation`, `TimeWindow`, `ApmMetrics` (Task 1).
- Produces:
  - `ApmHealthService(ApmMetricsClient metricsClient)`
  - `ServiceHealth serviceHealth(String service, String env, TimeWindow window, String operationOverride)`: `operationOverride` may be null or blank
  - `TopResources topResources(String service, String env, TimeWindow window, String operationOverride, ResourceSortCriteria sortBy, int limit)`
  - Throws `IllegalStateException` with message `No entry spans found for service <s> in env <e>; pass 'operation' explicitly`
  - Test fixture `FakeApmMetricsClient` (public, in test `datadog` package): `detectedOperation(String)`, `metricsFor(TimeWindow, ApmMetrics)`, `resources(List<ResourceStats>)`, `int detectCalls()`, `List<TimeWindow> queriedWindows()`, `String lastOperation()`

- [ ] **Step 1: Write the test fixture**

`src/test/java/co/fanki/datadog/traceinspector/datadog/FakeApmMetricsClient.java`:

```java
package co.fanki.datadog.traceinspector.datadog;

import co.fanki.datadog.traceinspector.domain.ApmMetrics;
import co.fanki.datadog.traceinspector.domain.ResourceStats;
import co.fanki.datadog.traceinspector.domain.TimeWindow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory ApmMetricsClient for tests.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public final class FakeApmMetricsClient implements ApmMetricsClient {

    private final Map<TimeWindow, ApmMetrics> metricsByWindow = new HashMap<>();
    private final List<TimeWindow> queriedWindows = new ArrayList<>();
    private List<ResourceStats> resources = List.of();
    private String detectedOperation;
    private String lastOperation;
    private int detectCalls;

    /**
     * Sets the operation returned by detection; null means no entry span found.
     *
     * @param operation the operation name
     */
    public void detectedOperation(final String operation) {
        this.detectedOperation = operation;
    }

    /**
     * Sets the metrics returned for a window.
     *
     * @param window the window
     * @param metrics the metrics
     */
    public void metricsFor(final TimeWindow window, final ApmMetrics metrics) {
        metricsByWindow.put(window, metrics);
    }

    /**
     * Sets the resources returned by queryResourceMetrics.
     *
     * @param resources the resources
     */
    public void resources(final List<ResourceStats> resources) {
        this.resources = List.copyOf(resources);
    }

    /**
     * Returns how many times detection was called.
     *
     * @return the call count
     */
    public int detectCalls() {
        return detectCalls;
    }

    /**
     * Returns the windows passed to the metric queries, in call order.
     *
     * @return the windows
     */
    public List<TimeWindow> queriedWindows() {
        return List.copyOf(queriedWindows);
    }

    /**
     * Returns the operation passed to the last metric query.
     *
     * @return the operation name, or null if no query ran
     */
    public String lastOperation() {
        return lastOperation;
    }

    @Override
    public Optional<String> detectEntryOperation(final String service, final String env, final TimeWindow window) {
        detectCalls++;
        return Optional.ofNullable(detectedOperation);
    }

    @Override
    public ApmMetrics queryServiceMetrics(
            final String service,
            final String env,
            final String operation,
            final TimeWindow window
    ) {
        lastOperation = operation;
        queriedWindows.add(window);
        return metricsByWindow.getOrDefault(window, ApmMetrics.empty());
    }

    @Override
    public List<ResourceStats> queryResourceMetrics(
            final String service,
            final String env,
            final String operation,
            final TimeWindow window
    ) {
        lastOperation = operation;
        queriedWindows.add(window);
        return resources;
    }
}
```

- [ ] **Step 2: Write the failing test**

`src/test/java/co/fanki/datadog/traceinspector/application/ApmHealthServiceTest.java`:

```java
package co.fanki.datadog.traceinspector.application;

import co.fanki.datadog.traceinspector.datadog.FakeApmMetricsClient;
import co.fanki.datadog.traceinspector.domain.ApmMetrics;
import co.fanki.datadog.traceinspector.domain.ApmOperation;
import co.fanki.datadog.traceinspector.domain.ResourceSortCriteria;
import co.fanki.datadog.traceinspector.domain.ResourceStats;
import co.fanki.datadog.traceinspector.domain.ServiceHealth;
import co.fanki.datadog.traceinspector.domain.TimeWindow;
import co.fanki.datadog.traceinspector.domain.TopResources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for ApmHealthService.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class ApmHealthServiceTest {

    private static final TimeWindow WINDOW = new TimeWindow(
            Instant.parse("2026-09-23T13:00:00Z"),
            Instant.parse("2026-09-23T14:00:00Z")
    );

    private FakeApmMetricsClient client;
    private ApmHealthService service;

    @BeforeEach
    void setUp() {
        client = new FakeApmMetricsClient();
        service = new ApmHealthService(client);
    }

    @Test
    void whenGettingHealth_givenProvidedOperation_shouldNotDetect() {
        client.detectedOperation("web.request");

        final ServiceHealth health = service.serviceHealth("payments", "prod", WINDOW, "servlet.request");

        assertEquals(ApmOperation.provided("servlet.request"), health.operation());
        assertEquals(0, client.detectCalls());
        assertEquals("servlet.request", client.lastOperation());
    }

    @Test
    void whenGettingHealth_givenNoOperation_shouldUseDetectedOperation() {
        client.detectedOperation("next.request");

        final ServiceHealth health = service.serviceHealth("portal", "prod", WINDOW, null);

        assertEquals(ApmOperation.detected("next.request"), health.operation());
        assertEquals(1, client.detectCalls());
    }

    @Test
    void whenGettingHealth_givenBlankOperation_shouldDetect() {
        client.detectedOperation("servlet.request");

        final ServiceHealth health = service.serviceHealth("payments", "prod", WINDOW, "  ");

        assertEquals(ApmOperation.Source.DETECTED, health.operation().source());
    }

    @Test
    void whenGettingHealth_givenNothingDetected_shouldThrowWithHint() {
        final IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.serviceHealth("payments", "prod", WINDOW, null));

        assertEquals("No entry spans found for service payments in env prod; pass 'operation' explicitly",
                e.getMessage());
    }

    @Test
    void whenGettingHealth_shouldQueryCurrentAndPreviousWindows() {
        final ApmMetrics current = new ApmMetrics(1000, 20, null, null, null);
        final ApmMetrics baseline = new ApmMetrics(1000, 10, null, null, null);
        client.metricsFor(WINDOW, current);
        client.metricsFor(WINDOW.previous(), baseline);

        final ServiceHealth health = service.serviceHealth("payments", "prod", WINDOW, "servlet.request");

        assertEquals(List.of(WINDOW, WINDOW.previous()), client.queriedWindows());
        assertEquals(current, health.current());
        assertEquals(baseline, health.baseline());
    }

    @Test
    void whenGettingTopResources_shouldRankAndLimit() {
        client.resources(List.of(
                new ResourceStats("a", new ApmMetrics(10, 1, null, null, null)),
                new ResourceStats("b", new ApmMetrics(10, 5, null, null, null)),
                new ResourceStats("c", new ApmMetrics(10, 3, null, null, null))));

        final TopResources top = service.topResources(
                "payments", "prod", WINDOW, "servlet.request", ResourceSortCriteria.ERRORS, 2);

        assertEquals(List.of("b", "c"), top.resources().stream().map(ResourceStats::resource).toList());
        assertEquals(ResourceSortCriteria.ERRORS, top.sortBy());
    }

    @Test
    void whenGettingTopResources_givenInvalidLimit_shouldFailBeforeCallingDatadog() {
        client.detectedOperation("servlet.request");

        assertThrows(IllegalArgumentException.class,
                () -> service.topResources("payments", "prod", WINDOW, null, ResourceSortCriteria.ERRORS, 0));
        assertEquals(0, client.detectCalls());
        assertTrue(client.queriedWindows().isEmpty());
    }

    @Test
    void whenGettingHealth_givenBlankService_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> service.serviceHealth(" ", "prod", WINDOW, "servlet.request"));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn test -Dtest=ApmHealthServiceTest`
Expected: compilation failure (`cannot find symbol: class ApmHealthService`).

- [ ] **Step 4: Implement**

`src/main/java/co/fanki/datadog/traceinspector/application/ApmHealthService.java`:

```java
package co.fanki.datadog.traceinspector.application;

import co.fanki.datadog.traceinspector.datadog.ApmMetricsClient;
import co.fanki.datadog.traceinspector.domain.ApmMetrics;
import co.fanki.datadog.traceinspector.domain.ApmOperation;
import co.fanki.datadog.traceinspector.domain.ResourceSortCriteria;
import co.fanki.datadog.traceinspector.domain.ResourceStats;
import co.fanki.datadog.traceinspector.domain.ServiceHealth;
import co.fanki.datadog.traceinspector.domain.TimeWindow;
import co.fanki.datadog.traceinspector.domain.TopResources;

import java.util.List;
import java.util.Objects;

/**
 * Orchestrates APM health and resource ranking queries.
 *
 * <p>Resolves the operation name and fetches metrics. All comparison and ranking rules live in the
 * domain ({@link ServiceHealth}, {@link ResourceSortCriteria}).</p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public final class ApmHealthService {

    private final ApmMetricsClient metricsClient;

    /**
     * Creates a new ApmHealthService.
     *
     * @param metricsClient the APM metrics client
     */
    public ApmHealthService(final ApmMetricsClient metricsClient) {
        this.metricsClient = Objects.requireNonNull(metricsClient, "metricsClient must not be null");
    }

    /**
     * Returns the health of a service in a window compared with the previous window.
     *
     * @param service the service name, must not be blank
     * @param env the environment, must not be blank
     * @param window the current window
     * @param operationOverride the operation name to use; null or blank to detect it
     *
     * @return the service health
     *
     * @throws IllegalArgumentException if service or env is blank
     * @throws IllegalStateException if no operation is given and none can be detected
     */
    public ServiceHealth serviceHealth(
            final String service,
            final String env,
            final TimeWindow window,
            final String operationOverride
    ) {
        requireNotBlank(service, "service");
        requireNotBlank(env, "env");
        Objects.requireNonNull(window, "window must not be null");

        final ApmOperation operation = resolveOperation(service, env, window, operationOverride);
        final ApmMetrics current = metricsClient.queryServiceMetrics(service, env, operation.name(), window);
        final TimeWindow baselineWindow = window.previous();
        final ApmMetrics baseline = metricsClient.queryServiceMetrics(service, env, operation.name(), baselineWindow);

        return new ServiceHealth(service, env, operation, window, current, baseline);
    }

    /**
     * Returns a service's resources ranked by the given criteria.
     *
     * @param service the service name, must not be blank
     * @param env the environment, must not be blank
     * @param window the window
     * @param operationOverride the operation name to use; null or blank to detect it
     * @param sortBy the ranking criteria
     * @param limit the maximum number of resources, between 1 and {@link ResourceSortCriteria#MAX_LIMIT}
     *
     * @return the ranked resources
     *
     * @throws IllegalArgumentException if service or env is blank, or limit is out of range
     * @throws IllegalStateException if no operation is given and none can be detected
     */
    public TopResources topResources(
            final String service,
            final String env,
            final TimeWindow window,
            final String operationOverride,
            final ResourceSortCriteria sortBy,
            final int limit
    ) {
        requireNotBlank(service, "service");
        requireNotBlank(env, "env");
        Objects.requireNonNull(window, "window must not be null");
        Objects.requireNonNull(sortBy, "sortBy must not be null");
        ResourceSortCriteria.requireValidLimit(limit);

        final ApmOperation operation = resolveOperation(service, env, window, operationOverride);
        final List<ResourceStats> stats = metricsClient.queryResourceMetrics(service, env, operation.name(), window);

        return new TopResources(service, env, operation, window, sortBy, sortBy.rank(stats, limit));
    }

    private ApmOperation resolveOperation(
            final String service,
            final String env,
            final TimeWindow window,
            final String operationOverride
    ) {
        if (operationOverride != null && !operationOverride.isBlank()) {
            return ApmOperation.provided(operationOverride.trim());
        }
        return metricsClient.detectEntryOperation(service, env, window)
                .map(ApmOperation::detected)
                .orElseThrow(() -> new IllegalStateException(
                        "No entry spans found for service " + service + " in env " + env
                                + "; pass 'operation' explicitly"));
    }

    private static void requireNotBlank(final String value, final String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -Dtest=ApmHealthServiceTest`
Expected: `Tests run: 8, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit** (ask for approval of the message first)

```bash
git add src/main/java/co/fanki/datadog/traceinspector/application/ApmHealthService.java \
        src/test/java/co/fanki/datadog/traceinspector/datadog/FakeApmMetricsClient.java \
        src/test/java/co/fanki/datadog/traceinspector/application/ApmHealthServiceTest.java
git commit -m "Add APM health service to resolve operations and fetch metrics"
```

---

### Task 6: apm.service_health tool

Implements the `apm.service_health` contract from the spec.

**Files:**
- Create: `src/main/java/co/fanki/datadog/traceinspector/mcp/ApmServiceHealthTool.java`
- Test: `src/test/java/co/fanki/datadog/traceinspector/mcp/ApmServiceHealthToolTest.java`

**Interfaces:**
- Consumes: `ApmHealthService#serviceHealth(String, String, TimeWindow, String)` (Task 5), `ServiceHealth` and its methods (Task 2), `MetricComparison` (Task 1), `DatadogConfig#defaultEnv()`, `McpTool`, `McpToolException(String toolName, String message, Throwable cause)`, `FakeApmMetricsClient` (Task 5).
- Produces: `ApmServiceHealthTool(ApmHealthService, DatadogConfig)`, tool name `apm.service_health`.

Output rounding: `hits` and `errors` are whole numbers (`long`). Everything else is rounded to 2 decimals. `null` stays `null`.

- [ ] **Step 1: Write the failing test**

`src/test/java/co/fanki/datadog/traceinspector/mcp/ApmServiceHealthToolTest.java`:

```java
package co.fanki.datadog.traceinspector.mcp;

import co.fanki.datadog.traceinspector.application.ApmHealthService;
import co.fanki.datadog.traceinspector.config.DatadogConfig;
import co.fanki.datadog.traceinspector.datadog.FakeApmMetricsClient;
import co.fanki.datadog.traceinspector.domain.ApmMetrics;
import co.fanki.datadog.traceinspector.domain.TimeWindow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for ApmServiceHealthTool.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class ApmServiceHealthToolTest {

    private static final String FROM = "2026-09-23T13:00:00Z";
    private static final String TO = "2026-09-23T14:00:00Z";
    private static final TimeWindow WINDOW = new TimeWindow(Instant.parse(FROM), Instant.parse(TO));

    private FakeApmMetricsClient client;
    private ApmServiceHealthTool tool;

    @BeforeEach
    void setUp() {
        client = new FakeApmMetricsClient();
        final DatadogConfig config = new DatadogConfig("api-key", "app-key", "datadoghq.com", "prod");
        tool = new ApmServiceHealthTool(new ApmHealthService(client), config);
    }

    private static Map<String, Object> args() {
        final Map<String, Object> args = new HashMap<>();
        args.put("service", "payments");
        args.put("from", FROM);
        args.put("to", TO);
        return args;
    }

    @Test
    void whenGettingName_shouldReturnApmServiceHealth() {
        assertEquals("apm.service_health", tool.name());
    }

    @Test
    @SuppressWarnings("unchecked")
    void whenGettingSchema_shouldRequireServiceFromAndTo() {
        assertEquals(List.of("service", "from", "to"), tool.inputSchema().get("required"));
        final Map<String, Object> properties = (Map<String, Object>) tool.inputSchema().get("properties");
        assertTrue(properties.containsKey("operation"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void whenExecuting_givenErrorSpike_shouldReturnDegradedComparison() {
        client.detectedOperation("servlet.request");
        client.metricsFor(WINDOW, new ApmMetrics(1000, 20, 45.0, 310.456, 900.0));
        client.metricsFor(WINDOW.previous(), new ApmMetrics(1000, 10, 42.0, 290.0, 850.0));

        final Map<String, Object> result = tool.execute(args());

        assertEquals(true, result.get("success"));
        assertEquals("prod", result.get("env"));
        assertEquals("servlet.request", result.get("operation"));
        assertEquals("detected", result.get("operationSource"));
        assertEquals(true, result.get("degraded"));
        assertEquals(List.of("error rate 1.00% -> 2.00%"), result.get("signals"));

        final Map<String, Object> window = (Map<String, Object>) result.get("baseline");
        assertEquals("2026-09-23T12:00:00Z", window.get("from"));

        final Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");
        final Map<String, Object> hits = (Map<String, Object>) metrics.get("hits");
        assertEquals(1000L, hits.get("current"));
        final Map<String, Object> p95 = (Map<String, Object>) metrics.get("latencyP95");
        assertEquals(310.46, p95.get("current"));
        final Map<String, Object> errorRate = (Map<String, Object>) metrics.get("errorRate");
        assertEquals(100.0, errorRate.get("deltaPct"));
    }

    @Test
    void whenExecuting_givenOperationArgument_shouldReportProvidedSource() {
        final Map<String, Object> args = args();
        args.put("operation", "next.request");

        final Map<String, Object> result = tool.execute(args);

        assertEquals("next.request", result.get("operation"));
        assertEquals("provided", result.get("operationSource"));
    }

    @Test
    void whenExecuting_givenMissingService_shouldThrowInvalidArguments() {
        final Map<String, Object> args = args();
        args.remove("service");

        final McpToolException e = assertThrows(McpToolException.class, () -> tool.execute(args));

        assertTrue(e.getMessage().contains("Invalid arguments"));
    }

    @Test
    void whenExecuting_givenFromAfterTo_shouldThrowInvalidArguments() {
        final Map<String, Object> args = args();
        args.put("from", TO);
        args.put("to", FROM);

        final McpToolException e = assertThrows(McpToolException.class, () -> tool.execute(args));

        assertTrue(e.getMessage().contains("Invalid arguments"));
    }

    @Test
    void whenExecuting_givenNoDetectableOperation_shouldThrowWithHint() {
        final McpToolException e = assertThrows(McpToolException.class, () -> tool.execute(args()));

        assertTrue(e.getMessage().contains("No entry spans found for service payments"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ApmServiceHealthToolTest`
Expected: compilation failure (`cannot find symbol: class ApmServiceHealthTool`).

- [ ] **Step 3: Implement**

`src/main/java/co/fanki/datadog/traceinspector/mcp/ApmServiceHealthTool.java`:

```java
package co.fanki.datadog.traceinspector.mcp;

import co.fanki.datadog.traceinspector.application.ApmHealthService;
import co.fanki.datadog.traceinspector.config.DatadogConfig;
import co.fanki.datadog.traceinspector.domain.MetricComparison;
import co.fanki.datadog.traceinspector.domain.ServiceHealth;
import co.fanki.datadog.traceinspector.domain.TimeWindow;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MCP tool that reports a service's APM health compared with the previous window.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public final class ApmServiceHealthTool implements McpTool {

    private static final String TOOL_NAME = "apm.service_health";
    private static final String TOOL_DESCRIPTION =
            "Get APM health (hits, errors, error rate, p50/p95/p99 latency) for a service from trace metrics "
                    + "and compare it with the previous window of the same length. Flags the service as degraded "
                    + "when error rate, p95 latency or traffic change past fixed thresholds";

    private final ApmHealthService healthService;
    private final DatadogConfig config;

    /**
     * Creates a new ApmServiceHealthTool.
     *
     * @param healthService the APM health service
     * @param config the Datadog configuration for defaults
     */
    public ApmServiceHealthTool(final ApmHealthService healthService, final DatadogConfig config) {
        this.healthService = Objects.requireNonNull(healthService, "healthService must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return TOOL_DESCRIPTION;
    }

    @Override
    public Map<String, Object> inputSchema() {
        final Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        final Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("service", Map.of("type", "string", "description", "Service name in Datadog"));
        properties.put("env", Map.of("type", "string", "default", config.defaultEnv(), "description", "Environment"));
        properties.put("from", Map.of("type", "string", "description", "ISO-8601 start timestamp"));
        properties.put("to", Map.of("type", "string", "description", "ISO-8601 end timestamp"));
        properties.put("operation", Map.of(
                "type", "string",
                "description", "APM operation name (e.g. servlet.request, next.request). Detected when omitted"
        ));

        schema.put("properties", properties);
        schema.put("required", List.of("service", "from", "to"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(final Map<String, Object> arguments) {
        Objects.requireNonNull(arguments, "arguments must not be null");

        try {
            final String service = getRequiredString(arguments, "service");
            final String env = getOptionalString(arguments, "env", config.defaultEnv());
            final Instant from = parseTimestamp(getRequiredString(arguments, "from"));
            final Instant to = parseTimestamp(getRequiredString(arguments, "to"));
            final String operation = getOptionalString(arguments, "operation", null);

            final ServiceHealth health = healthService.serviceHealth(service, env, new TimeWindow(from, to), operation);

            return buildSuccessResponse(health);
        } catch (final IllegalArgumentException e) {
            throw new McpToolException(TOOL_NAME, "Invalid arguments: " + e.getMessage(), e);
        } catch (final Exception e) {
            throw new McpToolException(TOOL_NAME, "Failed to get service health: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildSuccessResponse(final ServiceHealth health) {
        final Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("hits", comparison(health.hits(), true));
        metrics.put("errors", comparison(health.errors(), true));
        metrics.put("errorRate", comparison(health.errorRate(), false));
        metrics.put("latencyP50", comparison(health.latencyP50(), false));
        metrics.put("latencyP95", comparison(health.latencyP95(), false));
        metrics.put("latencyP99", comparison(health.latencyP99(), false));

        final Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("service", health.service());
        response.put("env", health.env());
        response.put("operation", health.operation().name());
        response.put("operationSource", health.operation().sourceLabel());
        response.put("window", windowMap(health.window()));
        response.put("baseline", windowMap(health.baselineWindow()));
        response.put("metrics", metrics);
        response.put("degraded", health.isDegraded());
        response.put("signals", health.signals());
        response.put("notes", health.notes());
        return response;
    }

    private static Map<String, Object> windowMap(final TimeWindow window) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("from", window.from().toString());
        map.put("to", window.to().toString());
        return map;
    }

    private static Map<String, Object> comparison(final MetricComparison comparison, final boolean wholeNumber) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("current", format(comparison.current(), wholeNumber));
        map.put("baseline", format(comparison.baseline(), wholeNumber));
        map.put("deltaPct", round(comparison.deltaPct()));
        return map;
    }

    private static Object format(final Double value, final boolean wholeNumber) {
        if (value == null) {
            return null;
        }
        if (wholeNumber) {
            return Math.round(value);
        }
        return round(value);
    }

    private static Double round(final Double value) {
        if (value == null) {
            return null;
        }
        return Math.round(value * 100.0) / 100.0;
    }

    private String getRequiredString(final Map<String, Object> args, final String key) {
        final Object value = args.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        return value.toString();
    }

    private String getOptionalString(final Map<String, Object> args, final String key, final String defaultValue) {
        final Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    private Instant parseTimestamp(final String timestamp) {
        try {
            return Instant.parse(timestamp);
        } catch (final Exception e) {
            throw new IllegalArgumentException("Invalid timestamp format. Expected ISO-8601: " + timestamp);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ApmServiceHealthToolTest`
Expected: `Tests run: 7, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit** (ask for approval of the message first)

```bash
git add src/main/java/co/fanki/datadog/traceinspector/mcp/ApmServiceHealthTool.java \
        src/test/java/co/fanki/datadog/traceinspector/mcp/ApmServiceHealthToolTest.java
git commit -m "Add apm.service_health tool"
```

---

### Task 7: apm.top_resources tool

Implements the `apm.top_resources` contract from the spec.

**Files:**
- Create: `src/main/java/co/fanki/datadog/traceinspector/mcp/ApmTopResourcesTool.java`
- Test: `src/test/java/co/fanki/datadog/traceinspector/mcp/ApmTopResourcesToolTest.java`

**Interfaces:**
- Consumes: `ApmHealthService#topResources(String, String, TimeWindow, String, ResourceSortCriteria, int)` (Task 5), `TopResources`, `ResourceStats`, `ResourceSortCriteria#fromKey`, `DEFAULT_LIMIT`, `MAX_LIMIT` (Task 3), `ApmMetrics` (Task 1), `FakeApmMetricsClient` (Task 5).
- Produces: `ApmTopResourcesTool(ApmHealthService, DatadogConfig)`, tool name `apm.top_resources`.

- [ ] **Step 1: Write the failing test**

`src/test/java/co/fanki/datadog/traceinspector/mcp/ApmTopResourcesToolTest.java`:

```java
package co.fanki.datadog.traceinspector.mcp;

import co.fanki.datadog.traceinspector.application.ApmHealthService;
import co.fanki.datadog.traceinspector.config.DatadogConfig;
import co.fanki.datadog.traceinspector.datadog.FakeApmMetricsClient;
import co.fanki.datadog.traceinspector.domain.ApmMetrics;
import co.fanki.datadog.traceinspector.domain.ResourceStats;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for ApmTopResourcesTool.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class ApmTopResourcesToolTest {

    private FakeApmMetricsClient client;
    private ApmTopResourcesTool tool;

    @BeforeEach
    void setUp() {
        client = new FakeApmMetricsClient();
        client.detectedOperation("servlet.request");
        client.resources(List.of(
                new ResourceStats("GET /a", new ApmMetrics(100, 1, 10.0, 20.0, 30.0)),
                new ResourceStats("POST /b", new ApmMetrics(4200, 310, 120.0, 850.0, 2100.0)),
                new ResourceStats("GET /c", new ApmMetrics(500, 3, null, null, null))));
        final DatadogConfig config = new DatadogConfig("api-key", "app-key", "datadoghq.com", "prod");
        tool = new ApmTopResourcesTool(new ApmHealthService(client), config);
    }

    private static Map<String, Object> args() {
        final Map<String, Object> args = new HashMap<>();
        args.put("service", "payments");
        args.put("from", "2026-09-23T13:00:00Z");
        args.put("to", "2026-09-23T14:00:00Z");
        return args;
    }

    @Test
    void whenGettingName_shouldReturnApmTopResources() {
        assertEquals("apm.top_resources", tool.name());
    }

    @Test
    @SuppressWarnings("unchecked")
    void whenExecuting_givenDefaults_shouldRankByErrors() {
        final Map<String, Object> result = tool.execute(args());

        assertEquals(true, result.get("success"));
        assertEquals("errors", result.get("sortBy"));
        assertEquals(3, result.get("count"));

        final List<Map<String, Object>> resources = (List<Map<String, Object>>) result.get("resources");
        assertEquals("POST /b", resources.get(0).get("resource"));
        assertEquals(310L, resources.get(0).get("errors"));
        assertEquals(7.38, resources.get(0).get("errorRate"));
        assertEquals(850.0, resources.get(0).get("latencyP95"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void whenExecuting_givenSortByLatencyAndLimit_shouldApplyBoth() {
        final Map<String, Object> args = args();
        args.put("sortBy", "latencyP95");
        args.put("limit", 2);

        final Map<String, Object> result = tool.execute(args);

        final List<Map<String, Object>> resources = (List<Map<String, Object>>) result.get("resources");
        assertEquals(2, resources.size());
        assertEquals("POST /b", resources.get(0).get("resource"));
        assertEquals("GET /a", resources.get(1).get("resource"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void whenExecuting_givenResourceWithoutLatency_shouldReturnNullLatency() {
        final Map<String, Object> args = args();
        args.put("sortBy", "hits");

        final Map<String, Object> result = tool.execute(args);

        final List<Map<String, Object>> resources = (List<Map<String, Object>>) result.get("resources");
        assertEquals("GET /c", resources.get(1).get("resource"));
        assertNull(resources.get(1).get("latencyP95"));
    }

    @Test
    void whenExecuting_givenUnknownSortBy_shouldThrowInvalidArguments() {
        final Map<String, Object> args = args();
        args.put("sortBy", "bogus");

        final McpToolException e = assertThrows(McpToolException.class, () -> tool.execute(args));

        assertTrue(e.getMessage().contains("Invalid arguments"));
    }

    @Test
    void whenExecuting_givenLimitAboveMax_shouldThrowInvalidArguments() {
        final Map<String, Object> args = args();
        args.put("limit", 51);

        final McpToolException e = assertThrows(McpToolException.class, () -> tool.execute(args));

        assertTrue(e.getMessage().contains("Invalid arguments"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ApmTopResourcesToolTest`
Expected: compilation failure (`cannot find symbol: class ApmTopResourcesTool`).

- [ ] **Step 3: Implement**

`src/main/java/co/fanki/datadog/traceinspector/mcp/ApmTopResourcesTool.java`:

```java
package co.fanki.datadog.traceinspector.mcp;

import co.fanki.datadog.traceinspector.application.ApmHealthService;
import co.fanki.datadog.traceinspector.config.DatadogConfig;
import co.fanki.datadog.traceinspector.domain.ApmMetrics;
import co.fanki.datadog.traceinspector.domain.ResourceSortCriteria;
import co.fanki.datadog.traceinspector.domain.ResourceStats;
import co.fanki.datadog.traceinspector.domain.TimeWindow;
import co.fanki.datadog.traceinspector.domain.TopResources;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MCP tool that ranks a service's resources (endpoints) by errors, error rate, latency or traffic.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public final class ApmTopResourcesTool implements McpTool {

    private static final String TOOL_NAME = "apm.top_resources";
    private static final String TOOL_DESCRIPTION =
            "Rank a service's resources (endpoints) by errors, error rate, p95 latency or hits "
                    + "using APM trace metrics for a time window";

    private final ApmHealthService healthService;
    private final DatadogConfig config;

    /**
     * Creates a new ApmTopResourcesTool.
     *
     * @param healthService the APM health service
     * @param config the Datadog configuration for defaults
     */
    public ApmTopResourcesTool(final ApmHealthService healthService, final DatadogConfig config) {
        this.healthService = Objects.requireNonNull(healthService, "healthService must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return TOOL_DESCRIPTION;
    }

    @Override
    public Map<String, Object> inputSchema() {
        final Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        final Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("service", Map.of("type", "string", "description", "Service name in Datadog"));
        properties.put("env", Map.of("type", "string", "default", config.defaultEnv(), "description", "Environment"));
        properties.put("from", Map.of("type", "string", "description", "ISO-8601 start timestamp"));
        properties.put("to", Map.of("type", "string", "description", "ISO-8601 end timestamp"));
        properties.put("operation", Map.of(
                "type", "string",
                "description", "APM operation name (e.g. servlet.request, next.request). Detected when omitted"
        ));
        properties.put("sortBy", Map.of(
                "type", "string",
                "enum", List.of("errors", "errorRate", "latencyP95", "hits"),
                "default", "errors",
                "description", "Ranking criteria, descending"
        ));
        properties.put("limit", Map.of(
                "type", "number",
                "default", ResourceSortCriteria.DEFAULT_LIMIT,
                "description", "Max resources to return (1-" + ResourceSortCriteria.MAX_LIMIT + ")"
        ));

        schema.put("properties", properties);
        schema.put("required", List.of("service", "from", "to"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(final Map<String, Object> arguments) {
        Objects.requireNonNull(arguments, "arguments must not be null");

        try {
            final String service = getRequiredString(arguments, "service");
            final String env = getOptionalString(arguments, "env", config.defaultEnv());
            final Instant from = parseTimestamp(getRequiredString(arguments, "from"));
            final Instant to = parseTimestamp(getRequiredString(arguments, "to"));
            final String operation = getOptionalString(arguments, "operation", null);
            final ResourceSortCriteria sortBy = ResourceSortCriteria.fromKey(
                    getOptionalString(arguments, "sortBy", ResourceSortCriteria.ERRORS.key()));
            final int limit = getOptionalInt(arguments, "limit", ResourceSortCriteria.DEFAULT_LIMIT);

            final TopResources top = healthService.topResources(
                    service, env, new TimeWindow(from, to), operation, sortBy, limit);

            return buildSuccessResponse(top);
        } catch (final IllegalArgumentException e) {
            throw new McpToolException(TOOL_NAME, "Invalid arguments: " + e.getMessage(), e);
        } catch (final Exception e) {
            throw new McpToolException(TOOL_NAME, "Failed to get top resources: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildSuccessResponse(final TopResources top) {
        final List<Map<String, Object>> resources = new ArrayList<>();
        for (final ResourceStats stats : top.resources()) {
            final ApmMetrics metrics = stats.metrics();
            final Map<String, Object> map = new LinkedHashMap<>();
            map.put("resource", stats.resource());
            map.put("hits", metrics.hits());
            map.put("errors", metrics.errors());
            map.put("errorRate", round(metrics.errorRate()));
            map.put("latencyP50", round(metrics.latencyP50()));
            map.put("latencyP95", round(metrics.latencyP95()));
            map.put("latencyP99", round(metrics.latencyP99()));
            resources.add(map);
        }

        final Map<String, Object> window = new LinkedHashMap<>();
        window.put("from", top.window().from().toString());
        window.put("to", top.window().to().toString());

        final Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("service", top.service());
        response.put("env", top.env());
        response.put("operation", top.operation().name());
        response.put("operationSource", top.operation().sourceLabel());
        response.put("window", window);
        response.put("sortBy", top.sortBy().key());
        response.put("count", resources.size());
        response.put("resources", resources);
        response.put("notes", top.notes());
        return response;
    }

    private static Double round(final Double value) {
        if (value == null) {
            return null;
        }
        return Math.round(value * 100.0) / 100.0;
    }

    private String getRequiredString(final Map<String, Object> args, final String key) {
        final Object value = args.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        return value.toString();
    }

    private String getOptionalString(final Map<String, Object> args, final String key, final String defaultValue) {
        final Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    private int getOptionalInt(final Map<String, Object> args, final String key, final int defaultValue) {
        final Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private Instant parseTimestamp(final String timestamp) {
        try {
            return Instant.parse(timestamp);
        } catch (final Exception e) {
            throw new IllegalArgumentException("Invalid timestamp format. Expected ISO-8601: " + timestamp);
        }
    }
}
```

Note: `Integer.parseInt` throws `NumberFormatException`, which extends `IllegalArgumentException`, so a non-numeric limit is reported as "Invalid arguments".

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ApmTopResourcesToolTest`
Expected: `Tests run: 6, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit** (ask for approval of the message first)

```bash
git add src/main/java/co/fanki/datadog/traceinspector/mcp/ApmTopResourcesTool.java \
        src/test/java/co/fanki/datadog/traceinspector/mcp/ApmTopResourcesToolTest.java
git commit -m "Add apm.top_resources tool"
```

---

### Task 8: Wiring, docs and live verification

**Files:**
- Modify: `src/main/java/co/fanki/datadog/traceinspector/DatadogMcpServer.java` (the `main` method, around lines 108-125)
- Modify: `CLAUDE.md` (Package Structure and Use Cases table)
- Modify: `README.md` (tools table near line 52, and a new tool section after the `log.correlate` section)
- Modify: `.claude/docs/use-cases/apm-service-health.md` (status line and open questions 1 and 2)

**Interfaces:**
- Consumes: `ApmMetricsClientImpl(DatadogConfig)` (Task 4), `ApmHealthService(ApmMetricsClient)` (Task 5), `ApmServiceHealthTool`, `ApmTopResourcesTool` (Tasks 6 and 7).
- Produces: a runnable server that exposes both tools.

- [ ] **Step 1: Wire the dependencies**

In `DatadogMcpServer.main()`, after `final FilterConfigStore filterConfigStore = new FilterConfigStore();` add:

```java
            final ApmMetricsClient apmMetricsClient = new ApmMetricsClientImpl(config);
            final ApmHealthService apmHealthService = new ApmHealthService(apmMetricsClient);
```

and extend the tool list:

```java
            final List<McpTool> tools = List.of(
                    new TraceListErrorTracesTool(diagnosticService, config),
                    new TraceInspectErrorTraceTool(diagnosticService, config),
                    new LogSearchTool(datadogClient, config, filterConfigStore),
                    new LogCorrelateTool(datadogClient, config, filterConfigStore),
                    new TraceExtractScenarioTool(datadogClient, scenarioExtractor, config),
                    new FilterConfigureTool(filterConfigStore),
                    new ApmServiceHealthTool(apmHealthService, config),
                    new ApmTopResourcesTool(apmHealthService, config)
            );
```

Add the imports:

```java
import co.fanki.datadog.traceinspector.application.ApmHealthService;
import co.fanki.datadog.traceinspector.datadog.ApmMetricsClient;
import co.fanki.datadog.traceinspector.datadog.ApmMetricsClientImpl;
import co.fanki.datadog.traceinspector.mcp.ApmServiceHealthTool;
import co.fanki.datadog.traceinspector.mcp.ApmTopResourcesTool;
```

- [ ] **Step 2: Build and run the full suite**

Run: `mvn clean package`
Expected: `BUILD SUCCESS`, and all tests pass, both existing and new.

- [ ] **Step 3: Verify the tools are listed**

Run:

```bash
printf '%s\n' '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' \
  | DATADOG_API_KEY=x DATADOG_APP_KEY=y java --enable-preview -jar target/datadog-mcp-server-1.5.0.jar \
  | grep -o '"apm\.[a-z_]*"' | sort -u
```

Expected output:

```
"apm.service_health"
"apm.top_resources"
```

- [ ] **Step 4: Live verification against Datadog (resolves spec open questions 1 and 2)**

This needs real keys. Ask the user to run it, or run it with their approval. Use the `env` block for `waabox-datadog-mcp` in `~/.claude.json` as the source of the keys. Never print the keys.

Pick one Java service and one Next.js portal the user names, and a busy one-hour window. For each, send:

```bash
printf '%s\n' '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"apm.service_health","arguments":{"service":"<service>","from":"<from>","to":"<to>"}}}' \
  | java --enable-preview -jar target/datadog-mcp-server-1.5.0.jar
```

and the same call with `"name":"apm.top_resources"`.

Check each of these and record the answer:
1. `operationSource` is `detected` and `operation` is the expected value (`servlet.request` for Java). For Next.js, write down whether `next.request` or `web.request` was picked (open question 2).
2. `hits` and `errors` roughly match the service page in the Datadog APM UI for the same window.
3. `latencyP95` is not null and roughly matches the UI (open question 1). If it is null or clearly wrong, stop and report to the user before changing anything: the percentile query shape needs a decision.
4. `top_resources` returns resource names without a `resource_name:` prefix.

- [ ] **Step 5: Update docs**

`CLAUDE.md`, Package Structure. Add these lines in their packages:

```
│   ├── ApmMetricsClient      # Interface for APM trace metrics (Metrics API v2)
│   ├── ApmMetricsClientImpl  # Scalar queries + entry operation detection
```
```
│   ├── TimeWindow, ApmOperation, ApmMetrics, MetricComparison
│   ├── ServiceHealth, ResourceStats, ResourceSortCriteria, TopResources
```
```
│   ├── ApmHealthService            # Resolves operation, fetches APM metrics
```
```
    ├── ApmServiceHealthTool        # apm.service_health
    ├── ApmTopResourcesTool         # apm.top_resources
```

`CLAUDE.md`, Use Cases table: change status from `Design approved` to `Implemented`.

`README.md`: add two rows to the tools table:

```
| `apm.service_health` | APM health of a service (hits, errors, error rate, p50/p95/p99) compared with the previous window, with a degraded verdict |
| `apm.top_resources` | Rank a service's endpoints by errors, error rate, p95 latency or hits |
```

Add a section after the `log.correlate` section. Use the same heading style as the existing ones, and the next free number. Include the input table and a JSON output example, both copied from the spec's API Contracts section. Add one sentence saying that the API key needs the `timeseries_query` permission.

`.claude/docs/use-cases/apm-service-health.md`: change `Status:` to `Implemented (<date>)`. Move open questions 1 and 2 to "Resolved Decisions", with the answers found in Step 4.

- [ ] **Step 6: Commit** (ask for approval of the message first)

```bash
git add src/main/java/co/fanki/datadog/traceinspector/DatadogMcpServer.java CLAUDE.md README.md \
        .claude/docs/use-cases/apm-service-health.md
git commit -m "Wire APM health tools into the server and document them"
```

---

## Spec Coverage

| Spec item | Task |
|-----------|------|
| BR-1 trace metrics only | 4 |
| BR-2, BR-3, BR-4 operation resolution | 1, 4, 5 |
| BR-5, BR-6 windows | 1, 5 |
| BR-7, BR-8, BR-10 queries, ms conversion | 4 |
| BR-9 error rate, BR-11 delta | 1 |
| BR-11a minimum traffic | 2 |
| BR-12, BR-13, BR-14 degraded rules | 2 |
| BR-15 grouped query | 4 |
| BR-16 to BR-20 ranking | 3, 5, 7 |
| BR-21 empty data notes | 2, 3, 4 |
| Error table | 5, 6, 7 |
| Open questions 1, 2 | 8 (live verification) |
| Package structure / docs | 8 |
