package co.fanki.datadog.traceinspector.mcp;

import java.util.Map;

/**
 * Interface for MCP tools that can be invoked via JSON-RPC.
 *
 * <p>Each tool implementation provides metadata about itself (name, description,
 * input schema) and can execute with given arguments.</p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public interface McpTool {

    /**
     * Returns the unique tool name.
     *
     * @return the tool name used for invocation
     */
    String name();

    /**
     * Returns a human-readable description of what the tool does.
     *
     * @return the tool description
     */
    String description();

    /**
     * Returns the JSON Schema for the tool's input parameters.
     *
     * @return a map representing the JSON Schema
     */
    Map<String, Object> inputSchema();

    /**
     * Executes the tool with the given arguments.
     *
     * @param arguments the input arguments matching the input schema
     *
     * @return the tool result as a map
     *
     * @throws McpToolException if execution fails
     */
    Map<String, Object> execute(Map<String, Object> arguments);

    /**
     * Converts this tool to its MCP tool definition format.
     *
     * @return a map representing the tool definition
     */
    default Map<String, Object> toToolDefinition() {
        return Map.of(
                "name", name(),
                "description", description(),
                "inputSchema", inputSchema()
        );
    }
}
