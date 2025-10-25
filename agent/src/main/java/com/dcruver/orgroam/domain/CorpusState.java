package com.dcruver.orgroam.domain;

import lombok.Builder;
import lombok.Data;
import lombok.With;

import java.util.List;
import java.util.Map;

/**
 * Aggregated world state for the entire corpus.
 * Immutable via Lombok @With for GOAP state transitions.
 */
@Data
@Builder
@With
public class CorpusState {
    private final List<NoteMetadata> notes;
    private final Map<String, NoteMetadata> notesById;

    // Aggregate statistics
    private final int totalNotes;
    private final int notesWithEmbeddings;
    private final int notesWithStaleEmbeddings;
    private final int notesWithFormatIssues;
    private final int orphanNotes;
    private final int notesWithNonCanonicalTags;
    private final int staleNotes;

    // Mean health score across corpus
    private final double meanHealthScore;

    // Structure optimization tracking
    private final int notesWithMultipleTopics;
    private final int notesTooSmall;
    private final int notesTooLarge;
    private final int splitCandidates;
    private final int mergeCandidates;
    private final double meanCoherence;
    private final double meanTokenCount;

    // Split/merge candidate details
    private final Map<String, StructureAnalysis> structureAnalyses;  // noteId -> analysis
    private final Map<String, List<String>> mergeGroups;  // noteId -> similar note IDs

    /**
     * Get note by ID
     */
    public NoteMetadata getNote(String noteId) {
        return notesById.get(noteId);
    }

    /**
     * Check if corpus meets target health
     */
    public boolean meetsHealthTarget(int targetHealth) {
        return meanHealthScore >= targetHealth;
    }

    /**
     * Count notes needing embeddings
     */
    public int getNotesNeedingEmbeddings() {
        return totalNotes - notesWithEmbeddings + notesWithStaleEmbeddings;
    }

    /**
     * Calculate health score improvement potential
     */
    public double getHealthImprovementPotential(int targetHealth) {
        return Math.max(0, targetHealth - meanHealthScore);
    }
}
