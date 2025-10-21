package com.dcruver.orgroam.nlp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Stores and retrieves note embeddings in SQLite.
 */
@Component
@Slf4j
public class EmbeddingStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String embedModel;

    public EmbeddingStore(
        DataSource dataSource,
        ObjectMapper objectMapper,
        @Value("${spring.ai.ollama.embedding.options.model}") String embedModel
    ) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
        this.embedModel = embedModel;
    }

    @PostConstruct
    public void init() {
        // Create table if not exists
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS embeddings (
                note_id TEXT PRIMARY KEY,
                chunk_hash TEXT NOT NULL,
                model TEXT NOT NULL,
                embedding_json TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                content_preview TEXT
            )
            """);

        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_note_model
            ON embeddings(note_id, model)
            """);

        log.info("Initialized embedding store");
    }

    /**
     * Store embedding for a note
     */
    public void store(String noteId, String chunkHash, List<Double> embedding, String contentPreview) {
        try {
            String embeddingJson = objectMapper.writeValueAsString(embedding);
            long timestamp = Instant.now().getEpochSecond();

            jdbcTemplate.update(
                "INSERT OR REPLACE INTO embeddings (note_id, chunk_hash, model, embedding_json, created_at, content_preview) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
                noteId, chunkHash, embedModel, embeddingJson, timestamp, contentPreview
            );

            log.debug("Stored embedding for note {}", noteId);
        } catch (Exception e) {
            log.error("Failed to store embedding for note {}", noteId, e);
        }
    }

    /**
     * Retrieve embedding for a note
     */
    public Optional<StoredEmbedding> retrieve(String noteId) {
        try {
            List<StoredEmbedding> results = jdbcTemplate.query(
                "SELECT * FROM embeddings WHERE note_id = ? AND model = ?",
                new EmbeddingRowMapper(),
                noteId, embedModel
            );

            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            log.error("Failed to retrieve embedding for note {}", noteId, e);
            return Optional.empty();
        }
    }

    /**
     * Retrieve all embeddings
     */
    public List<StoredEmbedding> retrieveAll() {
        return jdbcTemplate.query(
            "SELECT * FROM embeddings WHERE model = ?",
            new EmbeddingRowMapper(),
            embedModel
        );
    }

    /**
     * Check if embedding exists and is fresh
     */
    public boolean isFresh(String noteId, String chunkHash, int maxAgeDays) {
        try {
            List<StoredEmbedding> results = jdbcTemplate.query(
                "SELECT * FROM embeddings WHERE note_id = ? AND chunk_hash = ? AND model = ?",
                new EmbeddingRowMapper(),
                noteId, chunkHash, embedModel
            );

            if (results.isEmpty()) {
                return false;
            }

            StoredEmbedding stored = results.get(0);
            long ageSeconds = Instant.now().getEpochSecond() - stored.getCreatedAt().getEpochSecond();
            long maxAgeSeconds = maxAgeDays * 24L * 60 * 60;

            return ageSeconds < maxAgeSeconds;
        } catch (Exception e) {
            log.error("Failed to check freshness for note {}", noteId, e);
            return false;
        }
    }

    /**
     * Delete embedding for a note
     */
    public void delete(String noteId) {
        jdbcTemplate.update("DELETE FROM embeddings WHERE note_id = ?", noteId);
        log.debug("Deleted embedding for note {}", noteId);
    }

    /**
     * Row mapper for StoredEmbedding
     */
    private class EmbeddingRowMapper implements RowMapper<StoredEmbedding> {
        @Override
        public StoredEmbedding mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                String embeddingJson = rs.getString("embedding_json");
                List<Double> embedding = objectMapper.readValue(
                    embeddingJson,
                    new TypeReference<List<Double>>() {}
                );

                return StoredEmbedding.builder()
                    .noteId(rs.getString("note_id"))
                    .chunkHash(rs.getString("chunk_hash"))
                    .model(rs.getString("model"))
                    .embedding(embedding)
                    .createdAt(Instant.ofEpochSecond(rs.getLong("created_at")))
                    .contentPreview(rs.getString("content_preview"))
                    .build();
            } catch (IOException e) {
                throw new SQLException("Failed to deserialize embedding", e);
            }
        }
    }

    /**
     * Stored embedding data
     */
    @Data
    @Builder
    public static class StoredEmbedding {
        private String noteId;
        private String chunkHash;
        private String model;
        private List<Double> embedding;
        private Instant createdAt;
        private String contentPreview;
    }
}
