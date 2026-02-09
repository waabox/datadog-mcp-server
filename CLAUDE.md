# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Datadog MCP Server - An MCP (Model Context Protocol) server that enables AI assistants to query Datadog APM for error traces, search logs, and generate actionable debugging workflows. Communicates via JSON-RPC 2.0 over stdio.

## Build & Test Commands

```bash
# Build (creates shaded JAR with all dependencies)
mvn clean package

# Build without tests
mvn clean package -DskipTests

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=LogGroupSummaryTest

# Run a single test method
mvn test -Dtest=LogGroupSummaryTest#whenExtractingPattern_givenUuid_shouldReplaceWithPlaceholder

# Run the server locally (requires env vars)
java --enable-preview -jar target/datadog-mcp-server-1.3.1.jar
```

## Required Environment Variables

```bash
DATADOG_API_KEY=<your-api-key>      # Required
DATADOG_APP_KEY=<your-app-key>      # Required
DATADOG_SITE=datadoghq.com          # Optional (default: datadoghq.com)
DATADOG_ENV_DEFAULT=prod            # Optional (default: prod)
```

## Architecture

### Package Structure

```
co.fanki.datadog.traceinspector
├── DatadogMcpServer          # Entry point, wires dependencies, runs stdio loop
├── config/
│   └── DatadogConfig         # Environment-based configuration (record)
├── datadog/
│   ├── DatadogClient         # Interface for Datadog API operations
│   └── DatadogClientImpl     # Implementation using Datadog SDK
├── domain/                   # Rich domain models (records, immutable)
│   ├── TraceQuery, TraceSummary, TraceDetail, SpanDetail
│   ├── LogQuery, LogSummary, LogGroupSummary
│   ├── DiagnosticResult, ServiceErrorView
│   └── TraceScenario, EntryPoint, ExecutionStep, ErrorContext, StackTraceLocation
├── application/              # Orchestration services
│   ├── TraceDiagnosticService      # Coordinates trace inspection workflow
│   ├── MarkdownWorkflowGenerator   # Generates actionable debugging docs
│   └── TraceScenarioExtractor      # Extracts test scenarios from traces
└── mcp/                      # MCP protocol layer
    ├── McpTool                     # Interface for all tools
    ├── McpProtocolHandler          # JSON-RPC 2.0 request router
    ├── TraceListErrorTracesTool    # trace.list_error_traces
    ├── TraceInspectErrorTraceTool  # trace.inspect_error_trace
    ├── TraceExtractScenarioTool    # trace.extract_scenario
    ├── LogSearchTool               # log.search_logs (with summarize mode)
    └── LogCorrelateTool            # log.correlate
```

### Key Design Patterns

1. **Tool Interface Pattern**: All MCP tools implement `McpTool` with `name()`, `description()`, `inputSchema()`, `execute()`. Protocol handler routes by tool name.

2. **Domain Records**: All domain objects are immutable Java records with validation in compact constructors. No Lombok.

3. **Dependency Wiring**: Manual constructor injection in `DatadogMcpServer.main()`. No framework.

4. **Pattern Extraction**: `LogGroupSummary.extractPattern()` normalizes log messages by replacing UUIDs, timestamps, IPs, and numeric IDs with placeholders for grouping.

5. **Scenario Extraction**: `TraceScenarioExtractor` classifies spans by type (HTTP, DB, Cache, Queue, Internal) using tag prefixes and extracts structured test data.

### Adding a New Tool

1. Create class implementing `McpTool` in `mcp/` package
2. Implement `name()`, `description()`, `inputSchema()`, `execute()`
3. Add to tool list in `DatadogMcpServer.main()`

### Test Naming Convention

```java
void whenDoingSomething_givenSomeScenario_shouldDoOrHappenSomething()
```

## Technical Notes

- Java 21 with preview features enabled (`--enable-preview`)
- Uses Datadog API Client SDK v2.50.0
- Shaded JAR via maven-shade-plugin (single deployable artifact)
- No Spring, no Lombok - pure Java with Jackson for JSON
