package co.fanki.datadog.traceinspector;

import co.fanki.datadog.traceinspector.application.MarkdownWorkflowGenerator;
import co.fanki.datadog.traceinspector.application.TraceDiagnosticService;
import co.fanki.datadog.traceinspector.application.TraceScenarioExtractor;
import co.fanki.datadog.traceinspector.config.DatadogConfig;
import co.fanki.datadog.traceinspector.config.FilterConfigStore;
import co.fanki.datadog.traceinspector.datadog.DatadogClient;
import co.fanki.datadog.traceinspector.datadog.DatadogClientImpl;
import co.fanki.datadog.traceinspector.mcp.FilterConfigureTool;
import co.fanki.datadog.traceinspector.mcp.LogCorrelateTool;
import co.fanki.datadog.traceinspector.mcp.LogSearchTool;
import co.fanki.datadog.traceinspector.mcp.McpProtocolHandler;
import co.fanki.datadog.traceinspector.mcp.McpTool;
import co.fanki.datadog.traceinspector.mcp.TraceExtractScenarioTool;
import co.fanki.datadog.traceinspector.mcp.TraceInspectErrorTraceTool;
import co.fanki.datadog.traceinspector.mcp.TraceListErrorTracesTool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Main entry point for the Datadog MCP Server.
 *
 * <p>This server provides MCP tools for querying Datadog error traces
 * and generating diagnostic workflows. It communicates via JSON-RPC 2.0
 * over stdio.</p>
 *
 * <p>Required environment variables:
 * <ul>
 *   <li>DATADOG_API_KEY - Datadog API key</li>
 *   <li>DATADOG_APP_KEY - Datadog application key</li>
 * </ul>
 * </p>
 *
 * <p>Optional environment variables:
 * <ul>
 *   <li>DATADOG_SITE - Datadog site (default: datadoghq.com)</li>
 *   <li>DATADOG_ENV_DEFAULT - Default environment (default: prod)</li>
 * </ul>
 * </p>
 *
 * @author waabox(emiliano[at]fanki[dot]co)
 */
public final class DatadogMcpServer {

    private final McpProtocolHandler protocolHandler;
    private final BufferedReader reader;
    private final PrintWriter writer;

    /**
     * Creates a new DatadogMcpServer with the given dependencies.
     *
     * @param protocolHandler the MCP protocol handler
     * @param reader the input reader (typically stdin)
     * @param writer the output writer (typically stdout)
     */
    public DatadogMcpServer(
            final McpProtocolHandler protocolHandler,
            final BufferedReader reader,
            final PrintWriter writer
    ) {
        this.protocolHandler = protocolHandler;
        this.reader = reader;
        this.writer = writer;
    }

    /**
     * Runs the server's main loop, reading JSON-RPC requests and
     * writing responses.
     */
    public void run() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                final String response = protocolHandler.handleRequest(line);

                // Don't send empty responses (for notifications)
                if (response != null && !response.isBlank()) {
                    writer.println(response);
                    writer.flush();
                }
            }
        } catch (final IOException e) {
            System.err.println("Error reading input: " + e.getMessage());
        }
    }

    /**
     * Main entry point.
     *
     * @param args command line arguments (not used)
     */
    public static void main(final String[] args) {
        try {
            // Load configuration from environment
            final DatadogConfig config = DatadogConfig.fromEnvironment();

            // Wire up dependencies
            final DatadogClient datadogClient = new DatadogClientImpl(config);
            final MarkdownWorkflowGenerator workflowGenerator =
                    new MarkdownWorkflowGenerator();
            final TraceDiagnosticService diagnosticService =
                    new TraceDiagnosticService(datadogClient, workflowGenerator);
            final TraceScenarioExtractor scenarioExtractor =
                    new TraceScenarioExtractor();
            final FilterConfigStore filterConfigStore = new FilterConfigStore();

            // Create tools
            final List<McpTool> tools = List.of(
                    new TraceListErrorTracesTool(diagnosticService, config),
                    new TraceInspectErrorTraceTool(diagnosticService, config),
                    new LogSearchTool(datadogClient, config, filterConfigStore),
                    new LogCorrelateTool(datadogClient, config, filterConfigStore),
                    new TraceExtractScenarioTool(datadogClient, scenarioExtractor, config),
                    new FilterConfigureTool(filterConfigStore)
            );

            // Create protocol handler
            final McpProtocolHandler protocolHandler = new McpProtocolHandler(tools);

            // Set up stdio
            final BufferedReader reader = new BufferedReader(
                    new InputStreamReader(System.in, StandardCharsets.UTF_8)
            );
            final PrintWriter writer = new PrintWriter(
                    System.out, true, StandardCharsets.UTF_8
            );

            // Create and run server
            final DatadogMcpServer server = new DatadogMcpServer(
                    protocolHandler, reader, writer
            );

            server.run();

        } catch (final IllegalStateException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.err.println("Please set the required environment variables:");
            System.err.println("  DATADOG_API_KEY - Your Datadog API key");
            System.err.println("  DATADOG_APP_KEY - Your Datadog application key");
            System.exit(1);
        } catch (final Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
