package com.dcruver.orgroam.domain.actions;

import com.dcruver.orgroam.domain.CorpusState;
import com.dcruver.orgroam.domain.NoteCluster;
import com.dcruver.orgroam.domain.NoteMetadata;
import com.dcruver.orgroam.nlp.OllamaChatService;
import com.embabel.agent.core.action.Action;
import com.embabel.agent.core.action.ActionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GOAP Action: Use LLM to discover the implicit theme connecting notes in each cluster.
 *
 * This is where the INTELLIGENCE happens:
 * - Takes clusters of semantically similar orphans
 * - Reads the actual content of notes in each cluster
 * - Asks LLM: "What connects these notes? What is the implicit theme?"
 * - LLM discovers conceptual bridges that weren't explicitly stated
 *
 * Preconditions: Orphan clusters have been discovered
 * Effects: Clusters gain discoveredTheme field
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AnalyzeClusterThemesAction implements Action<CorpusState> {

    private final OllamaChatService llmService;

    private static final int MAX_CLUSTER_SIZE_TO_ANALYZE = 15;  // Don't send huge clusters to LLM

    @Override
    public String getName() {
        return "AnalyzeClusterThemes";
    }

    @Override
    public String getDescription() {
        return "Use LLM to discover the implicit themes connecting notes in each cluster";
    }

    @Override
    public boolean canExecute(CorpusState state) {
        // Precondition: orphan clusters must have been discovered
        if (state.getOrphanClusters() == null || state.getOrphanClusters().isEmpty()) {
            log.debug("Cannot analyze themes: no orphan clusters discovered");
            return false;
        }

        // Check if there are any clusters without themes
        long clustersWithoutThemes = state.getOrphanClusters().stream()
            .filter(cluster -> !cluster.hasTheme())
            .count();

        if (clustersWithoutThemes == 0) {
            log.debug("All clusters already have themes");
            return false;
        }

        return true;
    }

    @Override
    public double estimateCost(CorpusState state) {
        // Cost is proportional to number of clusters (LLM calls are expensive)
        long clustersToAnalyze = state.getOrphanClusters().stream()
            .filter(cluster -> !cluster.hasTheme())
            .count();
        return clustersToAnalyze * 15.0;  // 15 units per cluster (LLM analysis is expensive)
    }

    @Override
    public ActionResult<CorpusState> execute(CorpusState state) {
        log.info("Analyzing cluster themes with LLM...");

        List<NoteCluster> updatedClusters = new ArrayList<>();
        int analyzed = 0;

        for (NoteCluster cluster : state.getOrphanClusters()) {
            if (cluster.hasTheme()) {
                // Already analyzed, keep as-is
                updatedClusters.add(cluster);
                continue;
            }

            try {
                // Get note content for cluster
                List<NoteMetadata> clusterNotes = cluster.getNoteIds().stream()
                    .limit(MAX_CLUSTER_SIZE_TO_ANALYZE)  // Don't overwhelm LLM
                    .map(state::getNote)
                    .filter(note -> note != null)
                    .toList();

                if (clusterNotes.isEmpty()) {
                    log.warn("Cluster has no valid notes");
                    updatedClusters.add(cluster);
                    continue;
                }

                // Build LLM prompt with note content
                String prompt = buildThemeDiscoveryPrompt(clusterNotes);

                // Ask LLM to discover the theme
                String theme = llmService.chat(prompt);

                log.info("Discovered theme for {}-note cluster: {}", clusterNotes.size(),
                    theme.substring(0, Math.min(100, theme.length())));

                // Update cluster with discovered theme
                NoteCluster updated = cluster.withDiscoveredTheme(theme);
                updatedClusters.add(updated);
                analyzed++;

            } catch (Exception e) {
                log.error("Failed to analyze cluster theme: {}", e.getMessage());
                updatedClusters.add(cluster);  // Keep original
            }
        }

        // Update state with analyzed clusters
        CorpusState newState = state.withOrphanClusters(updatedClusters);

        String message = String.format("Analyzed %d clusters and discovered implicit themes", analyzed);
        return ActionResult.success(newState, message);
    }

    /**
     * Build a prompt that asks LLM to discover what connects these notes
     */
    private String buildThemeDiscoveryPrompt(List<NoteMetadata> notes) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are analyzing a knowledge base. ");
        prompt.append("These notes are semantically similar but have no explicit links between them.\n\n");
        prompt.append("Your task: Identify the IMPLICIT THEME or concept that connects them.\n\n");

        // Include note titles and excerpts
        for (int i = 0; i < notes.size(); i++) {
            NoteMetadata note = notes.get(i);
            prompt.append(String.format("Note %d: %s\n", i + 1, note.getTitle()));

            // Include first paragraph or 200 chars
            String content = note.getContent();
            String excerpt = content.lines()
                .filter(line -> !line.isBlank() && !line.startsWith(":") && !line.startsWith("#+"))
                .findFirst()
                .orElse("");

            if (excerpt.length() > 200) {
                excerpt = excerpt.substring(0, 200) + "...";
            }

            prompt.append(excerpt).append("\n\n");
        }

        prompt.append("---\n\n");
        prompt.append("What is the implicit theme, concept, or topic that connects these notes?\n");
        prompt.append("Provide:\n");
        prompt.append("1. A concise title for this theme (5-10 words)\n");
        prompt.append("2. A brief explanation (2-3 sentences) of what connects these notes\n");
        prompt.append("3. Why these notes should be linked together\n\n");
        prompt.append("Format your response as:\n");
        prompt.append("THEME: [title]\n");
        prompt.append("EXPLANATION: [explanation]\n");
        prompt.append("RATIONALE: [why they should be linked]\n");

        return prompt.toString();
    }

    @Override
    public boolean isSafe() {
        return true;  // Analysis only - doesn't modify files
    }
}
