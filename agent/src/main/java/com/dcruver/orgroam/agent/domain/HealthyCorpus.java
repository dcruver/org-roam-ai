package com.dcruver.orgroam.agent.domain;

import com.dcruver.orgroam.domain.CorpusState;
import lombok.Builder;
import lombok.Value;

/**
 * Terminal state: Knowledge base with improved structure.
 * This represents the goal state where:
 * - All formatting is normalized
 * - All embeddings are fresh
 * - Orphans are organized into clusters with hub notes
 * - Knowledge structure is improved
 */
@Value
@Builder
public class HealthyCorpus {
    CorpusState state;

    public int getTotalNotes() {
        return state.getTotalNotes();
    }

    /**
     * Terminal goal achieved - knowledge base is healthy
     * (In a full implementation, this would check actual health metrics)
     */
    public boolean isHealthy() {
        // For now, just return true since reaching this state means we've completed all actions
        return true;
    }

    public CorpusState getState() {
        return state;
    }
}
