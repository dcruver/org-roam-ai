package com.dcruver.orgroam.domain.actions;

import com.dcruver.orgroam.domain.CorpusState;
import com.dcruver.orgroam.domain.NoteMetadata;
import com.dcruver.orgroam.io.OrgFileReader;
import com.dcruver.orgroam.io.OrgNote;
import com.dcruver.orgroam.nlp.EmbeddingStore;
import com.dcruver.orgroam.nlp.OllamaEmbeddingService;
import com.embabel.agent.core.action.Action;
import com.embabel.agent.core.action.ActionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Safe action: Compute embeddings for notes missing or with stale embeddings.
 *
 * Preconditions:
 * - Note not Source-locked (or metadata-only operation allowed)
 * - Missing embeddings OR embeddings older than max age
 *
 * Effects:
 * - Write chunk embeddings to store
 * - Update note metadata with EMBED_MODEL and EMBED_AT
 *
 * Cost: Medium (LLM API call)
 */
@Component
@Slf4j
public class ComputeEmbeddingsAction implements Action<CorpusState> {

    private final OllamaEmbeddingService embeddingService;
    private final EmbeddingStore embeddingStore;
    private final OrgFileReader fileReader;

    public ComputeEmbeddingsAction(
            @org.springframework.beans.factory.annotation.Autowired(required = false) OllamaEmbeddingService embeddingService,
            EmbeddingStore embeddingStore,
            OrgFileReader fileReader) {
        this.embeddingService = embeddingService;
        this.embeddingStore = embeddingStore;
        this.fileReader = fileReader;
    }

    @Value("${gardener.embeddings.max-age-days:90}")
    private int maxAgeDays;

    @Value("${spring.ai.ollama.embedding.options.model}")
    private String embedModel;

    @Override
    public String getName() {
        return "ComputeEmbeddings";
    }

    @Override
    public String getDescription() {
        return "Compute and store embeddings for notes";
    }

    @Override
    public boolean canExecute(CorpusState state) {
        // Can execute if any note needs embeddings
        return state.getNotesNeedingEmbeddings() > 0;
    }

    @Override
    public ActionResult<CorpusState> execute(CorpusState state) {
        log.info("Computing embeddings for notes needing them");
        int computed = 0;
        int failed = 0;

        for (NoteMetadata note : state.getNotes()) {
            if (!note.isMetadataMutable()) {
                log.debug("Skipping note {} - agents disabled", note.getNoteId());
                continue;
            }

            boolean needsEmbedding = !note.isHasEmbeddings() || !note.isEmbeddingsFresh(maxAgeDays);
            if (!needsEmbedding) {
                continue;
            }

            try {
                // Read note content
                OrgNote orgNote = fileReader.read(note.getFilePath());
                String content = orgNote.getBody();

                if (content == null || content.isBlank()) {
                    log.warn("Note {} has no content to embed", note.getNoteId());
                    continue;
                }

                // Compute chunk hash (simplified - use content hash)
                String chunkHash = Integer.toHexString(content.hashCode());

                // Generate embedding
                List<Double> embedding = embeddingService.embed(content);
                if (embedding.isEmpty()) {
                    log.error("Failed to generate embedding for note {}", note.getNoteId());
                    failed++;
                    continue;
                }

                // Store embedding
                String preview = content.substring(0, Math.min(100, content.length()));
                embeddingStore.store(note.getNoteId(), chunkHash, embedding, preview);

                // Update metadata (in real implementation, would update the file)
                log.info("Computed embedding for note {}", note.getNoteId());
                computed++;

            } catch (Exception e) {
                log.error("Failed to compute embedding for note {}", note.getNoteId(), e);
                failed++;
            }
        }

        log.info("Computed {} embeddings ({} failed)", computed, failed);

        return ActionResult.<CorpusState>builder()
            .success(failed == 0)
            .resultingState(state) // Would update state with new embeddings
            .message(String.format("Computed %d embeddings (%d failed)", computed, failed))
            .build();
    }

    @Override
    public double getCost(CorpusState state) {
        // Cost based on number of notes needing embeddings
        // Each embedding call is moderate cost
        return state.getNotesNeedingEmbeddings() * 5.0;
    }

    @Override
    public CorpusState apply(CorpusState state) {
        // Return updated state after computing embeddings
        // In real implementation, would update note metadata
        List<NoteMetadata> updatedNotes = state.getNotes().stream()
            .map(note -> {
                if (!note.isHasEmbeddings()) {
                    return note.withHasEmbeddings(true)
                        .withEmbedModel(embedModel)
                        .withEmbedAt(Instant.now());
                }
                return note;
            })
            .toList();

        return state.withNotes(updatedNotes);
    }
}
