# AGENTS.md

This document provides guidelines for agentic coding contributors to the crypto-scout-analyst module.

## Project Overview

Java 25 Maven microservice for real-time cryptocurrency market analysis. Consumes data from RabbitMQ Streams, applies technical analysis using jcryptolib indicators, and provides analytical capabilities. Built on ActiveJ for fully async I/O.

## MCP Server Configuration

This module uses the **Context7 MCP server** for enhanced code intelligence and documentation retrieval.

### Available MCP Tools

When working with this codebase, you can use the following MCP tools via the context7 server:

- **resolve-library-id**: Resolve a library name to its Context7 library ID
- **get-library-docs**: Retrieve up-to-date documentation for a library by its ID

### Configuration

The MCP server is configured in `.opencode/package.json`:

```json
{
  "mcp": {
    "context7": {
      "type": "remote",
      "url": "https://mcp.context7.com/mcp",
      "headers": {
        "CONTEXT7_API_KEY": "{env:CONTEXT7_API_KEY}"
      },
      "enabled": true
    }
  }
}
```


