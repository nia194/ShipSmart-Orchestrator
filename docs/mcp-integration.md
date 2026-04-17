# ShipSmart-MCP Integration Guide (Java / Spring)

The **ShipSmart-MCP** service (separate repo: `ShipSmart-MCP`) exposes
ShipSmart's shipping tools over a small HTTP contract derived from the Model
Context Protocol. The Python API already consumes it for its advisor and
orchestration flows; this document describes how the Java orchestrator will
call the same contract when its AI features land.

> **Today:** no Java runtime code calls MCP yet. The config hooks below are in
> place so the integration can ship behind an env-var switch without a config
> refactor.

## Configuration

Configured via `application.yml` / env vars:

| Property                 | Env var                 | Purpose                                              |
| ------------------------ | ----------------------- | ---------------------------------------------------- |
| `shipsmart.mcp.base-url` | `SHIPSMART_MCP_URL`     | HTTPS URL of the MCP service.                        |
| `shipsmart.mcp.api-key`  | `SHIPSMART_MCP_API_KEY` | Shared secret for `X-MCP-Api-Key`. Optional locally. |

Local dev: `http://localhost:8001` with an empty API key.
Production (Render): `https://shipsmart-mcp.onrender.com` with the API key set
in the Render dashboard (also see `render.yaml`).

If `shipsmart.mcp.base-url` is empty, any AI feature that depends on it should
short-circuit to a clear 503 / disabled state — never silently fall back to a
mock.

## HTTP contract

All endpoints are JSON. When `SHIPSMART_MCP_API_KEY` is configured on the MCP
server, every `POST /tools/*` request **must** send `X-MCP-Api-Key: <key>` or
the server returns 401.

| Method | Path          | Body                                  | Returns                                 |
| ------ | ------------- | ------------------------------------- | --------------------------------------- |
| GET    | `/health`     | –                                     | `{ status, service, version, tools }`   |
| GET    | `/`           | –                                     | service discovery                       |
| POST   | `/tools/list` | –                                     | `{ tools: [MCPToolDefinition] }`        |
| POST   | `/tools/call` | `{ name, arguments }`                 | `{ success, content: [...], error? }`   |

**`MCPToolDefinition`** is `{ name, description, input_schema }`. `input_schema`
is a JSON Schema object with `type: "object"`, `properties`, and `required`.

**`/tools/call` response** returns a list of content blocks. Text blocks carry:
- The tool's `data` payload as indented JSON (success case), _or_
- `Error: <message>` (failure case).
- An optional trailing block `Metadata: <json>` with provider info
  (e.g. `{"provider": "fedex", "tool": "get_quote_preview"}`).

Today the server ships two tools:
- `validate_address` → `{ is_valid, normalized_address?, deliverable?, issues? }`
- `get_quote_preview` → `{ billable_weight_lbs, services: [...], disclaimer }`

## Sample `WebClient` (when AI features land)

```java
// Suggested wiring — drop into a @Configuration class when adding AI features.
@Configuration
public class McpClientConfig {

    @Bean
    public WebClient mcpWebClient(
            @Value("${shipsmart.mcp.base-url:}") String baseUrl,
            @Value("${shipsmart.mcp.api-key:}") String apiKey) {

        if (baseUrl.isBlank()) {
            // Fail fast at bean creation rather than on first tool call.
            throw new IllegalStateException(
                "shipsmart.mcp.base-url is not configured; set SHIPSMART_MCP_URL.");
        }

        WebClient.Builder b = WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        if (!apiKey.isBlank()) {
            b.defaultHeader("X-MCP-Api-Key", apiKey);
        }
        return b.build();
    }
}
```

```java
// Tool call — use Jackson to decode {success, content, error}.
public Mono<JsonNode> callTool(String name, Map<String, Object> arguments) {
    Map<String, Object> body = Map.of("name", name, "arguments", arguments);
    return mcpWebClient.post()
        .uri("/tools/call")
        .bodyValue(body)
        .retrieve()
        .bodyToMono(JsonNode.class);
}
```

The first `content[].text` block is the tool's data (parse it as JSON). The
`"Metadata: ..."` block, when present, is a second text block whose payload is
prefixed with the literal string `"Metadata: "`.

## Related

- **Python consumer** (reference implementation):
  `ShipSmart-API/app/services/mcp_client.py` (`McpClient`, `RemoteTool`,
  `RemoteToolRegistry`). The Java client can mirror its surface.
- **MCP server code & test fixtures**: `ShipSmart-MCP` repo.
