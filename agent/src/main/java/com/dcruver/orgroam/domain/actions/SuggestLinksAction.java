package com.dcruver.orgroam.domain.actions;

import com.dcruver.orgroam.domain.CorpusState;
import com.dcruver.orgroam.domain.NoteMetadata;
import com.dcruver.orgroam.domain.NoteType;
import com.dcruver.orgroam.io.OrgFileReader;
import com.dcruver.orgroam.io.OrgNote;
import com.dcruver.orgroam.io.PatchWriter;
import com.dcruver.orgroam.nlp.OllamaChatService;
import com.dcruver.orgroam.nlp.OrgRoamMcpClient;
import com.embabel.agent.core.action.Action;
import com.embabel.agent.core.action.ActionResult;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Semi-auto action: Suggest links between related notes.
 *
 * Uses embedding similarity (+ optional BM25) to find related notes,
 * then generates proposals with rationale.
 *
 * Preconditions:
 * - Notes have embeddings
 * - Orphan notes exist OR notes have low link density
 *
 * Effects:
 * - Creates link proposals with diff and rationale
 * - Increases linkCount for target notes
 *
 * Cost: High (embedding similarity + LLM for rationale)
 */
@Component
@Slf4j
public class SuggestLinksAction implements Action<CorpusState> {

    private final OrgFileReader fileReader;
    private final PatchWriter patchWriter;
    private final OrgRoamMcpClient mcpClient;
    private final OllamaChatService chatService;

    public SuggestLinksAction(
            OrgFileReader fileReader,
            PatchWriter patchWriter,
            @org.springframework.beans.factory.annotation.Autowired(required = false) OrgRoamMcpClient mcpClient,
            @org.springframework.beans.factory.annotation.Autowired(required = false) OllamaChatService chatService) {
        this.fileReader = fileReader;
        this.patchWriter = patchWriter;
        this.mcpClient = mcpClient;
        this.chatService = chatService;
    }

    @Value("${gardener.linking.confidence-threshold:0.72}")
    private double confidenceThreshold;

    @Value("${gardener.linking.max-links-per-run:7}")
    private int maxLinksPerRun;

    @Value("${gardener.linking.auto-link-source-notes:false}")
    private boolean autoLinkSourceNotes;

    @Override
    public String getName() {
        return "SuggestLinks";
    }

    @Override
    public String getDescription() {
        return "Suggest links between related notes using embedding similarity";
    }

    @Override
    public boolean canExecute(CorpusState state) {
        // Check if MCP client is available
        if (mcpClient == null || !mcpClient.isAvailable()) {
            log.debug("MCP client not available, cannot suggest links");
            return false;
        }
        return state.getOrphanNotes() > 0 || hasNotesNeedingLinks(state);
    }

    @Override
    public ActionResult<CorpusState> execute(CorpusState state) {
        log.info("Suggesting links for orphan and low-link notes using MCP semantic search");
        int suggestionsCreated = 0;
        int failed = 0;

        // Find notes needing links
        List<NoteMetadata> notesNeedingLinks = state.getNotes().stream()
            .filter(n -> n.isOrphan() || n.getLinkCount() < 3)
            .filter(n -> n.isContentMutable() || autoLinkSourceNotes)
            .filter(n -> !n.isAgentsDisabled())
            .limit(10)  // Process max 10 notes per run
            .toList();

        if (notesNeedingLinks.isEmpty()) {
            log.info("No notes need links");
            return ActionResult.<CorpusState>builder()
                .success(true)
                .resultingState(state)
                .message("No notes need links")
                .build();
        }

        for (NoteMetadata note : notesNeedingLinks) {
            try {
                // Read note content for semantic search
                OrgNote orgNote = fileReader.read(note.getFilePath());
                String noteContent = orgNote.getBody();

                if (noteContent == null || noteContent.isBlank()) {
                    log.debug("Note {} has no content, skipping", note.getNoteId());
                    continue;
                }

                // Use MCP to find semantically similar notes
                List<LinkSuggestion> suggestions = findSimilarNotesViaMcp(note, noteContent, state);

                if (suggestions.isEmpty()) {
                    log.debug("No link suggestions for note {}", note.getNoteId());
                    continue;
                }

                // Take top N suggestions
                List<LinkSuggestion> topSuggestions = suggestions.stream()
                    .limit(maxLinksPerRun)
                    .toList();

                // Create proposal
                createLinkProposal(note, topSuggestions, orgNote);
                suggestionsCreated++;

            } catch (Exception e) {
                log.error("Failed to suggest links for note {}", note.getNoteId(), e);
                failed++;
            }
        }

        log.info("Created {} link suggestions ({} failed)", suggestionsCreated, failed);

        return ActionResult.<CorpusState>builder()
            .success(failed == 0)
            .resultingState(state)
            .message(String.format("Created %d link suggestions (%d failed)", suggestionsCreated, failed))
            .build();
    }

