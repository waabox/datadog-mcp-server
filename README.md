# Datadog Trace Diagnostic MCP Server

[![Build](https://github.com/waabox/datadog-mcp-server/actions/workflows/ci.yml/badge.svg)](https://github.com/waabox/datadog-mcp-server/actions)
[![Release](https://img.shields.io/github/v/tag/waabox/datadog-mcp-server?label=release)](https://github.com/waabox/datadog-mcp-server/tags)
[![License](https://img.shields.io/github/license/waabox/datadog-mcp-server)](LICENSE)
[![Java](https://img.shields.io/badge/java-21%2B-orange)](https://adoptium.net/)
![MCP Server](https://img.shields.io/badge/MCP-server-black)
![Datadog APM](https://img.shields.io/badge/Datadog-APM%20traces-purple)

An MCP (Model Context Protocol) server that lets AI assistants like Claude query Datadog APM for error traces, search logs, and generate actionable debugging workflows.

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

## Available Tools

| Tool | Description |
|------|-------------|
| `trace.list_error_traces` | List error traces for a service within a time window |
| `trace.inspect_error_trace` | Deep-dive into a specific trace with spans, logs, and diagnostic workflow |
| `trace.extract_scenario` | Extract structured test scenario from a trace for debugging and unit test generation |
| `log.search_logs` | Search logs with filters and optional pattern summarization |
| `log.correlate` | Correlate logs with a specific trace ID |

---

## Usage Examples

### 1. List Error Traces (`trace.list_error_traces`)

Search for error traces in a specific service.

**Natural Language Prompts:**

```
"List error traces for payment-service from the last hour"

"Show me the last 10 errors in order-service today"

"Find errors in api-gateway between 2pm and 3pm in staging environment"

"What errors occurred in user-service yesterday?"
```

**Parameters:**

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `service` | Yes | - | Service name in Datadog |
| `from` | Yes | - | ISO-8601 start timestamp |
| `to` | Yes | - | ISO-8601 end timestamp |
| `env` | No | `prod` | Environment (prod, staging, dev) |
| `limit` | No | `20` | Maximum traces to return (max 100) |

**Example Response:**

```json
{
  "success": true,
  "count": 3,
  "traces": [
    {
      "traceId": "abc123def456789",
      "service": "payment-service",
      "resourceName": "POST /api/v1/payments",
      "errorMessage": "Connection timeout to payment gateway",
      "timestamp": "2024-01-15T14:32:15Z",
      "duration": "2.45s"
    }
  ]
}
```

---

### 2. Inspect Error Trace (`trace.inspect_error_trace`)

Get detailed diagnostic information about a specific trace, including all spans, logs, and an actionable debugging workflow.

**Natural Language Prompts:**

```
"Inspect trace abc123def456789 in payment-service"

"Analyze the error trace xyz789 and help me debug it"

"Show me the full details of trace abc123 from order-service in the last hour"

"Generate a diagnostic workflow for trace def456 in checkout-service"
```

**Parameters:**

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `traceId` | Yes | - | The trace ID to inspect |
| `service` | Yes | - | Service name in Datadog |
| `from` | Yes | - | ISO-8601 start timestamp |
| `to` | Yes | - | ISO-8601 end timestamp |
| `env` | No | `prod` | Environment |

**Example Response:**

```json
{
  "success": true,
  "traceId": "abc123def456789",
  "service": "payment-service",
  "involvedServices": ["api-gateway", "payment-service", "fraud-detection", "notification-service"],
  "totalErrors": 2,
  "isDistributedError": true,
  "traceSummary": {
    "duration": "1.24s",
    "spanCount": 28,
    "errorSpanCount": 2,
    "startTime": "2024-01-15T14:32:15Z"
  },
  "serviceErrors": [
    {
      "serviceName": "payment-service",
      "errorCount": 1,
      "primaryError": "PaymentGatewayException: Connection refused",
      "errorTypes": ["PaymentGatewayException"]
    },
    {
      "serviceName": "fraud-detection",
      "errorCount": 1,
      "primaryError": "TimeoutException: Request timed out after 5000ms",
      "errorTypes": ["TimeoutException"]
    }
  ],
  "workflow": "# Diagnostic Workflow\n\n## Error Summary\n..."
}
```

---

### 3. Extract Test Scenario (`trace.extract_scenario`)

Extract a structured test scenario from a trace for debugging and unit test generation. This tool analyzes the execution flow and identifies the exact location in the code where the error occurred.

**Natural Language Prompts:**

```
"Extract the scenario from trace abc123 in order-service"

"Analyze trace xyz789 and help me create a unit test"

"Show me the execution flow and error location for trace def456"

"What's the test scenario for this failing trace?"
```

**Parameters:**

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `traceId` | Yes | - | The trace ID to analyze |
| `service` | Yes | - | Service name in Datadog |
| `from` | Yes | - | ISO-8601 start timestamp |
| `to` | Yes | - | ISO-8601 end timestamp |
| `env` | No | `prod` | Environment |

**Example Response:**

```json
{
  "success": true,
  "traceId": "abc123def456789",
  "entryPoint": {
    "method": "POST",
    "path": "/api/orders",
    "body": "{\"product_id\": \"123\", \"quantity\": 5}"
  },
  "executionFlow": [
    {
      "order": 1,
      "spanId": "span1",
      "service": "api-gateway",
      "operation": "POST /api/orders",
      "type": "http",
      "durationMs": 1240
    },
    {
      "order": 2,
      "spanId": "span2",
      "parentSpanId": "span1",
      "service": "order-service",
      "operation": "OrderController.createOrder",
      "type": "internal",
      "durationMs": 1100
    },
    {
      "order": 3,
      "spanId": "span3",
      "parentSpanId": "span2",
      "service": "order-service",
      "operation": "SELECT * FROM products WHERE id = ?",
      "type": "db",
      "durationMs": 45
    },
    {
      "order": 4,
      "spanId": "span4",
      "parentSpanId": "span2",
      "service": "order-service",
      "operation": "OrderService.validateStock",
      "type": "internal",
      "durationMs": 12,
      "isError": true
    }
  ],
  "errorContext": {
    "service": "order-service",
    "operation": "OrderService.validateStock",
    "exceptionType": "InsufficientStockException",
    "message": "Not enough stock: requested 5, available 2",
    "location": {
      "className": "com.example.order.OrderService",
      "methodName": "validateStock",
      "fileName": "OrderService.java",
      "lineNumber": 142
    },
    "spanTags": {
      "product_id": "123",
      "quantity": "5"
    }
  },
  "relevantData": {
    "product_id": "123",
    "quantity": "5",
    "user_id": "456"
  },
  "suggestedTestScenario": {
    "given": "Data: product_id=123, quantity=5",
    "when": "Request: POST /api/orders with body",
    "then": "InsufficientStockException is thrown with message 'Not enough stock: requested 5, available 2'"
  },
  "involvedServices": ["api-gateway", "order-service"],
  "stepCount": 4,
  "totalDurationMs": 1240
}
```

**Workflow: From Trace to Unit Test**

```
1. User: "Extract scenario from trace abc123 in order-service"

2. Claude analyzes the trace and responds:
   "Based on the trace, here's what happened:

   Entry Point: POST /api/orders with body {"product_id": "123", "quantity": 5}

   Execution Flow:
   1. api-gateway → POST /api/orders (1240ms)
   2. order-service → OrderController.createOrder (1100ms)
   3. order-service → SELECT * FROM products WHERE id = ? (45ms)
   4. order-service → OrderService.validateStock ❌ (12ms)

   Error: InsufficientStockException at OrderService.java:142
   Message: 'Not enough stock: requested 5, available 2'

   Where is the order-service codebase? I can look at the code and suggest a unit test."

3. User provides codebase path

4. Claude reads the code and proposes:
   "@Test
   void whenValidatingStock_givenQuantityExceedsAvailable_shouldThrowException() {
       // Given
       Product product = new Product("123", "Test Product", 2);
       OrderRequest request = new OrderRequest("123", 5);

       // When / Then
       InsufficientStockException ex = assertThrows(
           InsufficientStockException.class,
           () -> orderService.validateStock(request, product)
       );

       assertEquals("Not enough stock: requested 5, available 2", ex.getMessage());
   }"
```

---

### 4. Search Logs (`log.search_logs`)

Search for logs with various filters and optional pattern summarization.

**Natural Language Prompts:**

```
"Search ERROR logs in payment-service from the last hour"

"Find all logs mentioning 'timeout' in order-service today"

"Show me WARNING and ERROR logs in api-gateway between 2pm and 4pm"

"Summarize the log patterns in notification-service from yesterday"

"Search logs with query 'user_id:12345' in user-service"
```

**Parameters:**

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `service` | Yes | - | Service name in Datadog |
| `from` | Yes | - | ISO-8601 start timestamp |
| `to` | Yes | - | ISO-8601 end timestamp |
| `env` | No | `prod` | Environment |
| `level` | No | - | Log level filter (ERROR, WARN, INFO, DEBUG) |
| `query` | No | - | Additional Datadog query string |
| `limit` | No | `100` | Maximum logs to return |
| `outputMode` | No | `full` | `full` returns all logs, `summarize` groups by pattern |
| `maxMessageLength` | No | `500` | Max message length in full mode |

**Example Response (full mode):**

```json
{
  "success": true,
  "count": 45,
  "logs": [
    {
      "timestamp": "2024-01-15T14:32:15Z",
      "level": "ERROR",
      "service": "payment-service",
      "message": "Failed to process payment: Connection timeout to gateway.example.com",
      "host": "prod-payment-01",
      "traceId": "abc123def456789"
    }
  ]
}
```

**Example Response (summarize mode):**

```json
{
  "success": true,
  "totalLogs": 150,
  "uniquePatterns": 8,
  "groups": [
    {
      "pattern": "Failed to process payment: Connection timeout to *",
      "level": "ERROR",
      "count": 45,
      "firstOccurrence": "2024-01-15T14:00:00Z",
      "lastOccurrence": "2024-01-15T14:45:00Z"
    },
    {
      "pattern": "Request completed successfully in * ms",
      "level": "INFO",
      "count": 89,
      "firstOccurrence": "2024-01-15T14:00:00Z",
      "lastOccurrence": "2024-01-15T14:59:00Z"
    }
  ]
}
```

---

### 5. Correlate Logs with Traces (`log.correlate`)

Find all logs associated with a specific trace ID and optionally include trace details.

**Natural Language Prompts:**

```
"Correlate logs for trace abc123 in payment-service"

"Show me all logs related to trace xyz789 from the last hour"

"Get trace details and associated logs for trace def456 in order-service"

"Find logs for trace abc123 without trace details"
```

**Parameters:**

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `traceId` | Yes | - | The trace ID to search for |
| `service` | Yes | - | Service name in Datadog |
| `from` | Yes | - | ISO-8601 start timestamp |
| `to` | Yes | - | ISO-8601 end timestamp |
| `env` | No | `prod` | Environment |
| `includeTrace` | No | `true` | Include trace summary in response |

**Example Response:**

```json
{
  "success": true,
  "traceId": "abc123def456789",
  "trace": {
    "service": "payment-service",
    "resourceName": "POST /api/v1/payments",
    "duration": "542.00ms",
    "spanCount": 12,
    "services": ["api-gateway", "payment-service", "database-service"],
    "hasErrors": true
  },
  "logs": [
    {
      "timestamp": "2024-01-15T14:32:15.123Z",
      "level": "INFO",
      "message": "Processing payment request for order #12345"
    },
    {
      "timestamp": "2024-01-15T14:32:15.456Z",
      "level": "ERROR",
      "message": "Payment gateway returned error: insufficient_funds",
      "attributes": {
        "order_id": "12345",
        "amount": "99.99"
      }
    }
  ],
  "logCount": 2
}
```

---

## Common Workflows

### Debugging a Production Incident

```
1. "List error traces for payment-service from the last 30 minutes"
   → Identifies recent errors and their trace IDs

2. "Inspect trace abc123 in payment-service from the last hour"
   → Gets full diagnostic with spans, logs, and workflow

3. "Correlate logs for trace abc123 in payment-service"
   → Shows all logs associated with this specific request
```

### Investigating Intermittent Errors

```
1. "Search ERROR logs in order-service from today, summarize patterns"
   → Groups similar errors to identify patterns

2. "List error traces for order-service from the last 6 hours with limit 50"
   → Gets more traces to analyze the pattern

3. "Inspect trace xyz789 in order-service"
   → Deep dive into a specific occurrence
```

### Cross-Service Error Analysis

```
1. "Inspect trace abc123 in api-gateway from the last hour"
   → Shows all services involved in the distributed trace

2. "Search ERROR logs in downstream-service with query 'trace_id:abc123'"
   → Finds related errors in downstream services
```

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
target/datadog-mcp-server-1.3.0.jar
```

---

## MCP Configuration (Claude Code)

Add this to your `~/.claude.json`:

```json
{
  "mcpServers": {
    "waabox-datadog-mcp": {
      "command": "java",
      "args": ["-jar", "/Users/YOUR_USER/.claude/apps/mcp/datadog-mcp-server.jar"],
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
