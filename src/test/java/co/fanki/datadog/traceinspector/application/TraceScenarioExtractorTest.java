package co.fanki.datadog.traceinspector.application;

import co.fanki.datadog.traceinspector.domain.EntryPoint;
import co.fanki.datadog.traceinspector.domain.ErrorContext;
import co.fanki.datadog.traceinspector.domain.ExecutionStep;
import co.fanki.datadog.traceinspector.domain.ServiceErrorView;
import co.fanki.datadog.traceinspector.domain.SpanDetail;
import co.fanki.datadog.traceinspector.domain.TraceDetail;
import co.fanki.datadog.traceinspector.domain.TraceScenario;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for TraceScenarioExtractor.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class TraceScenarioExtractorTest {

    private TraceScenarioExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new TraceScenarioExtractor();
    }

    @Test
    void whenExtracting_givenTraceWithHttpEntryPoint_shouldExtractEntryPoint() {
        final Instant now = Instant.now();
        final SpanDetail rootSpan = new SpanDetail(
                "span1", null, "api-gateway", "http.request",
                "POST /api/orders", now, 100_000_000L, false,
                "", "", "",
                Map.of(
                        "http.method", "POST",
                        "http.url", "/api/orders",
                        "http.status_code", "200"
                )
        );

        final TraceDetail traceDetail = new TraceDetail(
                "trace123", "api-gateway", "prod", "POST /api/orders",
                now, 100_000_000L, List.of(rootSpan)
        );

        final TraceScenario scenario = extractor.extract(traceDetail, List.of());

        assertTrue(scenario.hasEntryPoint());
        assertEquals("POST", scenario.entryPoint().method());
        assertEquals("/api/orders", scenario.entryPoint().path());
    }

    @Test
    void whenExtracting_givenTraceWithMultipleSpans_shouldBuildExecutionFlow() {
        final Instant now = Instant.now();

        final SpanDetail span1 = new SpanDetail(
                "span1", null, "api-gateway", "http.request",
                "POST /api/orders", now, 500_000_000L, false,
                "", "", "",
                Map.of("http.method", "POST", "http.url", "/api/orders")
        );

        final SpanDetail span2 = new SpanDetail(
                "span2", "span1", "order-service", "OrderController.create",
                "OrderController.create", now.plusMillis(10), 400_000_000L, false,
                "", "", "", Map.of()
        );

        final SpanDetail span3 = new SpanDetail(
                "span3", "span2", "order-service", "db.query",
                "SELECT * FROM products", now.plusMillis(20), 50_000_000L, false,
                "", "", "",
                Map.of("db.statement", "SELECT * FROM products WHERE id = ?")
        );

        final TraceDetail traceDetail = new TraceDetail(
                "trace123", "api-gateway", "prod", "POST /api/orders",
                now, 500_000_000L, List.of(span1, span2, span3)
        );

        final TraceScenario scenario = extractor.extract(traceDetail, List.of());

        assertEquals(3, scenario.stepCount());

        final List<ExecutionStep> steps = scenario.executionFlow();
        assertEquals(1, steps.get(0).order());
        assertEquals("api-gateway", steps.get(0).service());
        assertEquals(ExecutionStep.TYPE_HTTP, steps.get(0).type());

        assertEquals(2, steps.get(1).order());
        assertEquals("order-service", steps.get(1).service());

        assertEquals(3, steps.get(2).order());
        assertEquals(ExecutionStep.TYPE_DB, steps.get(2).type());
    }

    @Test
    void whenExtracting_givenTraceWithError_shouldExtractErrorContext() {
        final Instant now = Instant.now();
        final String stackTrace = """
                java.lang.RuntimeException: Not enough stock
                    at com.example.order.OrderService.validateStock(OrderService.java:142)
                    at com.example.order.OrderService.processOrder(OrderService.java:85)
                """;

        final SpanDetail errorSpan = new SpanDetail(
                "span1", null, "order-service", "OrderService.validateStock",
                "OrderService.validateStock", now, 10_000_000L, true,
                "Not enough stock: requested 5, available 2",
                "InsufficientStockException",
                stackTrace,
                Map.of("product_id", "123", "quantity", "5")
        );

        final TraceDetail traceDetail = new TraceDetail(
                "trace123", "order-service", "prod", "OrderService.validateStock",
                now, 10_000_000L, List.of(errorSpan)
        );

        final TraceScenario scenario = extractor.extract(traceDetail, List.of());

        assertTrue(scenario.hasError());

        final ErrorContext error = scenario.errorContext();
        assertEquals("order-service", error.service());
        assertEquals("InsufficientStockException", error.exceptionType());
        assertEquals("Not enough stock: requested 5, available 2", error.message());

        assertTrue(error.hasLocation());
        assertEquals("OrderService.java", error.location().fileName());
        assertEquals(142, error.location().lineNumber());
        assertEquals("validateStock", error.location().methodName());
    }

    @Test
    void whenExtracting_givenTraceWithRelevantData_shouldExtractRelevantData() {
        final Instant now = Instant.now();
        final SpanDetail span = new SpanDetail(
                "span1", null, "order-service", "processOrder",
                "processOrder", now, 100_000_000L, false,
                "", "", "",
                Map.of(
                        "user_id", "456",
                        "order_id", "789",
                        "product_id", "123",
                        "quantity", "5"
                )
        );

        final TraceDetail traceDetail = new TraceDetail(
                "trace123", "order-service", "prod", "processOrder",
                now, 100_000_000L, List.of(span)
        );

        final TraceScenario scenario = extractor.extract(traceDetail, List.of());

        final Map<String, String> data = scenario.relevantData();
        assertTrue(data.containsKey("user_id"));
        assertTrue(data.containsKey("order_id"));
        assertTrue(data.containsKey("product_id"));
        assertEquals("456", data.get("user_id"));
    }

    @Test
    void whenExtracting_givenTraceWithMultipleServices_shouldExtractInvolvedServices() {
        final Instant now = Instant.now();

        final SpanDetail span1 = new SpanDetail(
                "span1", null, "api-gateway", "request",
                "request", now, 100_000_000L, false,
                "", "", "", Map.of()
        );

        final SpanDetail span2 = new SpanDetail(
                "span2", "span1", "order-service", "process",
                "process", now.plusMillis(10), 80_000_000L, false,
                "", "", "", Map.of()
        );

        final SpanDetail span3 = new SpanDetail(
                "span3", "span2", "payment-service", "charge",
                "charge", now.plusMillis(20), 50_000_000L, false,
                "", "", "", Map.of()
        );

        final TraceDetail traceDetail = new TraceDetail(
                "trace123", "api-gateway", "prod", "request",
                now, 100_000_000L, List.of(span1, span2, span3)
        );

        final TraceScenario scenario = extractor.extract(traceDetail, List.of());

        assertEquals(3, scenario.involvedServices().size());
        assertTrue(scenario.involvedServices().contains("api-gateway"));
        assertTrue(scenario.involvedServices().contains("order-service"));
        assertTrue(scenario.involvedServices().contains("payment-service"));
    }

    @Test
    void whenExtracting_givenEmptyTrace_shouldReturnEmptyScenario() {
        final TraceDetail traceDetail = new TraceDetail(
                "trace123", "service", "prod", "",
                Instant.now(), 0L, List.of()
        );

        final TraceScenario scenario = extractor.extract(traceDetail, List.of());

        assertNotNull(scenario);
        assertEquals("trace123", scenario.traceId());
        assertFalse(scenario.hasEntryPoint());
        assertFalse(scenario.hasError());
        assertEquals(0, scenario.stepCount());
    }

    @Test
    void whenExtracting_givenTraceWithDbSpan_shouldClassifyAsDb() {
        final Instant now = Instant.now();
        final SpanDetail dbSpan = new SpanDetail(
                "span1", null, "order-service", "db.query",
                "SELECT * FROM orders", now, 50_000_000L, false,
                "", "", "",
                Map.of("db.statement", "SELECT * FROM orders WHERE id = ?")
        );

        final TraceDetail traceDetail = new TraceDetail(
                "trace123", "order-service", "prod", "db.query",
                now, 50_000_000L, List.of(dbSpan)
        );

        final TraceScenario scenario = extractor.extract(traceDetail, List.of());

        assertEquals(ExecutionStep.TYPE_DB, scenario.executionFlow().get(0).type());
    }

    @Test
    void whenExtracting_givenTraceWithLogs_shouldExtractDataFromLogs() {
        final Instant now = Instant.now();
        final SpanDetail span = new SpanDetail(
                "span1", null, "order-service", "process",
                "process", now, 100_000_000L, false,
                "", "", "", Map.of()
        );

        final TraceDetail traceDetail = new TraceDetail(
                "trace123", "order-service", "prod", "process",
                now, 100_000_000L, List.of(span)
        );

        final ServiceErrorView.LogEntry log = new ServiceErrorView.LogEntry(
                now, "ERROR", "Processing failed",
                Map.of("user_id", "789", "order_id", "456")
        );

        final TraceScenario scenario = extractor.extract(traceDetail, List.of(log));

        assertTrue(scenario.relevantData().containsKey("user_id"));
        assertEquals("789", scenario.relevantData().get("user_id"));
    }

    @Test
    void whenExtracting_givenErrorScenario_shouldGenerateSuggestedTestScenario() {
        final Instant now = Instant.now();
        final SpanDetail errorSpan = new SpanDetail(
                "span1", null, "order-service", "validateStock",
                "OrderService.validateStock", now, 10_000_000L, true,
                "Not enough stock",
                "InsufficientStockException",
                "",
                Map.of("product_id", "123", "quantity", "5")
        );

        final TraceDetail traceDetail = new TraceDetail(
                "trace123", "order-service", "prod", "validateStock",
                now, 10_000_000L, List.of(errorSpan)
        );

        final TraceScenario scenario = extractor.extract(traceDetail, List.of());

        final Map<String, String> testScenario = scenario.suggestedTestScenario();
        assertNotNull(testScenario);
        assertFalse(testScenario.isEmpty());
        assertTrue(testScenario.containsKey("given"));
        assertTrue(testScenario.containsKey("when"));
        assertTrue(testScenario.containsKey("then"));
        assertTrue(testScenario.get("then").contains("InsufficientStockException"));
    }
}
