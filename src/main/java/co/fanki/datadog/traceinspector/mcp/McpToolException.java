package co.fanki.datadog.traceinspector.mcp;

/**
 * Exception thrown when an MCP tool execution fails.
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public class McpToolException extends RuntimeException {

    private final String toolName;

    /**
     * Creates a new McpToolException.
     *
     * @param toolName the name of the tool that failed
     * @param message the error message
     */
    public McpToolException(final String toolName, final String message) {
        super(message);
        this.toolName = toolName;
    }

    /**
     * Creates a new McpToolException with a cause.
     *
     * @param toolName the name of the tool that failed
     * @param message the error message
     * @param cause the underlying cause
     */
    public McpToolException(
            final String toolName,
            final String message,
            final Throwable cause
    ) {
        super(message, cause);
        this.toolName = toolName;
    }

    /**
     * Returns the name of the tool that failed.
     *
     * @return the tool name
     */
    public String getToolName() {
        return toolName;
    }
}
