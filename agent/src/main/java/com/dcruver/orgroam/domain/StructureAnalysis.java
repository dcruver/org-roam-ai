package com.dcruver.orgroam.domain;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Result of LLM analysis on note structure.
 * Used to determine if note should be split, merged, or left alone.
 */
@Data
@Builder
public class StructureAnalysis {
    /**
     * List of distinct topics detected in the note
     */
    private final List<String> topics;

    /**
     * Internal semantic coherence score (0.0-1.0)
     * Higher = more focused on single topic
     */
    private final double coherence;

    /**
     * Suggested split points if note should be divided
     */
    private final List<SplitPoint> splitPoints;

    /**
     * Estimated token count
     */
    private final int tokenCount;

    /**
     * Whether token count is optimal (300-800 range)
     */
    private final boolean optimalSize;

    /**
     * Whether note is too small (< 300 tokens)
     */
    private final boolean tooSmall;

    /**
     * Whether note is too large (> 800 tokens)
     */
    private final boolean tooLarge;

    /**
     * LLM's overall recommendation
     */
    private final String recommendation;

    /**
     * Check if note has multiple topics
     */
    public boolean hasMultipleTopics() {
        return topics != null && topics.size() > 1;
    }

    /**
     * Check if note should be split based on analysis
     */
    public boolean shouldSplit() {
        return hasMultipleTopics() && splitPoints != null && !splitPoints.isEmpty();
    }

    /**
     * Check if note might be merge candidate (too small or low coherence)
     */
    public boolean mightNeedMerge() {
        return tooSmall || (coherence < 0.5 && !hasMultipleTopics());
    }
}
