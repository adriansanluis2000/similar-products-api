# 0013. Similar products as an MCP tool

- **Status:** Accepted
- **Date:** 2026-10-20

## Context

Shopping assistants and other AI agents increasingly reach business capabilities through tools rather than screens. The Model Context Protocol (MCP) is the open standard for exposing tools to agents: a client discovers the tools of a server, with descriptions and schemas meant for a model, and calls them. Similar products are a natural tool for an agent that suggests alternatives to a product. The architecture (ADR 0003) claims that new channels can be added without touching the core, and this is a chance to prove it.

## Decision

The service exposes `get_similar_products(productId)` over MCP, as a second inbound adapter (`infrastructure.adapter.in.mcp`) that calls the same `GetSimilarProductsUseCase` as the REST adapter. `domain` and `application` do not change.

- **Server:** Spring AI's MCP server for Spring MVC (2.0.1, built on the same Spring Boot version), declared with `@McpTool`.
- **Transport:** streamable HTTP at `/mcp`, on the same port as the API. The protocol is set explicitly, because Spring AI otherwise falls back to the older SSE transport.
- **Tool:** a description written for the model (what it returns, in which order, when to use it, that the list may be shorter than usual), a structured output with its JSON schema, and read-only and idempotent hints. Unknown products, malformed ids and an unavailable catalog are returned as tool errors with a readable message, so the agent can correct itself or retry.
- **Off by default:** `similar-products.mcp.enabled=false`. The same property turns on Spring AI's server and the tool, so the REST API and the load test are unaffected unless it is switched on.

## Alternatives

- **Let agents call the REST API.** Possible with an OpenAPI-based tool, but every client has to wire it, and agents get HTTP errors instead of messages meant for them.
- **A separate MCP service calling the REST API.** One more deployable and one more network hop, for logic that already lives in the use case.
- **Stdio transport.** Only for servers launched by the client as a local process; it cannot share the running service.
- **The stateless variant of streamable HTTP.** No sessions, so it scales across replicas without sticky routing. The tool keeps no state, so switching is a property away when the service runs replicated.

## Consequences

- A new channel was added without touching the core: the use case, its budget and its business rules apply to agents as they do to the REST API.
- The endpoint has no authentication. It must stay off, or sit behind authentication, wherever the service is reachable from outside.
- Spring AI adds dependencies to the image even when the server is off.
