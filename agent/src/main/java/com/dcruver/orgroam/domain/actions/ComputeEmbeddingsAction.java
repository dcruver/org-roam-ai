package com.dcruver.orgroam.domain.actions;

import com.dcruver.orgroam.domain.CorpusState;
import com.dcruver.orgroam.domain.NoteMetadata;
import com.dcruver.orgroam.nlp.OrgRoamMcpClient;
import com.embabel.agent.core.action.Action;
import com.embabel.agent.core.action.ActionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Safe action: Trigger embedding generation for notes via org-roam-semantic.
 *
 * NOTE: This action does NOT directly generate embeddings. Instead, it relies on
 * org-roam-semantic's Emacs hooks to automatically generate embeddings when notes
 * are modified. The MCP server ensures embeddings are created during note operations.
 *
 * Preconditions:
 * - Note not Source-locked (or metadata-only operation allowed)
 * - Missing embeddings OR embeddings older than max age
 *
 * Effects:
 * - Logs notes needing embeddings
 * - User should manually trigger org-roam-semantic-generate-all-embeddings in Emacs
 *   or embeddings will be generated automatically on next note modification
 *
 * Cost: Low (just logging and verification)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ComputeEmbeddingsAction implements Action<CorpusState> {

    @Autowired(required = false)
    private final OrgRoamMcpClient mcpClient;

    @Value("${gardener.embeddings.max-age-days:90}")
    private int maxAgeDays;

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
        log.info("Generating embeddings for notes via org-roam-semantic (through MCP)");

        List<NoteMetadata> notesNeedingEmbeddings = state.getNotes().stream()
            .filter(note -> note.isMetadataMutable())
            .filter(note -> !note.isHasEmbeddings() || !note.isEmbeddingsFresh(maxAgeDays))
            .toList();

        if (notesNeedingEmbeddings.isEmpty()) {
            log.info("No notes need embeddings");
            return ActionResult.<CorpusState>builder()
                .success(true)
                .resultingState(state)
                .message("No notes need embeddings")
                .build();
        }

        log.info("Found {} notes needing embeddings:", notesNeedingEmbeddings.size());
        for (NoteMetadata note : notesNeedingEmbeddings) {
            String reason = !note.isHasEmbeddings() ? "missing embeddings" : "stale embeddings";
            log.info("  - {} ({}): {}", note.getFilePath().getFileName(), note.getNoteId(), reason);
        }

        // Check if MCP is available
        if (mcpClient == null || !mcpClient.isAvailable()) {
            log.error("MCP server is not available - cannot generate embeddings");
            log.warn("To generate embeddings manually:");
            log.warn("  1. Ensure Emacs is running with org-roam-semantic loaded");
            log.warn("  2. Run: M-x org-roam-semantic-generate-all-embeddings");
            return ActionResult.<CorpusState>builder()
                .success(false)
                .resultingState(state)
                .message("MCP server unavailable - embeddings must be generated manually")
                .build();
        }

        // Call MCP to generate embeddings
        log.info("Calling MCP server to generate embeddings...");
        OrgRoamMcpClient.GenerateEmbeddingsResult result = mcpClient.generateEmbeddings(false);

        if (result.isSuccess()) {
            log.info("Successfully generated {} embeddings", result.getCount());
            return ActionResult.<CorpusState>builder()
                .success(true)
                .resultingState(state)
                .message(String.format("Generated %d embeddings via org-roam-semantic",
                    result.getCount()))
                .build();
        } else {
            log.error("Failed to generate embeddings: {}", result.getMessage());
            return ActionResult.<CorpusState>builder()
                .success(false)
                .resultingState(state)
                .message("Failed to generate embeddings: " + result.getMessage())
                .build();
        }
    }

    @Override
    public double getCost(CorpusState state) {
        // Cost reflects actual work: MCP call + embedding generation in Emacs
        // Each embedding call to Ollama takes time, so moderate cost
        return state.getNotesNeedingEmbeddings() * 5.0;
    }

    @Override
    public CorpusState apply(CorpusState state) {
        // This action doesn't modify state - embeddings are managed externally
        // by org-roam-semantic in Emacs
        return state;
    }
}
