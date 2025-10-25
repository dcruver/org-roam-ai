package com.dcruver.orgroam.domain;

import lombok.Builder;
import lombok.Data;
import lombok.With;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * World state representation for a single note.
 * All fields are immutable via Lombok @With for GOAP state transitions.
 */
@Data
@Builder
@With
public class NoteMetadata {
    private final String noteId;
    private final Path filePath;
    private final NoteType noteType;

    // Embedding state
    private final boolean hasEmbeddings;
    private final String embedModel;
    private final Instant embedAt;

    // Format state
    private final boolean formatOk;
    private final boolean hasProperties;
    private final boolean hasTitle;

    // Link state
    private final int linkCount;
    private final boolean orphan;
    private final List<String> outboundLinks;
    private final List<String> inboundLinks;

    // Taxonomy state
    private final Set<String> tags;
    private final boolean tagsCanonical;

    // Provenance state
    private final boolean provenanceOk;
    private final Instant createdAt;
    private final Instant updatedAt;

    // Staleness
    private final int staleDays;

    // Opt-out
    private final boolean agentsDisabled;

    // Computed health score (0-100)
    private final int healthScore;

    // Structure analysis (from LLM)
    private final StructureAnalysis structureAnalysis;
    private final boolean hasMultipleTopics;
    private final List<String> detectedTopics;
    private final List<SplitPoint> suggestedSplitPoints;
    private final double internalCoherence;  // 0.0-1.0

    // Token count and size categorization
    private final int tokenCount;
    private final boolean tooSmall;  // < 300 tokens
    private final boolean tooLarge;  // > 800 tokens

    // Merge candidates (from semantic search)
    private final List<String> semanticallySimilarNoteIds;

    /**
     * Check if embeddings are fresh based on max age in days
     */
    public boolean isEmbeddingsFresh(int maxAgeDays) {
        if (!hasEmbeddings || embedAt == null) {
            return false;
        }
        Instant threshold = Instant.now().minusSeconds(maxAgeDays * 24L * 60 * 60);
        return embedAt.isAfter(threshold);
    }

    /**
     * Check if note is stale based on threshold
     */
    public boolean isStale(int thresholdDays) {
        return staleDays > thresholdDays;
    }

    /**
     * Check if this note can be modified (not Source-locked and agents not disabled)
     */
    public boolean isContentMutable() {
        return noteType != NoteType.SOURCE && !agentsDisabled;
    }

    /**
     * Check if metadata can be modified
     */
    public boolean isMetadataMutable() {
        return !agentsDisabled;
    }
}
