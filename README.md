# Datadog Trace Diagnostic MCP Server

An MCP (Model Context Protocol) server that enables AI assistants like Claude to query Datadog APM for error traces and generate actionable debugging workflows.

## What Does This Server Do?

This MCP server provides two powerful diagnostic tools:

### 1. List Error Traces (`trace.list_error_traces`)
Searches Datadog APM for error traces matching your criteria:
- Filter by service name, environment, and time range
- Returns trace IDs, error messages, durations, and timestamps
- Perfect for finding recent production errors

### 2. Inspect Error Trace (`trace.inspect_error_trace`)
Performs deep analysis of a specific error trace:
- Retrieves all spans in the distributed trace
- Fetches associated log entries
- Identifies all services involved in the error
- **Generates a markdown diagnostic workflow** with:
  - Error summary and timeline
  - Stack traces and error types
  - Service-by-service breakdown
  - Recommended debugging steps
  - Direct links to Datadog UI

## Datadog Permissions Required

This server uses three Datadog API endpoints that require specific permissions:

| Endpoint | Permission Required |
|----------|---------------------|
| `POST /api/v2/spans/events/search` | `apm_read` |
| `GET /api/v1/trace/{traceId}` | `apm_read` |
| `POST /api/v2/logs/events/search` | `logs_read_data` |

### Minimum Required Scopes

When creating your Application Key, you need these scopes:
- **`apm_read`** - Read APM data (traces, spans, services)
- **`logs_read_data`** - Read log data associated with traces

### Recommended Datadog Roles

The user creating the Application Key should have one of these roles:
- **Datadog Read Only** - Sufficient for this server (read-only operations)
- **Datadog Standard** - Includes read permissions
- **Custom Role** with `apm_read` and `logs_read_data` permissions

## Creating Your Datadog Keys

You need two keys: an **API Key** and an **Application Key**.

### Step 1: Create an API Key

