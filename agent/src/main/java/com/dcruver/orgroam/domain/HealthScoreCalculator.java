package com.dcruver.orgroam.domain;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Calculates health scores for notes based on configurable weights.
 * Weights must sum to 100.
 */
@Component
@ConfigurationProperties(prefix = "gardener.health.weights")
@Data
public class HealthScoreCalculator {
    private int formatting = 10;
    private int provenance = 25;
    private int embeddingsFreshness = 15;
    private int links = 20;
    private int taxonomy = 10;
    private int contradictionsInverse = 10;
    private int stalenessInverse = 10;

    private int embeddingsMaxAgeDays = 90;
    private int staleThresholdDays = 90;

    /**
     * Calculate health score for a note (0-100)
     */
    public int calculateScore(NoteMetadata note) {
        int score = 0;

        // Formatting (10): Has proper structure
        if (note.isFormatOk() && note.isHasProperties() && note.isHasTitle()) {
            score += formatting;
        }

        // Provenance (25): Has proper metadata
        if (note.isProvenanceOk() && note.getCreatedAt() != null && note.getUpdatedAt() != null) {
            score += provenance;
        }

        // Embeddings freshness (15): Has fresh embeddings
        if (note.isEmbeddingsFresh(embeddingsMaxAgeDays)) {
            score += embeddingsFreshness;
        }

        // Links (20): Has good link density (not orphan, has some links)
        if (!note.isOrphan() && note.getLinkCount() > 0) {
            score += links;
        } else if (note.getLinkCount() > 0) {
            // Partial credit if has links but still orphan
            score += links / 2;
        }

        // Taxonomy (10): Tags are canonical
        if (note.isTagsCanonical()) {
            score += taxonomy;
        }

        // Contradictions inverse (10): No contradictions (simplified for now - always full score)
        // TODO: Implement contradiction detection
        score += contradictionsInverse;

        // Staleness inverse (10): Not stale
        if (!note.isStale(staleThresholdDays)) {
            score += stalenessInverse;
        } else {
            // Partial credit based on how stale (linear decay)
            int excessDays = note.getStaleDays() - staleThresholdDays;
            double decay = Math.max(0, 1.0 - (excessDays / (double) staleThresholdDays));
            score += (int) (stalenessInverse * decay);
        }

        return Math.min(100, Math.max(0, score));
    }

    /**
     * Calculate mean health score across corpus
     */
    public double calculateMeanScore(Iterable<NoteMetadata> notes) {
        int count = 0;
        int totalScore = 0;

        for (NoteMetadata note : notes) {
            totalScore += calculateScore(note);
            count++;
        }

        return count > 0 ? (double) totalScore / count : 0.0;
    }

    public void setEmbeddingsMaxAgeDays(int embeddingsMaxAgeDays) {
        this.embeddingsMaxAgeDays = embeddingsMaxAgeDays;
    }

    public void setStaleThresholdDays(int staleThresholdDays) {
        this.staleThresholdDays = staleThresholdDays;
    }
}
