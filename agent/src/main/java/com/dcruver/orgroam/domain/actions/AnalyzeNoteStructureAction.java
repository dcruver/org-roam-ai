package com.dcruver.orgroam.domain.actions;

import com.dcruver.orgroam.domain.*;
import com.dcruver.orgroam.io.OrgFileReader;
import com.dcruver.orgroam.io.OrgNote;
import com.dcruver.orgroam.nlp.OllamaChatService;
import com.dcruver.orgroam.nlp.OrgRoamMcpClient;
import com.embabel.agent.core.action.Action;
import com.embabel.agent.core.action.ActionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Discovery action: Analyze note structure to identify split/merge candidates.
 *
 * Uses LLM to analyze each note's semantic structure and identify:
 * - Notes with multiple topics (split candidates)
 * - Notes that are too small or have low coherence (merge candidates)
 * - Optimal chunk size (300-800 tokens for embeddings)
 *
 * This is a **discovery action** - it doesn't modify notes, just populates
 * structure analysis data that other actions (SplitNote, MergeNotes) can use.
 *
 * Preconditions:
 * - LLM service available
 * - MCP client available (for semantic search to find merge candidates)
 *
 * Effects:
 * - Populates structureAnalysis field in NoteMetadata
 * - Updates CorpusState with split/merge candidate counts
 * - Identifies semantically similar notes via MCP
 *
 * Cost: High (LLM analysis for each note + semantic searches)
 */
@Component
@Slf4j
public class AnalyzeNoteStructureAction implements Action<CorpusState> {

    private final OrgFileReader fileReader;
    private final OllamaChatService chatService;
    private final OrgRoamMcpClient mcpClient;
    private final ObjectMapper objectMapper;

    private static final int MIN_OPTIMAL_TOKENS = 300;
    private static final int MAX_OPTIMAL_TOKENS = 800;
    private static final double SIMILARITY_THRESHOLD = 0.85;  // For merge candidates

