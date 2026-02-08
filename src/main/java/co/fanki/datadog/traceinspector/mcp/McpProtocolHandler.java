package co.fanki.datadog.traceinspector.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Handles MCP JSON-RPC 2.0 protocol messages.
 *
 * <p>This handler processes incoming JSON-RPC requests and routes them
 * to the appropriate tool implementations. It supports the MCP protocol
 * methods: initialize, tools/list, and tools/call.</p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public final class McpProtocolHandler {

    private static final String JSONRPC_VERSION = "2.0";
    private static final String PROTOCOL_VERSION = "2024-11-05";
    private static final String SERVER_NAME = "datadog-trace-inspector";
    private static final String SERVER_VERSION = "1.0.0";

    private final ObjectMapper objectMapper;
    private final Map<String, McpTool> tools;

    /**
     * Creates a new McpProtocolHandler with the given tools.
     *
     * @param tools the list of tools to register
     */
    public McpProtocolHandler(final List<McpTool> tools) {
        Objects.requireNonNull(tools, "tools must not be null");

        this.objectMapper = new ObjectMapper();
        this.tools = new HashMap<>();

        for (final McpTool tool : tools) {
            this.tools.put(tool.name(), tool);
        }
    }

    /**
     * Handles an incoming JSON-RPC request.
     *
     * @param request the JSON-RPC request string
     *
     * @return the JSON-RPC response string
     */
    public String handleRequest(final String request) {
        if (request == null || request.isBlank()) {
            return buildErrorResponse(null, -32600, "Invalid Request");
        }

        try {
            final JsonNode requestNode = objectMapper.readTree(request);
            return processRequest(requestNode);
        } catch (final JsonProcessingException e) {
            return buildErrorResponse(null, -32700, "Parse error: " + e.getMessage());
        }
    }

    private String processRequest(final JsonNode requestNode) {
        final JsonNode idNode = requestNode.get("id");
        final Object id = idNode != null && !idNode.isNull()
                ? (idNode.isNumber() ? idNode.asLong() : idNode.asText())
                : null;

        final JsonNode methodNode = requestNode.get("method");
        if (methodNode == null || !methodNode.isTextual()) {
            return buildErrorResponse(id, -32600, "Invalid Request: missing method");
        }

        final String method = methodNode.asText();
        final JsonNode params = requestNode.get("params");

        try {
            return switch (method) {
                case "initialize" -> handleInitialize(id, params);
                case "tools/list" -> handleToolsList(id);
                case "tools/call" -> handleToolsCall(id, params);
                case "notifications/initialized" -> ""; // No response for notifications
                default -> buildErrorResponse(id, -32601, "Method not found: " + method);
            };
        } catch (final McpToolException e) {
            return buildErrorResponse(id, -32000, e.getMessage());
        } catch (final Exception e) {
            return buildErrorResponse(id, -32603, "Internal error: " + e.getMessage());
        }
    }

    private String handleInitialize(final Object id, final JsonNode params) {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", PROTOCOL_VERSION);

        final Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);
        result.put("serverInfo", serverInfo);

        final Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("tools", Map.of());
        result.put("capabilities", capabilities);

        return buildSuccessResponse(id, result);
    }

    private String handleToolsList(final Object id) {
        final List<Map<String, Object>> toolDefinitions = tools.values().stream()
                .map(McpTool::toToolDefinition)
                .toList();

        final Map<String, Object> result = Map.of("tools", toolDefinitions);
        return buildSuccessResponse(id, result);
    }

    private String handleToolsCall(final Object id, final JsonNode params) {
        if (params == null) {
            return buildErrorResponse(id, -32602, "Invalid params: params required");
        }

        final JsonNode nameNode = params.get("name");
        if (nameNode == null || !nameNode.isTextual()) {
            return buildErrorResponse(id, -32602, "Invalid params: name required");
        }

        final String toolName = nameNode.asText();
        final McpTool tool = tools.get(toolName);

        if (tool == null) {
            return buildErrorResponse(id, -32602, "Unknown tool: " + toolName);
        }

        final JsonNode argsNode = params.get("arguments");
        final Map<String, Object> arguments = argsNode != null
                ? objectMapper.convertValue(argsNode, Map.class)
                : Map.of();

        final Map<String, Object> toolResult = tool.execute(arguments);

        // Format as MCP tool result
        final Map<String, Object> result = new LinkedHashMap<>();
        final List<Map<String, Object>> content = List.of(Map.of(
                "type", "text",
                "text", serializeToolResult(toolResult)
        ));
        result.put("content", content);

        return buildSuccessResponse(id, result);
    }

    private String serializeToolResult(final Map<String, Object> result) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(result);
        } catch (final JsonProcessingException e) {
            return "{}";
        }
    }

    private String buildSuccessResponse(final Object id, final Object result) {
        try {
            final ObjectNode response = objectMapper.createObjectNode();
            response.put("jsonrpc", JSONRPC_VERSION);

            if (id instanceof Number num) {
                response.put("id", num.longValue());
            } else if (id instanceof String str) {
                response.put("id", str);
            } else {
                response.putNull("id");
            }

            response.set("result", objectMapper.valueToTree(result));
            return objectMapper.writeValueAsString(response);
        } catch (final JsonProcessingException e) {
            return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":" +
                    "{\"code\":-32603,\"message\":\"Failed to serialize response\"}}";
        }
    }

    private String buildErrorResponse(final Object id, final int code, final String message) {
        try {
            final ObjectNode response = objectMapper.createObjectNode();
            response.put("jsonrpc", JSONRPC_VERSION);

            if (id instanceof Number num) {
                response.put("id", num.longValue());
            } else if (id instanceof String str) {
                response.put("id", str);
            } else {
                response.putNull("id");
            }

            final ObjectNode error = objectMapper.createObjectNode();
            error.put("code", code);
            error.put("message", message);
            response.set("error", error);

            return objectMapper.writeValueAsString(response);
        } catch (final JsonProcessingException e) {
            return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":" +
                    "{\"code\":-32603,\"message\":\"Failed to serialize error\"}}";
        }
    }

    /**
     * Returns the list of registered tools.
     *
     * @return an unmodifiable map of tools by name
     */
    public Map<String, McpTool> getTools() {
        return Map.copyOf(tools);
    }
}
