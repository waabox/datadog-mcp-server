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

You need two keys from Datadog: an **API Key** and an **Application Key**.

### Step 1: Get Your API Key

1. Log in to [Datadog](https://app.datadoghq.com)
2. Go to **Organization Settings** (bottom-left menu)
3. Navigate to **ACCESS** → **API Keys**
4. Click **+ New Key**
5. Give it a name (e.g., `MCP Server`)
6. Copy the key value (32-character hex string like `abcd1234abcd1234abcd1234abcd1234`)

> **Note:** The API Key is different from the Key ID. Copy the actual key value, not the UUID.

### Step 2: Get Your Application Key

1. In **Organization Settings**, go to **ACCESS** → **Application Keys**
2. Click **+ New Key**
3. Give it a name (e.g., `MCP Server`)
4. **Important:** Configure the required scopes:
   - `apm_read` - Read APM traces and spans
   - `logs_read_data` - Read log data
5. Copy the key value (40-character string like `abcd1234abcd1234abcd1234abcd1234abcd1234`)

> **Note:** Copy the actual key value from the KEY column, not the Key ID.

### Step 3: Configure Environment Variables

Set these in your MCP configuration:

| Variable | Description | Example |
|----------|-------------|---------|
| `DATADOG_API_KEY` | Your API Key (32 chars) | `abcd1234abcd1234abcd1234abcd1234` |
| `DATADOG_APP_KEY` | Your Application Key (40 chars) | `abcd1234abcd1234abcd1234abcd1234abcd1234` |
| `DATADOG_SITE` | Your Datadog site (optional) | `datadoghq.com` |

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

Add this to your `~/.claude.json`:

```json
{
  "mcpServers": {
    "waabox-datadog-mcp": {
      "command": "java",
      "args": ["-jar", "/Users/YOUR_USER/.claude/apps/mcp/datadog-mcp-server-1.1.0.jar"],
      "env": {
        "DATADOG_API_KEY": "your-api-key-here",
        "DATADOG_APP_KEY": "your-app-key-here",
        "DATADOG_SITE": "datadoghq.com"
      }
    }
  }
}
```

Replace `YOUR_USER` with your username and update the keys with your actual Datadog credentials.

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
