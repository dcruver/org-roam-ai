package com.dcruver.orgroam.nlp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Client for org-roam-mcp server providing semantic search and note operations.
 *
 * The MCP server wraps Emacs org-roam-semantic functionality, providing:
 * - Semantic search using vector embeddings (Ollama/nomic-embed-text)
 * - Contextual search with full note content
 * - Note creation with auto-embedding generation
 *
 * This client uses HTTP JSON-RPC protocol to communicate with the MCP server.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "gardener.mcp.enabled", havingValue = "true", matchIfMissing = true)
public class OrgRoamMcpClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final int timeoutMs;

    public OrgRoamMcpClient(McpProperties mcpProperties) {
        this.baseUrl = mcpProperties.getBaseUrl();
        this.timeoutMs = mcpProperties.getTimeoutMs();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(timeoutMs))
            .build();
        this.objectMapper = new ObjectMapper();

        log.info("OrgRoamMcpClient initialized with base URL: {} (timeout: {}ms)",
            baseUrl, timeoutMs);
    }

    /**
     * Perform semantic search using vector embeddings.
     *
     * @param query Search query text
     * @param limit Maximum number of results
     * @param threshold Minimum similarity threshold (0.0-1.0)
     * @return List of semantically similar notes
     */
    public List<SemanticSearchResult> semanticSearch(String query, int limit, double threshold) {
        try {
            McpRequest request = McpRequest.builder()
                .jsonrpc("2.0")
                .id(1)
                .method("tools/call")
                .params(Map.of(
                    "name", "semantic_search",
                    "arguments", Map.of(
                        "query", query,
                        "limit", limit,
                        "threshold", threshold
                    )
                ))
                .build();

            String responseBody = sendRequest(request);
            McpResponse<SemanticSearchResponse> response = objectMapper.readValue(
                responseBody,
                objectMapper.getTypeFactory().constructParametricType(
                    McpResponse.class,
                    SemanticSearchResponse.class
                )
            );

            if (response.getError() != null) {
                log.error("MCP semantic search error: {}", response.getError());
                return List.of();
            }

            return response.getResult().getNotes();

        } catch (Exception e) {
            log.error("Failed to perform semantic search via MCP", e);
            return List.of();
        }
    }

    /**
     * Perform contextual search (keyword-based with full context).
     *
     * @param query Search query
     * @param limit Maximum results
     * @return List of matching notes with content
     */
    public List<ContextualSearchResult> contextualSearch(String query, int limit) {
        try {
            McpRequest request = McpRequest.builder()
                .jsonrpc("2.0")
                .id(1)
                .method("tools/call")
                .params(Map.of(
                    "name", "contextual_search",
                    "arguments", Map.of(
                        "query", query,
                        "limit", limit
                    )
                ))
                .build();

            String responseBody = sendRequest(request);
            McpResponse<ContextualSearchResponse> response = objectMapper.readValue(
                responseBody,
                objectMapper.getTypeFactory().constructParametricType(
                    McpResponse.class,
                    ContextualSearchResponse.class
                )
            );

            if (response.getError() != null) {
                log.error("MCP contextual search error: {}", response.getError());
                return List.of();
            }

            return response.getResult().getNotes();

        } catch (Exception e) {
            log.error("Failed to perform contextual search via MCP", e);
            return List.of();
        }
    }

    /**
     * Check if MCP server is available.
     */
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;

        } catch (Exception e) {
            log.debug("MCP server not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Send JSON-RPC request to MCP server.
     */
    private String sendRequest(McpRequest request) throws Exception {
        String requestBody = objectMapper.writeValueAsString(request);

        log.debug("Sending MCP request: {}", requestBody);

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl))
            .timeout(Duration.ofMillis(timeoutMs))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("MCP request failed with status: " + response.statusCode());
        }

        log.debug("Received MCP response: {}", response.body());
        return response.body();
    }

    // ==================== DTOs ====================

    @Data
    @lombok.Builder
    private static class McpRequest {
        private String jsonrpc;
        private int id;
        private String method;
        private Object params;
    }

    @Data
    private static class McpResponse<T> {
        private String jsonrpc;
        private Integer id;
        private T result;
        private McpError error;
    }

    @Data
    private static class McpError {
        private int code;
        private String message;
    }

    @Data
    public static class SemanticSearchResponse {
        private List<SemanticSearchResult> notes;
    }

    @Data
    public static class SemanticSearchResult {
        private String file;
        private String title;
        private double similarity;
        @JsonProperty("node_id")
        private String nodeId;
    }

    @Data
    public static class ContextualSearchResponse {
        private List<ContextualSearchResult> notes;
    }

    @Data
    public static class ContextualSearchResult {
        private String file;
        private String title;
        private String content;
        private List<String> tags;
        private List<String> backlinks;
        @JsonProperty("node_id")
        private String nodeId;
    }

    /**
     * Configuration properties for MCP integration.
     */
    @Configuration
    @ConfigurationProperties(prefix = "gardener.mcp")
    @Data
    public static class McpProperties {
        private boolean enabled = true;
        private String baseUrl = "http://localhost:8000";
        private int timeoutMs = 30000;
    }
}