    public AnalyzeNoteStructureAction(
            OrgFileReader fileReader,
            @Autowired(required = false) OllamaChatService chatService,
            @Autowired(required = false) OrgRoamMcpClient mcpClient) {
        this.fileReader = fileReader;
        this.chatService = chatService;
        this.mcpClient = mcpClient;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getName() {
        return "AnalyzeNoteStructure";
    }

    @Override
    public String getDescription() {
        return "Analyze note structure with LLM to discover split/merge candidates";
    }

    @Override
    public boolean canExecute(CorpusState state) {
        if (chatService == null) {
            log.warn("Cannot execute AnalyzeNoteStructure: LLM service not available");
            return false;
        }

        // Can always analyze if LLM is available
        // Even if MCP is unavailable, we can still do structure analysis
        return true;
    }

    @Override
    public double getCost(CorpusState state) {
        // High cost: LLM analysis for each note + semantic searches
        // Estimate: 0.5 per note for structure analysis + 0.3 per note for semantic search
        int notesToAnalyze = state.getTotalNotes();
        return notesToAnalyze * 0.8;
    }

    @Override
    public CorpusState apply(CorpusState state) {
        ActionResult<CorpusState> result = execute(state);
        return result.getResultingState();
    }

    @Override
    public ActionResult<CorpusState> execute(CorpusState state) {
        log.info("Analyzing note structure for {} notes", state.getTotalNotes());

        int analyzedCount = 0;
        int splitCandidatesFound = 0;
        int mergeCandidatesFound = 0;
        Map<String, StructureAnalysis> analyses = new HashMap<>();
        Map<String, List<String>> mergeGroups = new HashMap<>();

        for (NoteMetadata note : state.getNotes()) {
            try {
                // Read note content
                OrgNote orgNote = fileReader.read(note.getFilePath());
                String content = orgNote.getRawContent();

                // Skip very short notes (< 100 chars)
                if (content.length() < 100) {
                    log.debug("Skipping analysis for tiny note: {}", note.getNoteId());
                    continue;
                }

                // LLM structure analysis
                String jsonResponse = chatService.analyzeNoteStructure(content);
                StructureAnalysis analysis = parseStructureAnalysis(jsonResponse, content);

                // Store analysis
                analyses.put(note.getNoteId(), analysis);

                // Update metadata
                note = note
                    .withStructureAnalysis(analysis)
                    .withHasMultipleTopics(analysis.hasMultipleTopics())
                    .withDetectedTopics(analysis.getTopics())
                    .withSuggestedSplitPoints(analysis.getSplitPoints())
                    .withInternalCoherence(analysis.getCoherence())
                    .withTokenCount(analysis.getTokenCount())
                    .withTooSmall(analysis.isTooSmall())
                    .withTooLarge(analysis.isTooLarge());

                // Count candidates
                if (analysis.shouldSplit()) {
                    splitCandidatesFound++;
                    log.info("Split candidate: {} - {} topics: {}",
                        note.getNoteId(),
                        analysis.getTopics().size(),
                        String.join(", ", analysis.getTopics()));
                }

                if (analysis.mightNeedMerge() && mcpClient != null && mcpClient.isAvailable()) {
                    // Find semantically similar notes via MCP
                    try {
                        final String currentNoteId = note.getNoteId();  // Final for lambda
                        List<OrgRoamMcpClient.SemanticSearchResult> similarNotes =
                            mcpClient.semanticSearch(content, 5, SIMILARITY_THRESHOLD);

                        List<String> similarIds = similarNotes.stream()
                            .filter(r -> r.getSimilarity() >= SIMILARITY_THRESHOLD)
                            .map(OrgRoamMcpClient.SemanticSearchResult::getNodeId)
                            .filter(id -> !id.equals(currentNoteId))
                            .collect(Collectors.toList());

                        if (!similarIds.isEmpty()) {
                            mergeGroups.put(currentNoteId, similarIds);
                            mergeCandidatesFound++;
                            note = note.withSemanticallySimilarNoteIds(similarIds);
                            log.info("Merge candidate: {} - similar to {} notes (scores: {})",
                                currentNoteId,
                                similarIds.size(),
                                similarNotes.stream()
                                    .limit(3)
                                    .map(r -> String.format("%.2f", r.getSimilarity()))
                                    .collect(Collectors.joining(", ")));
                        }
                    } catch (Exception e) {
                        log.warn("Failed to find merge candidates for {}: {}",
                            note.getNoteId(), e.getMessage());
                    }
                }

                analyzedCount++;

                // Progress logging every 10 notes
                if (analyzedCount % 10 == 0) {
                    log.info("Progress: analyzed {}/{} notes", analyzedCount, state.getTotalNotes());
                }

            } catch (Exception e) {
                log.error("Failed to analyze note {}: {}", note.getNoteId(), e.getMessage(), e);
            }
        }

        // Calculate aggregate statistics
        double meanCoherence = analyses.values().stream()
            .mapToDouble(StructureAnalysis::getCoherence)
            .average()
            .orElse(0.0);

        double meanTokenCount = analyses.values().stream()
            .mapToDouble(StructureAnalysis::getTokenCount)
            .average()
            .orElse(0.0);

        int notesWithMultipleTopics = (int) analyses.values().stream()
            .filter(StructureAnalysis::hasMultipleTopics)
            .count();

        int notesTooSmall = (int) analyses.values().stream()
            .filter(StructureAnalysis::isTooSmall)
            .count();

        int notesTooLarge = (int) analyses.values().stream()
            .filter(StructureAnalysis::isTooLarge)
            .count();

        // Update corpus state
        CorpusState updatedState = state
            .withStructureAnalyses(analyses)
            .withMergeGroups(mergeGroups)
            .withSplitCandidates(splitCandidatesFound)
            .withMergeCandidates(mergeCandidatesFound)
            .withNotesWithMultipleTopics(notesWithMultipleTopics)
            .withNotesTooSmall(notesTooSmall)
            .withNotesTooLarge(notesTooLarge)
            .withMeanCoherence(meanCoherence)
            .withMeanTokenCount(meanTokenCount);

        String message = String.format(
            "Analyzed %d notes: %d split candidates, %d merge candidates, " +
            "mean coherence: %.2f, mean tokens: %.0f",
            analyzedCount, splitCandidatesFound, mergeCandidatesFound,
            meanCoherence, meanTokenCount
        );

        log.info(message);

        return ActionResult.<CorpusState>builder()
            .success(true)
            .resultingState(updatedState)
            .message(message)
            .build();
    }

    /**
     * Parse LLM JSON response into StructureAnalysis object
     */
    private StructureAnalysis parseStructureAnalysis(String jsonResponse, String noteContent) {
        try {
            // Extract JSON from response (LLM might add text before/after)
            String json = extractJson(jsonResponse);
            JsonNode root = objectMapper.readTree(json);

            // Parse topics
            List<String> topics = new ArrayList<>();
            if (root.has("topics") && root.get("topics").isArray()) {
                root.get("topics").forEach(t -> topics.add(t.asText()));
            }

            // Parse split points
            List<SplitPoint> splitPoints = new ArrayList<>();
            if (root.has("splitPoints") && root.get("splitPoints").isArray()) {
                root.get("splitPoints").forEach(sp -> {
                    splitPoints.add(SplitPoint.builder()
                        .characterOffset(sp.get("characterOffset").asInt(0))
                        .beforeHeading(sp.get("beforeHeading").asText(""))
                        .afterHeading(sp.get("afterHeading").asText(""))
                        .rationale(sp.get("rationale").asText(""))
                        .confidence(sp.get("confidence").asDouble(0.5))
                        .build());
                });
            }

            double coherence = root.has("coherence") ? root.get("coherence").asDouble(0.5) : 0.5;
            int tokenCount = root.has("tokenCount") ?
                root.get("tokenCount").asInt() :
                chatService.estimateTokenCount(noteContent);

            boolean optimalSize = root.has("optimalSize") ? root.get("optimalSize").asBoolean() :
                (tokenCount >= MIN_OPTIMAL_TOKENS && tokenCount <= MAX_OPTIMAL_TOKENS);
            boolean tooSmall = root.has("tooSmall") ? root.get("tooSmall").asBoolean() :
                (tokenCount < MIN_OPTIMAL_TOKENS);
            boolean tooLarge = root.has("tooLarge") ? root.get("tooLarge").asBoolean() :
                (tokenCount > MAX_OPTIMAL_TOKENS);

            String recommendation = root.has("recommendation") ?
                root.get("recommendation").asText("No action needed") :
                "No action needed";

            return StructureAnalysis.builder()
                .topics(topics)
                .coherence(coherence)
                .splitPoints(splitPoints)
                .tokenCount(tokenCount)
                .optimalSize(optimalSize)
                .tooSmall(tooSmall)
                .tooLarge(tooLarge)
                .recommendation(recommendation)
                .build();

        } catch (Exception e) {
            log.error("Failed to parse LLM JSON response: {}", e.getMessage());
            // Return minimal analysis as fallback
            int estimatedTokens = chatService.estimateTokenCount(noteContent);
            return StructureAnalysis.builder()
                .topics(List.of("Unknown"))
                .coherence(0.5)
                .splitPoints(List.of())
                .tokenCount(estimatedTokens)
                .optimalSize(estimatedTokens >= MIN_OPTIMAL_TOKENS && estimatedTokens <= MAX_OPTIMAL_TOKENS)
                .tooSmall(estimatedTokens < MIN_OPTIMAL_TOKENS)
                .tooLarge(estimatedTokens > MAX_OPTIMAL_TOKENS)
                .recommendation("Analysis failed - manual review needed")
                .build();
        }
    }

    /**
     * Extract JSON from LLM response (might have text before/after)
     */
    private String extractJson(String response) {
        int jsonStart = response.indexOf('{');
        int jsonEnd = response.lastIndexOf('}');
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return response.substring(jsonStart, jsonEnd + 1);
        }
        return response;
    }
}