1. Log in to [Datadog](https://app.datadoghq.com)
2. Go to **Organization Settings** (bottom-left menu)
3. Navigate to **API Keys** section
4. Click **+ New Key**
5. Give it a name (e.g., `mcp-server-api-key`)
6. Click **Create Key**
7. **Copy and save the key** - you won't see it again!

### Step 2: Create an Application Key with Scopes

1. In **Organization Settings**, go to **Application Keys**
2. Click **+ New Key**
3. Give it a name (e.g., `mcp-server-app-key`)
4. **Important**: Click **Select Scopes** and check:
   - `apm_read` - Read APM data
   - `logs_read_data` - Read logs data
5. Click **Create Key**
6. **Copy and save the key** - you won't see it again!

### Step 3: Verify Your Keys Work

```bash
# Test API connectivity
curl -X GET "https://api.datadoghq.com/api/v1/validate" \
  -H "DD-API-KEY: your-api-key" \
  -H "DD-APPLICATION-KEY: your-app-key"

# Expected response: {"valid":true}
```

## Requirements

- **Java 21** or higher
- **Maven 3.8+** (for building)
- **Docker** (for running integration tests)

## Installation

### Build from Source

```bash
# Clone the repository
git clone https://github.com/your-org/datadog-mcp-server.git
cd datadog-mcp-server

# Build the fat JAR
mvn clean package

# The JAR will be at: target/datadog-mcp-server-1.0.0-SNAPSHOT.jar
```

### Verify the Build

```bash
# Check the JAR was created
ls -la target/datadog-mcp-server-1.0.0-SNAPSHOT.jar

# Test it responds to MCP protocol
echo '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' | \
  DATADOG_API_KEY=test DATADOG_APP_KEY=test \
  java -jar target/datadog-mcp-server-1.0.0-SNAPSHOT.jar
```

## Setting Up with Claude Code

### Option 1: Global Configuration (Recommended)

Edit your Claude Code MCP settings file:

**macOS/Linux:** `~/.config/claude-code/mcp.json`
**Windows:** `%APPDATA%\claude-code\mcp.json`

```json
{
  "mcpServers": {
    "datadog-traces": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/datadog-mcp-server-1.0.0-SNAPSHOT.jar"
      ],
      "env": {
        "DATADOG_API_KEY": "your-api-key-here",
        "DATADOG_APP_KEY": "your-application-key-here",
        "DATADOG_SITE": "datadoghq.com",
        "DATADOG_ENV_DEFAULT": "prod"
      }
    }
  }
}
```

### Option 2: Project-Level Configuration

Create `.claude/mcp.json` in your project root:

```json
{
  "mcpServers": {
    "datadog-traces": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/datadog-mcp-server-1.0.0-SNAPSHOT.jar"
      ],
      "env": {
        "DATADOG_API_KEY": "your-api-key-here",
        "DATADOG_APP_KEY": "your-application-key-here"
      }
    }
  }
}
```

### Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `DATADOG_API_KEY` | Yes | - | Your Datadog API key |
| `DATADOG_APP_KEY` | Yes | - | Your Datadog Application key |
| `DATADOG_SITE` | No | `datadoghq.com` | Datadog site (e.g., `datadoghq.eu`, `us5.datadoghq.com`) |
| `DATADOG_ENV_DEFAULT` | No | `prod` | Default environment for queries |

### Verify Claude Code Can Use the Server

After configuring, restart Claude Code and test:

```
You: List error traces for service "my-api" from the last hour

Claude: I'll use the Datadog trace inspector to find error traces...
```

## Using the Tools

### List Error Traces

Ask Claude to find errors:

```
"Show me error traces for the user-service in production from the last 2 hours"

"Find errors in payment-api between 2024-01-15 10:00 and 11:00 UTC"

"What errors happened in auth-service today?"
```

**Tool Input:**
```json
{
  "service": "user-service",
  "env": "prod",
  "from": "2024-01-15T10:00:00Z",
  "to": "2024-01-15T12:00:00Z",
  "limit": 20
}
```

### Inspect a Specific Trace

Once you have a trace ID:

```
"Analyze trace abc123def456 and tell me what went wrong"

"Generate a diagnostic report for this error trace: xyz789"
```

**Tool Input:**
```json
{
  "service": "user-service",
  "env": "prod",
  "from": "2024-01-15T10:00:00Z",
  "to": "2024-01-15T12:00:00Z",
  "traceId": "abc123def456"
}
```

**Output includes:**
- Complete span timeline
- Error messages and stack traces
- All services in the distributed trace
- Related log entries
- Recommended debugging actions
- Direct Datadog links

## Datadog Site Configuration

If you're not using the main US Datadog site, set `DATADOG_SITE`:

| Region | Site Value |
|--------|------------|
| US1 (default) | `datadoghq.com` |
| US3 | `us3.datadoghq.com` |
| US5 | `us5.datadoghq.com` |
| EU | `datadoghq.eu` |
| AP1 | `ap1.datadoghq.com` |
| US1-FED | `ddog-gov.com` |

## Troubleshooting

### "Configuration error: Required environment variable DATADOG_API_KEY is not set"

Ensure your MCP configuration includes the environment variables:
```json
"env": {
  "DATADOG_API_KEY": "your-key",
  "DATADOG_APP_KEY": "your-key"
}
```

### "API request failed" with 401 or 403

Your keys don't have the required permissions. Verify:
1. API key is valid: test with `/api/v1/validate`
2. Application key has `apm_read` and `logs_read_data` scopes
3. User who created the app key has appropriate Datadog role

### "API request failed" with 429

You're being rate-limited. Reduce query frequency or contact Datadog support.

### No traces returned

- Verify the service name matches exactly (case-sensitive)
- Check the time range includes when errors occurred
- Ensure APM is properly configured for your service

## Development

### Running Tests

```bash
# All tests (requires Docker for integration tests)
mvn test

# Unit tests only
mvn test -Dtest="*Test" -DfailIfNoTests=false
```

### Project Structure

```
src/main/java/co/fanki/datadog/traceinspector/
├── DatadogMcpServer.java           # Main entry point
├── config/DatadogConfig.java       # Environment configuration
├── domain/                         # Domain models (records)
├── datadog/                        # Datadog API client
├── application/                    # Business logic services
└── mcp/                            # MCP protocol handling
```

## License

MIT License

## Author

waabox (emiliano[at]fanki[dot]co)
