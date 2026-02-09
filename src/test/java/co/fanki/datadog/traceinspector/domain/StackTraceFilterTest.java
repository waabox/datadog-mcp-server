package co.fanki.datadog.traceinspector.domain;

import co.fanki.datadog.traceinspector.domain.StackTraceFilter.StackTraceDetail;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link StackTraceFilter}.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class StackTraceFilterTest {

    private static final String SAMPLE_STACK_TRACE = """
            co.fanki.pass.ticket.exception.TransitionException: Transition from USED with action DIRECT_TRANSFER is not allowed.
            \tat co.fanki.pass.ticket.util.statemachine.StateMachine.lambda$handle$0(StateMachine.java:45)
            \tat java.base/java.util.Optional.orElseThrow(Optional.java:403)
            \tat co.fanki.pass.ticket.util.statemachine.StateMachine.handle(StateMachine.java:45)
            \tat co.fanki.pass.ticket.domain.ticket.domain.entities.TicketStateMachine.handle(TicketStateMachine.java:193)
            \tat co.fanki.pass.ticket.domain.ticket.domain.entities.Ticket.directTransferTo(Ticket.java:1197)
            \tat co.fanki.pass.ticket.domain.ticket.domain.service.TicketService.transferTicket(TicketService.java:1824)
            \tat java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)
            \tat java.base/java.lang.reflect.Method.invoke(Method.java:580)
            \tat org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:343)
            \tat org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:699)
            \tat co.fanki.pass.ticket.domain.ticket.application.controller.TicketController.transferTicket(TicketController.java:530)
            \tat org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:195)
            \tat feign.SynchronousMethodHandler.invoke(SynchronousMethodHandler.java:72)
            """;

    @Test
    void whenFilteringWithFullDetail_givenAnyStackTrace_shouldReturnUnchanged() {
        final StackTraceFilter filter = new StackTraceFilter(List.of("co.fanki"));

        final String result = filter.filter(SAMPLE_STACK_TRACE, StackTraceDetail.FULL);

        assertEquals(SAMPLE_STACK_TRACE, result);
    }

    @Test
    void whenFilteringWithRelevantDetail_givenRelevantPackages_shouldKeepOnlyThoseFrames() {
        final StackTraceFilter filter = new StackTraceFilter(List.of("co.fanki"));

        final String result = filter.filter(SAMPLE_STACK_TRACE, StackTraceDetail.RELEVANT);

        // Should keep co.fanki frames
        assertTrue(result.contains("StateMachine.lambda$handle$0(StateMachine.java:45)"));
        assertTrue(result.contains("TicketStateMachine.handle(TicketStateMachine.java:193)"));
        assertTrue(result.contains("Ticket.directTransferTo(Ticket.java:1197)"));
        assertTrue(result.contains("TicketService.transferTicket(TicketService.java:1824)"));
        assertTrue(result.contains("TicketController.transferTicket(TicketController.java:530)"));

        // Should NOT keep framework frames
        assertFalse(result.contains("java.util.Optional.orElseThrow"));
        assertFalse(result.contains("AopUtils.invokeJoinpointUsingReflection"));
        assertFalse(result.contains("CglibAopProxy"));
        assertFalse(result.contains("ApplicationFilterChain"));

        // Should have omitted frames summary
        assertTrue(result.contains("framework frames omitted"));
    }

    @Test
    void whenFilteringWithMinimalDetail_givenStackTrace_shouldReturnOnlyExceptionLine() {
        final StackTraceFilter filter = new StackTraceFilter(List.of("co.fanki"));

        final String result = filter.filter(SAMPLE_STACK_TRACE, StackTraceDetail.MINIMAL);

        assertTrue(result.contains("TransitionException: Transition from USED"));
        assertFalse(result.contains("at co.fanki"));
        assertFalse(result.contains("StateMachine"));
    }

    @Test
    void whenFilteringWithCausedBy_givenNestedExceptions_shouldKeepCausedByLines() {
        final String stackWithCause = """
                java.lang.RuntimeException: Wrapper exception
                \tat com.example.Service.method(Service.java:10)
                Caused by: co.fanki.pass.ticket.exception.TransitionException: Root cause
                \tat co.fanki.pass.ticket.domain.Ticket.transfer(Ticket.java:100)
                \tat org.springframework.proxy.invoke(Proxy.java:50)
                """;

        final StackTraceFilter filter = new StackTraceFilter(List.of("co.fanki"));

        final String result = filter.filter(stackWithCause, StackTraceDetail.MINIMAL);

        assertTrue(result.contains("RuntimeException: Wrapper exception"));
        assertTrue(result.contains("Caused by: co.fanki.pass.ticket.exception.TransitionException"));
    }

    @Test
    void whenFilteringWithEmptyPackages_givenAnyDetail_shouldReturnFullStackTrace() {
        final StackTraceFilter filter = new StackTraceFilter(Collections.emptyList());

        final String result = filter.filter(SAMPLE_STACK_TRACE, StackTraceDetail.RELEVANT);

        assertEquals(SAMPLE_STACK_TRACE, result);
    }

    @Test
    void whenFilteringNullStackTrace_shouldReturnNull() {
        final StackTraceFilter filter = new StackTraceFilter(List.of("co.fanki"));

        final String result = filter.filter(null, StackTraceDetail.RELEVANT);

        assertEquals(null, result);
    }

    @Test
    void whenFilteringBlankStackTrace_shouldReturnAsIs() {
        final StackTraceFilter filter = new StackTraceFilter(List.of("co.fanki"));

        final String result = filter.filter("   ", StackTraceDetail.RELEVANT);

        assertEquals("   ", result);
    }

    @Test
    void whenFilteringAttributes_givenStackTraceKey_shouldFilterStackTrace() {
        final StackTraceFilter filter = new StackTraceFilter(List.of("co.fanki"));

        final Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("stack_trace", SAMPLE_STACK_TRACE);
        attributes.put("other_key", "other_value");

        final Map<String, Object> result = filter.filterAttributes(
                attributes, StackTraceDetail.RELEVANT
        );

        final String filteredStack = (String) result.get("stack_trace");
        assertTrue(filteredStack.contains("framework frames omitted"));
        assertEquals("other_value", result.get("other_key"));
    }

    @Test
    void whenFilteringAttributes_givenNoStackTraceKey_shouldReturnOriginal() {
        final StackTraceFilter filter = new StackTraceFilter(List.of("co.fanki"));

        final Map<String, Object> attributes = Map.of(
                "level", "ERROR",
                "message", "Some error"
        );

        final Map<String, Object> result = filter.filterAttributes(
                attributes, StackTraceDetail.RELEVANT
        );

        assertEquals(attributes, result);
    }

    @Test
    void whenFilteringAttributes_givenNullAttributes_shouldReturnNull() {
        final StackTraceFilter filter = new StackTraceFilter(List.of("co.fanki"));

        final Map<String, Object> result = filter.filterAttributes(
                null, StackTraceDetail.RELEVANT
        );

        assertEquals(null, result);
    }

    @Test
    void whenFilteringWithMultiplePackages_shouldKeepAllRelevantFrames() {
        final String stackWithMultiplePackages = """
                java.lang.Exception: Test
                \tat co.fanki.service.ServiceA.method(ServiceA.java:10)
                \tat org.springframework.proxy.invoke(Proxy.java:50)
                \tat com.mycompany.other.OtherClass.call(OtherClass.java:20)
                \tat org.apache.tomcat.filter.doFilter(Filter.java:30)
                """;

        final StackTraceFilter filter = new StackTraceFilter(
                List.of("co.fanki", "com.mycompany")
        );

        final String result = filter.filter(stackWithMultiplePackages, StackTraceDetail.RELEVANT);

        assertTrue(result.contains("ServiceA.method(ServiceA.java:10)"));
        assertTrue(result.contains("OtherClass.call(OtherClass.java:20)"));
        assertFalse(result.contains("springframework"));
        assertFalse(result.contains("tomcat"));
    }

    @Test
    void whenFilteringRelevant_shouldShowFrameworkNamesInSummary() {
        final StackTraceFilter filter = new StackTraceFilter(List.of("co.fanki"));

        final String result = filter.filter(SAMPLE_STACK_TRACE, StackTraceDetail.RELEVANT);

        // Should mention the frameworks that were omitted
        assertTrue(result.contains("spring") || result.contains("java") || result.contains("apache"));
    }
}
