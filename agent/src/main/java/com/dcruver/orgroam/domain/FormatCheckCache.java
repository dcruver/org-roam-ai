package com.dcruver.orgroam.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache for LLM-based format check results.
 * Stores results keyed by (file path, last modified time) to avoid
 * redundant expensive LLM calls for unchanged files.
 *
 * Cache is persisted to disk as JSON and loaded on startup.
 */
@Component
@Slf4j
public class FormatCheckCache {

    @Value("${gardener.cache-dir:${user.home}/.gardener/cache}")
    private String cacheDir;

    @Value("${gardener.format-check-cache-enabled:true}")
    private boolean cacheEnabled;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private Path cacheFile;

    @PostConstruct
    public void init() {
        if (!cacheEnabled) {
            log.info("Format check cache disabled");
            return;
        }

        try {
            Path cacheDirPath = Path.of(cacheDir);
            Files.createDirectories(cacheDirPath);
            cacheFile = cacheDirPath.resolve("format-check-cache.json");

            if (Files.exists(cacheFile)) {
                loadCache();
                log.info("Loaded format check cache with {} entries", cache.size());
            } else {
                log.info("No existing format check cache found, starting fresh");
            }
        } catch (Exception e) {
            log.error("Failed to initialize format check cache: {}", e.getMessage(), e);
            // Continue without cache rather than failing
        }
    }

    @PreDestroy
    public void shutdown() {
        if (cacheEnabled && cacheFile != null) {
            try {
                saveCache();
                log.info("Saved format check cache with {} entries", cache.size());
            } catch (Exception e) {
                log.error("Failed to save format check cache: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Check if we have a cached result for this file.
     * Returns cached result only if file hasn't been modified since the check.
     *
     * @param filePath Path to the file
     * @param lastModified File's current last modified time
     * @return Cached format check result, or null if no valid cache entry
     */
    public Boolean getCachedResult(Path filePath, Instant lastModified) {
        if (!cacheEnabled) {
            return null;
        }

        String key = filePath.toString();
        CacheEntry entry = cache.get(key);

        if (entry == null) {
            log.debug("Cache miss for {}: no entry", filePath.getFileName());
            return null;
        }

        // Check if file has been modified since the cached check
        if (!entry.getLastModified().equals(lastModified)) {
            log.debug("Cache miss for {}: file modified (cached: {}, current: {})",
                filePath.getFileName(), entry.getLastModified(), lastModified);
            cache.remove(key); // Remove stale entry
            return null;
        }

        log.debug("Cache hit for {}: formatOk={}", filePath.getFileName(), entry.isFormatOk());
        return entry.isFormatOk();
    }

    /**
     * Store a format check result in the cache.
     *
     * @param filePath Path to the file
     * @param lastModified File's last modified time at the time of check
     * @param formatOk Result of the format check
     * @param analysisSnippet Short snippet of the LLM analysis (for debugging)
     */
    public void cacheResult(Path filePath, Instant lastModified, boolean formatOk, String analysisSnippet) {
        if (!cacheEnabled) {
            return;
        }

        String key = filePath.toString();
        CacheEntry entry = new CacheEntry();
        entry.setFilePath(key);
        entry.setLastModified(lastModified);
        entry.setFormatOk(formatOk);
        entry.setCheckedAt(Instant.now());
        entry.setAnalysisSnippet(analysisSnippet != null ?
            analysisSnippet.substring(0, Math.min(200, analysisSnippet.length())) : null);

        cache.put(key, entry);
        log.debug("Cached format check for {}: formatOk={}", filePath.getFileName(), formatOk);
    }

    /**
     * Clear the entire cache.
     * Useful for forcing a full re-scan with LLM checks.
     */
    public void clearCache() {
        cache.clear();
        log.info("Cleared format check cache");
    }

    /**
     * Get cache statistics.
     */
    public CacheStats getStats() {
        CacheStats stats = new CacheStats();
        stats.setSize(cache.size());
        stats.setEnabled(cacheEnabled);

        long formatOkCount = cache.values().stream()
            .filter(CacheEntry::isFormatOk)
            .count();

        stats.setFormatOkCount((int) formatOkCount);
        stats.setFormatIssuesCount(cache.size() - (int) formatOkCount);

        return stats;
    }

    /**
     * Load cache from disk.
     */
    @SuppressWarnings("unchecked")
    private void loadCache() throws IOException {
        if (cacheFile == null || !Files.exists(cacheFile)) {
            return;
        }

        String json = Files.readString(cacheFile);
        Map<String, CacheEntry> loaded = objectMapper.readValue(json,
            objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, CacheEntry.class));

        cache.putAll(loaded);
    }

    /**
     * Save cache to disk.
     */
    private void saveCache() throws IOException {
        if (cacheFile == null) {
            return;
        }

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(cache);
        Files.writeString(cacheFile, json);
    }

    /**
     * Cache entry containing format check result and metadata.
     */
    @Data
    public static class CacheEntry {
        private String filePath;
        private Instant lastModified;
        private boolean formatOk;
        private Instant checkedAt;
        private String analysisSnippet;
    }

    /**
     * Cache statistics.
     */
    @Data
    public static class CacheStats {
        private int size;
        private boolean enabled;
        private int formatOkCount;
        private int formatIssuesCount;
    }
}