    @Override
    public double getCost(CorpusState state) {
        // High cost - embedding comparison + LLM calls
        int notesNeedingLinks = (int) state.getNotes().stream()
            .filter(n -> n.isOrphan() || n.getLinkCount() < 3)
            .count();

        return notesNeedingLinks * 10.0;
    }

    @Override
    public CorpusState apply(CorpusState state) {
        // State doesn't change until proposals are applied
        return state;
    }

    /**
     * Find similar notes using MCP semantic search
     */
    private List<LinkSuggestion> findSimilarNotesViaMcp(
        NoteMetadata sourceNote,
        String noteContent,
        CorpusState state
    ) {
        // Query MCP for semantically similar notes
        List<OrgRoamMcpClient.SemanticSearchResult> mcpResults = mcpClient.semanticSearch(
            noteContent,
            maxLinksPerRun * 2,  // Get more results than needed to filter
            confidenceThreshold
        );

        List<LinkSuggestion> suggestions = new ArrayList<>();

        for (OrgRoamMcpClient.SemanticSearchResult result : mcpResults) {
            // Skip self
            if (result.getNodeId() != null && result.getNodeId().equals(sourceNote.getNoteId())) {
                continue;
            }

            // Skip if already linked
            if (sourceNote.getOutboundLinks() != null &&
                result.getNodeId() != null &&
                sourceNote.getOutboundLinks().contains("id:" + result.getNodeId())) {
                continue;
            }

            // Find the note in corpus state
            NoteMetadata targetNote = result.getNodeId() != null ?
                state.getNote(result.getNodeId()) : null;

            if (targetNote != null) {
                suggestions.add(new LinkSuggestion(targetNote, result.getSimilarity()));
            } else {
                log.debug("Could not find target note {} in corpus state", result.getNodeId());
            }
        }

        // Sort by similarity descending (should already be sorted but ensure it)
        suggestions.sort(Comparator.comparingDouble(LinkSuggestion::getSimilarity).reversed());

        log.debug("Found {} link suggestions for note {} via MCP", suggestions.size(), sourceNote.getNoteId());
        return suggestions;
    }

    /**
     * Create a link proposal
     */
    private void createLinkProposal(NoteMetadata sourceNote, List<LinkSuggestion> suggestions, OrgNote orgNote) throws Exception {
        String originalContent = orgNote.getRawContent();

        // Build updated content with links
        StringBuilder linkSection = new StringBuilder("\n\n** Related Notes\n");
        StringBuilder rationale = new StringBuilder("Link suggestions based on semantic similarity:\n\n");

        for (LinkSuggestion suggestion : suggestions) {
            String targetId = suggestion.getTargetNote().getNoteId();
            String targetTitle = suggestion.getTargetNote().getFilePath().getFileName().toString();

            linkSection.append(String.format("- [[id:%s][%s]] (similarity: %.3f)\n",
                targetId, targetTitle, suggestion.getSimilarity()));

            // Generate rationale using LLM
            try {
                String llmRationale = chatService.generateLinkRationale(
                    orgNote.getBody(),
                    "", // Would read target note content
                    suggestion.getSimilarity()
                );
                rationale.append(String.format("- %s → %s: %s\n",
                    sourceNote.getNoteId(), targetId, llmRationale));
            } catch (Exception e) {
                log.warn("Failed to generate LLM rationale", e);
                rationale.append(String.format("- %s → %s: High semantic similarity (%.3f)\n",
                    sourceNote.getNoteId(), targetId, suggestion.getSimilarity()));
            }
        }

        String updatedContent = originalContent + linkSection.toString();

        // Create proposal
        Map<String, Object> beforeStats = Map.of(
            "linkCount", sourceNote.getLinkCount(),
            "orphan", sourceNote.isOrphan()
        );

        Map<String, Object> afterStats = Map.of(
            "linkCount", sourceNote.getLinkCount() + suggestions.size(),
            "orphan", false
        );

        patchWriter.createProposal(
            sourceNote.getNoteId(),
            sourceNote.getFilePath(),
            getName(),
            rationale.toString(),
            originalContent,
            updatedContent,
            beforeStats,
            afterStats
        );

        log.info("Created link proposal for note {} with {} suggestions",
            sourceNote.getNoteId(), suggestions.size());
    }

    private boolean hasNotesNeedingLinks(CorpusState state) {
        return state.getNotes().stream()
            .anyMatch(n -> n.getLinkCount() < 3 && !n.isAgentsDisabled());
    }

    @Data
    private static class LinkSuggestion {
        private final NoteMetadata targetNote;
        private final double similarity;
    }
}
