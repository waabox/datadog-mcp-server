# Datadog Trace Diagnostic MCP Server

[![Build](https://github.com/waabox/datadog-mcp-server/actions/workflows/ci.yml/badge.svg)](https://github.com/waabox/datadog-mcp-server/actions)
[![Release](https://img.shields.io/github/v/tag/waabox/datadog-mcp-server?label=release)](https://github.com/waabox/datadog-mcp-server/tags)
[![License](https://img.shields.io/github/license/waabox/datadog-mcp-server)](LICENSE)
[![Java](https://img.shields.io/badge/java-21%2B-orange)](https://adoptium.net/)
![MCP Server](https://img.shields.io/badge/MCP-server-black)
![Datadog APM](https://img.shields.io/badge/Datadog-APM%20traces-purple)

An MCP (Model Context Protocol) server that lets AI assistants like Claude query Datadog APM for error traces and generate actionable debugging workflows.

---

## Quick Install

Requirements: **Java 21+**, **Maven 3.8+**

```bash
curl -fsSL https://raw.githubusercontent.com/waabox/datadog-mcp-server/main/install.sh | bash
```

The installer will:

1. Download the latest stable version from git  
2. Build the project (skips tests for speed)  
3. Copy the JAR to `~/.claude/apps/mcp/`  
4. Configure Claude Code's `mcp.json`  
5. Ask for your Datadog API keys (optional)  

---

## What Does This Server Do?

### 1. List Error Traces (`trace.list_error_traces`)
Search Datadog APM for traces matching filters.

### 2. Inspect Error Trace (`trace.inspect_error_trace`)
Deep-dive into spans, logs, stack traces, and generate a full diagnostic markdown.

---

## Datadog Permissions Required

| Endpoint | Permission |
|---------|------------|
| `POST /api/v2/spans/events/search` | `apm_read` |
| `GET /api/v1/trace/{traceId}` | `apm_read` |
| `POST /api/v2/logs/events/search` | `logs_read_data` |

---

## Creating Your Datadog Keys

### API Key  
Create via **Organization Settings → API Keys**.

### Application Key  
Requires scopes:  
- `apm_read`  
- `logs_read_data`  

---

## Installation

### Build from Source

```bash
git clone https://github.com/waabox/datadog-mcp-server.git
cd datadog-mcp-server
mvn clean package
```

The JAR appears in:

```
target/datadog-mcp-server-1.0.0-SNAPSHOT.jar
```

---

## MCP Configuration (Claude Code)

```json
{
  "mcpServers": {
    "datadog-traces": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/datadog.jar"],
      "env": {
        "DATADOG_API_KEY": "your-key",
        "DATADOG_APP_KEY": "your-key"
      }
    }
  }
}
```

---

## Datadog Sites

| Region | Site |
|--------|------|
| US1 | datadoghq.com |
| US3 | us3.datadoghq.com |
| US5 | us5.datadoghq.com |
| EU | datadoghq.eu |
| AP1 | ap1.datadoghq.com |

---

## License

MIT License  
