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

            // Handle null result or null notes list
            if (response.getResult() == null || response.getResult().getNotes() == null) {
                log.warn("MCP returned null result or notes list for semantic search");
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
     * Add entry to daily note.
     *
     * @param timestamp Entry timestamp in HH:MM format
     * @param title Entry title
     * @param points Main points or content items
     * @param nextSteps Next steps or action items
     * @param tags Tags for the entry
     * @return Success status
     */
    public boolean addDailyEntry(String timestamp, String title, List<String> points,
                                  List<String> nextSteps, List<String> tags) {
        try {
            McpRequest request = McpRequest.builder()
                .jsonrpc("2.0")
                .id(1)
                .method("tools/call")
                .params(Map.of(
                    "name", "add_daily_entry",
                    "arguments", Map.of(
                        "timestamp", timestamp,
                        "title", title,
                        "points", points,
                        "next_steps", nextSteps != null ? nextSteps : List.of(),
                        "tags", tags != null ? tags : List.of()
                    )
                ))
                .build();

            String responseBody = sendRequest(request);
            McpResponse<Map<String, Object>> response = objectMapper.readValue(
                responseBody,
                objectMapper.getTypeFactory().constructParametricType(
                    McpResponse.class,
                    objectMapper.getTypeFactory().constructType(Map.class)
                )
            );

            if (response.getError() != null) {
                log.error("MCP add daily entry error: {}", response.getError());
                return false;
            }

            log.info("Added entry to daily note: {}", title);
            return true;

        } catch (Exception e) {
            log.error("Failed to add daily entry via MCP", e);
            return false;
        }
    }

    /**
     * Generate embeddings for org-roam notes using org-roam-semantic.
     *
     * @param force Regenerate embeddings even for notes that already have them
     * @return Result with count of embeddings generated
     */
    public GenerateEmbeddingsResult generateEmbeddings(boolean force) {
        try {
            McpRequest request = McpRequest.builder()
                .jsonrpc("2.0")
                .id(1)
                .method("tools/call")
                .params(Map.of(
                    "name", "generate_embeddings",
                    "arguments", Map.of(
                        "force", force
                    )
                ))
                .build();

            String responseBody = sendRequest(request);
            McpResponse<Map<String, Object>> response = objectMapper.readValue(
                responseBody,
                objectMapper.getTypeFactory().constructParametricType(
                    McpResponse.class,
                    objectMapper.getTypeFactory().constructType(Map.class)
                )
            );

            if (response.getError() != null) {
                log.error("MCP generate embeddings error: {}", response.getError());
                return new GenerateEmbeddingsResult(false, 0, "Error: " + response.getError().getMessage());
            }

            // Parse the count from the response (it's embedded in the text response)
            Map<String, Object> result = response.getResult();
            Object contentObj = result.get("content");
            if (contentObj instanceof List) {
                List<?> contentList = (List<?>) contentObj;
                if (!contentList.isEmpty() && contentList.get(0) instanceof Map) {
                    Map<?, ?> textContent = (Map<?, ?>) contentList.get(0);
                    String text = (String) textContent.get("text");

                    // Extract count from text like "✅ Generated 25 embeddings for org-roam notes"
                    int count = 0;
                    if (text != null && text.contains("Generated")) {
                        String[] parts = text.split("Generated")[1].split("embeddings");
                        if (parts.length > 0) {
                            try {
                                count = Integer.parseInt(parts[0].trim());
                            } catch (NumberFormatException e) {
                                log.warn("Could not parse embedding count from response: {}", text);
                            }
                        }
                    }

                    log.info("Generated {} embeddings via MCP", count);
                    return new GenerateEmbeddingsResult(true, count, text);
                }
            }

            return new GenerateEmbeddingsResult(true, 0, "Embeddings generated successfully");

        } catch (Exception e) {
            log.error("Failed to generate embeddings via MCP", e);
            return new GenerateEmbeddingsResult(false, 0, "Exception: " + e.getMessage());
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
        private Boolean success;
        private String query;
        @JsonProperty("search_type")
        private String searchType;
        @JsonProperty("total_found")
        private Integer totalFound;
        @JsonProperty("similarity_cutoff")
        private Double similarityCutoff;
        private List<SemanticSearchResult> notes;
        @JsonProperty("knowledge_context")
        private Map<String, Object> knowledgeContext;
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
     * Result from generate_embeddings operation.
     */
    public static class GenerateEmbeddingsResult {
        private final boolean success;
        private final int count;
        private final String message;

        public GenerateEmbeddingsResult(boolean success, int count, String message) {
            this.success = success;
            this.count = count;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public int getCount() {
            return count;
        }

        public String getMessage() {
            return message;
        }
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
