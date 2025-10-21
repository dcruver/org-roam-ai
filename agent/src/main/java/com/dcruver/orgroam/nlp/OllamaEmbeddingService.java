package com.dcruver.orgroam.nlp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for generating embeddings using Ollama via Spring AI.
 * Embabel creates EmbeddingModel beans automatically from configured models.
 */
@Service
@Slf4j
public class OllamaEmbeddingService {

    private final EmbeddingModel embeddingModel;

    /**
     * Inject the EmbeddingModel bean created by Embabel.
     * Embabel's @EnableAgents(localModels = {LocalModels.OLLAMA}) automatically
     * creates EmbeddingModel beans from available Ollama models.
     */
    public OllamaEmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        log.info("OllamaEmbeddingService initialized with EmbeddingModel: {}", embeddingModel.getClass().getSimpleName());
    }

    /**
     * Generate embedding for a single text
     */
    public List<Double> embed(String text) {
        if (text == null || text.isBlank()) {
            log.warn("Cannot generate embedding for empty text");
            return List.of();
        }

        try {
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
            if (response.getResults().isEmpty()) {
                log.warn("No embedding generated for text");
                return List.of();
            }

            // Convert float[] to List<Double>
            float[] floatArray = response.getResults().get(0).getOutput();
            List<Double> result = new java.util.ArrayList<>(floatArray.length);
            for (float f : floatArray) {
                result.add((double) f);
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to generate embedding", e);
            return List.of();
        }
    }

    /**
     * Generate embeddings for multiple texts
     */
    public List<List<Double>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        try {
            EmbeddingResponse response = embeddingModel.embedForResponse(texts);
            return response.getResults().stream()
                .map(result -> {
                    float[] floatArray = result.getOutput();
                    List<Double> doubles = new java.util.ArrayList<>(floatArray.length);
                    for (float f : floatArray) {
                        doubles.add((double) f);
                    }
                    return doubles;
                })
                .toList();
        } catch (Exception e) {
            log.error("Failed to generate batch embeddings", e);
            return List.of();
        }
    }

    /**
     * Calculate cosine similarity between two embeddings
     */
    public double cosineSimilarity(List<Double> embedding1, List<Double> embedding2) {
        if (embedding1.size() != embedding2.size()) {
            throw new IllegalArgumentException("Embeddings must have same dimension");
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < embedding1.size(); i++) {
            dotProduct += embedding1.get(i) * embedding2.get(i);
            norm1 += embedding1.get(i) * embedding1.get(i);
            norm2 += embedding2.get(i) * embedding2.get(i);
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}
