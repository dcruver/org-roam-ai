package com.dcruver.orgroam.agent.domain;

import com.dcruver.orgroam.domain.CorpusState;
import lombok.Builder;
import lombok.Value;

/**
 * Corpus with all notes having fresh embeddings.
 * Prerequisite for semantic operations (clustering, similarity search).
 */
@Value
@Builder
public class CorpusWithEmbeddings {
    CorpusState state;

    public int getTotalNotes() {
        return state.getTotalNotes();
    }

    public int getOrphanCount() {
        return state.getOrphanNotes() != null ? state.getOrphanNotes().size() : 0;
    }

    /**
     * Guarantee: all notes have embeddings
     */
    public int getNotesNeedingEmbeddings() {
        return 0;  // Always zero - this type guarantees embeddings exist
    }

    public CorpusState getState() {
        return state;
    }
}
