package co.fanki.datadog.traceinspector.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for McpProtocolHandler.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
class McpProtocolHandlerTest {

    private ObjectMapper objectMapper;
    private McpProtocolHandler handler;
    private TestTool testTool;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        testTool = new TestTool();
        handler = new McpProtocolHandler(List.of(testTool));
    }

    @Test
    void whenHandlingInitialize_givenValidRequest_shouldReturnServerInfo() throws Exception {
        final String request = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
                """;

        final String response = handler.handleRequest(request);
        final JsonNode json = objectMapper.readTree(response);

        assertEquals("2.0", json.get("jsonrpc").asText());
        assertEquals(1, json.get("id").asInt());
        assertNotNull(json.get("result"));

        final JsonNode result = json.get("result");
        assertEquals("2024-11-05", result.get("protocolVersion").asText());
        assertNotNull(result.get("serverInfo"));
        assertEquals("datadog-trace-inspector", result.get("serverInfo").get("name").asText());
    }

    @Test
    void whenHandlingToolsList_givenValidRequest_shouldReturnToolDefinitions() throws Exception {
        final String request = """
                {"jsonrpc":"2.0","id":2,"method":"tools/list"}
                """;

        final String response = handler.handleRequest(request);
        final JsonNode json = objectMapper.readTree(response);

        assertEquals("2.0", json.get("jsonrpc").asText());
        assertEquals(2, json.get("id").asInt());
        assertNotNull(json.get("result"));

        final JsonNode tools = json.get("result").get("tools");
        assertNotNull(tools);
        assertTrue(tools.isArray());
        assertEquals(1, tools.size());

        final JsonNode tool = tools.get(0);
        assertEquals("test.tool", tool.get("name").asText());
        assertEquals("A test tool", tool.get("description").asText());
        assertNotNull(tool.get("inputSchema"));
    }

    @Test
    void whenHandlingToolsCall_givenValidRequest_shouldExecuteTool() throws Exception {
        final String request = """
                {
                  "jsonrpc": "2.0",
                  "id": 3,
                  "method": "tools/call",
                  "params": {
                    "name": "test.tool",
                    "arguments": {"input": "hello"}
                  }
                }
                """;

        final String response = handler.handleRequest(request);
        final JsonNode json = objectMapper.readTree(response);

        assertEquals("2.0", json.get("jsonrpc").asText());
        assertEquals(3, json.get("id").asInt());
        assertNotNull(json.get("result"));

        final JsonNode content = json.get("result").get("content");
        assertNotNull(content);
        assertTrue(content.isArray());
        assertEquals(1, content.size());
        assertEquals("text", content.get(0).get("type").asText());

        final String text = content.get(0).get("text").asText();
        assertTrue(text.contains("echo"));
        assertTrue(text.contains("hello"));
    }

    @Test
    void whenHandlingToolsCall_givenUnknownTool_shouldReturnError() throws Exception {
        final String request = """
                {
                  "jsonrpc": "2.0",
                  "id": 4,
                  "method": "tools/call",
                  "params": {
                    "name": "unknown.tool",
                    "arguments": {}
                  }
                }
                """;

        final String response = handler.handleRequest(request);
        final JsonNode json = objectMapper.readTree(response);

        assertEquals("2.0", json.get("jsonrpc").asText());
        assertEquals(4, json.get("id").asInt());
        assertNotNull(json.get("error"));
        assertEquals(-32602, json.get("error").get("code").asInt());
        assertTrue(json.get("error").get("message").asText().contains("Unknown tool"));
    }

    @Test
    void whenHandlingInvalidJson_givenMalformedRequest_shouldReturnParseError() throws Exception {
        final String request = "not valid json {";

        final String response = handler.handleRequest(request);
        final JsonNode json = objectMapper.readTree(response);

        assertEquals("2.0", json.get("jsonrpc").asText());
        assertNotNull(json.get("error"));
        assertEquals(-32700, json.get("error").get("code").asInt());
        assertTrue(json.get("error").get("message").asText().contains("Parse error"));
    }

    @Test
    void whenHandlingMissingMethod_givenRequestWithoutMethod_shouldReturnError() throws Exception {
        final String request = """
                {"jsonrpc":"2.0","id":5}
                """;

        final String response = handler.handleRequest(request);
        final JsonNode json = objectMapper.readTree(response);

        assertEquals("2.0", json.get("jsonrpc").asText());
        assertEquals(5, json.get("id").asInt());
        assertNotNull(json.get("error"));
        assertEquals(-32600, json.get("error").get("code").asInt());
    }

    @Test
    void whenHandlingUnknownMethod_givenInvalidMethod_shouldReturnMethodNotFound() throws Exception {
        final String request = """
                {"jsonrpc":"2.0","id":6,"method":"unknown/method"}
                """;

        final String response = handler.handleRequest(request);
        final JsonNode json = objectMapper.readTree(response);

        assertEquals("2.0", json.get("jsonrpc").asText());
        assertEquals(6, json.get("id").asInt());
        assertNotNull(json.get("error"));
        assertEquals(-32601, json.get("error").get("code").asInt());
        assertTrue(json.get("error").get("message").asText().contains("Method not found"));
    }

    @Test
    void whenHandlingNotification_givenInitializedNotification_shouldReturnEmptyResponse() {
        final String request = """
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                """;

        final String response = handler.handleRequest(request);

        assertTrue(response.isEmpty());
    }

    @Test
    void whenHandlingBlankRequest_givenEmptyString_shouldReturnInvalidRequest() throws Exception {
        final String response = handler.handleRequest("");
        final JsonNode json = objectMapper.readTree(response);

        assertNotNull(json.get("error"));
        assertEquals(-32600, json.get("error").get("code").asInt());
    }

    @Test
    void whenHandlingNullRequest_givenNull_shouldReturnInvalidRequest() throws Exception {
        final String response = handler.handleRequest(null);
        final JsonNode json = objectMapper.readTree(response);

        assertNotNull(json.get("error"));
        assertEquals(-32600, json.get("error").get("code").asInt());
    }

    @Test
    void whenHandlingStringId_givenStringId_shouldPreserveIdType() throws Exception {
        final String request = """
                {"jsonrpc":"2.0","id":"string-id-123","method":"initialize","params":{}}
                """;

        final String response = handler.handleRequest(request);
        final JsonNode json = objectMapper.readTree(response);

        assertEquals("string-id-123", json.get("id").asText());
    }

    /**
     * Test tool implementation for unit testing.
     */
    private static class TestTool implements McpTool {

        @Override
        public String name() {
            return "test.tool";
        }

        @Override
        public String description() {
            return "A test tool";
        }

        @Override
        public Map<String, Object> inputSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "input", Map.of(
                                    "type", "string",
                                    "description", "Test input"
                            )
                    )
            );
        }

        @Override
        public Map<String, Object> execute(final Map<String, Object> arguments) {
            final String input = arguments.getOrDefault("input", "default").toString();
            return Map.of("echo", input);
        }
    }
}
