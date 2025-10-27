package com.dcruver.orgroam.agent.domain;

import com.dcruver.orgroam.domain.CorpusState;
import lombok.Builder;
import lombok.Value;

/**
 * Initial corpus state after scanning - no guarantees about formatting or embeddings.
 * First type in the action chain for Embabel planning.
 */
@Value
@Builder
public class RawCorpus {
    CorpusState state;  // Underlying state data

    public int getTotalNotes() {
        return state.getTotalNotes();
    }

    public int getNotesWithFormatIssues() {
        return state.getNotesWithFormatIssues();
    }

    public int getNotesNeedingEmbeddings() {
        return state.getNotesNeedingEmbeddings();
    }

    public int getOrphanCount() {
        return state.getOrphanNotes() != null ? state.getOrphanNotes().size() : 0;
    }
}
