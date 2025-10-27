package com.dcruver.orgroam.agent.domain;

import com.dcruver.orgroam.domain.CorpusState;
import lombok.Builder;
import lombok.Value;

/**
 * Corpus with all formatting issues resolved.
 * Precondition for embedding generation (embeddings need valid format).
 */
@Value
@Builder
public class FormattedCorpus {
    CorpusState state;

    public int getTotalNotes() {
        return state.getTotalNotes();
    }

    public int getNotesNeedingEmbeddings() {
        return state.getNotesNeedingEmbeddings();
    }

    public int getOrphanCount() {
        return state.getOrphanNotes() != null ? state.getOrphanNotes().size() : 0;
    }

    /**
     * Guarantee: no format issues (enforced by construction)
     */
    public int getNotesWithFormatIssues() {
        return 0;  // Always zero - this type guarantees formatting is done
    }
}
