package com.dcruver.orgroam.domain.actions;

import com.dcruver.orgroam.domain.CorpusState;
import com.dcruver.orgroam.domain.HubNoteCandidate;
import com.dcruver.orgroam.domain.NoteCluster;
import com.dcruver.orgroam.domain.NoteMetadata;
import com.dcruver.orgroam.io.PatchWriter;
import com.dcruver.orgroam.nlp.OllamaChatService;
import com.embabel.agent.core.action.Action;
import com.embabel.agent.core.action.ActionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GOAP Action: Propose hub notes (MOCs) to organize clusters of related notes.
 *
 * This is where GOAP creates NEW KNOWLEDGE STRUCTURE:
 * - Takes clusters with discovered themes
 * - Generates hub note content via LLM
 * - Creates proposals for new MOC notes that will connect the cluster
 * - Each proposal includes full note content, links, and rationale
 *
 * Preconditions: Clusters have discovered themes
 * Effects: Hub note candidates are created, proposals written
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProposeHubNoteAction implements Action<CorpusState> {

    private final OllamaChatService llmService;
    private final PatchWriter patchWriter;

    @Value("${gardener.notes-path}")
    private String notesPath;

    private static final int MIN_CLUSTER_SIZE = 3;  // Only propose hubs for meaningful clusters

    @Override
    public String getName() {
        return "ProposeHubNote";
    }

    @Override
    public String getDescription() {
        return "Generate hub note (MOC) proposals to organize clusters of related notes";
    }

    @Override
    public boolean canExecute(CorpusState state) {
        // Precondition: clusters must have discovered themes
        if (state.getOrphanClusters() == null || state.getOrphanClusters().isEmpty()) {
            log.debug("Cannot propose hub notes: no orphan clusters");
            return false;
        }

        // Check if there are clusters with themes that need hub notes
        long clustersNeedingHubs = state.getOrphanClusters().stream()
            .filter(cluster -> cluster.size() >= MIN_CLUSTER_SIZE)
            .filter(NoteCluster::hasTheme)
            .count();

        if (clustersNeedingHubs == 0) {
            log.debug("No clusters with themes need hub notes");
            return false;
        }

        return true;
    }

    @Override
    public double estimateCost(CorpusState state) {
        // Cost is proportional to number of hubs to generate (LLM content generation is expensive)
        long hubsToGenerate = state.getOrphanClusters().stream()
            .filter(cluster -> cluster.size() >= MIN_CLUSTER_SIZE)
            .filter(NoteCluster::hasTheme)
            .count();
        return hubsToGenerate * 20.0;  // 20 units per hub (most expensive action)
    }

    @Override
    public ActionResult<CorpusState> execute(CorpusState state) {
        log.info("Generating hub note proposals for orphan clusters...");

        List<HubNoteCandidate> hubCandidates = new ArrayList<>();
        int proposalsCreated = 0;

        for (NoteCluster cluster : state.getOrphanClusters()) {
            if (cluster.size() < MIN_CLUSTER_SIZE || !cluster.hasTheme()) {
                continue;  // Skip small or unanalyzed clusters
            }

            try {
                // Get cluster notes
                List<NoteMetadata> clusterNotes = cluster.getNoteIds().stream()
                    .map(state::getNote)
                    .filter(note -> note != null)
                    .toList();

                if (clusterNotes.isEmpty()) {
                    log.warn("Cluster has no valid notes");
                    continue;
                }

                // Extract theme information
                String themeAnalysis = cluster.getDiscoveredTheme();
                String hubTitle = extractHubTitle(themeAnalysis);

                // Generate hub note content via LLM
                String hubContent = generateHubNoteContent(hubTitle, themeAnalysis, clusterNotes);

                // Calculate priority based on cluster size and isolation
                double priority = calculatePriority(cluster, state);

                // Create hub note candidate
                HubNoteCandidate candidate = HubNoteCandidate.builder()
                    .proposedTitle(hubTitle)
                    .proposedContent(hubContent)
                    .linkedNoteIds(cluster.getNoteIds())
                    .rationale(buildRationale(cluster, clusterNotes, themeAnalysis))
                    .type(HubNoteCandidate.HubType.MOC)
                    .priority(priority)
                    .build();

                hubCandidates.add(candidate);

                // Create a proposal for human review
                createHubProposal(candidate, state);
                proposalsCreated++;

                log.info("Created hub note proposal: {}", hubTitle);

            } catch (Exception e) {
                log.error("Failed to generate hub note for cluster: {}", e.getMessage(), e);
            }
        }

        // Update state with hub candidates
        CorpusState newState = state.withHubCandidates(hubCandidates);

        String message = String.format("Generated %d hub note proposals to organize orphan clusters",
            proposalsCreated);
        return ActionResult.success(newState, message);
    }

    /**
     * Extract a concise title from the LLM's theme analysis
     */
    private String extractHubTitle(String themeAnalysis) {
        // Look for "THEME: " line in analysis
        String[] lines = themeAnalysis.split("\n");
        for (String line : lines) {
            if (line.startsWith("THEME:")) {
                return line.substring(6).trim();
            }
        }

        // Fallback: use first line
        return lines.length > 0 ? lines[0].trim() : "Related Concepts";
    }

    /**
     * Generate full hub note content via LLM
     */
    private String generateHubNoteContent(String title, String themeAnalysis,
                                         List<NoteMetadata> linkedNotes) {
        String prompt = buildHubContentPrompt(title, themeAnalysis, linkedNotes);
        return llmService.chat(prompt);
    }

    private String buildHubContentPrompt(String title, String themeAnalysis,
                                         List<NoteMetadata> linkedNotes) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are creating a hub note (Map of Content / MOC) for a knowledge base.\n\n");
        prompt.append("Hub note title: ").append(title).append("\n\n");
        prompt.append("Theme analysis:\n").append(themeAnalysis).append("\n\n");
        prompt.append("Notes to be linked:\n");

        for (NoteMetadata note : linkedNotes) {
            prompt.append("- [[").append(note.getTitle()).append("]]\n");
        }

        prompt.append("\n---\n\n");
        prompt.append("Create org-mode content for this hub note that:\n");
        prompt.append("1. Explains the theme/concept clearly (2-3 paragraphs)\n");
        prompt.append("2. Shows how the linked notes relate to this theme\n");
        prompt.append("3. Provides context for why these notes are grouped together\n");
        prompt.append("4. Uses org-mode wiki-links format: [[Note Title]]\n");
        prompt.append("5. Does NOT include :PROPERTIES: drawer or #+title (those will be added automatically)\n\n");
        prompt.append("Write in a clear, encyclopedic style suitable for a personal knowledge base.\n");

        return prompt.toString();
    }

    /**
     * Calculate priority for this hub note proposal
     */
    private double calculatePriority(NoteCluster cluster, CorpusState state) {
        // Priority factors:
        // - Larger clusters = higher priority
        // - Higher average similarity = higher priority
        // - More isolated (fewer existing links) = higher priority

        double sizeFactor = Math.min(1.0, cluster.size() / 10.0);  // Normalize to 0-1
        double similarityFactor = cluster.getAvgSimilarity();

        // Calculate isolation (how many of these notes have NO links at all)
        long completelyOrphaned = cluster.getNoteIds().stream()
            .map(state::getNote)
            .filter(note -> note != null && note.getLinkCount() == 0)
            .count();

        double isolationFactor = (double) completelyOrphaned / cluster.size();

        // Weighted average
        return (sizeFactor * 0.4) + (similarityFactor * 0.3) + (isolationFactor * 0.3);
    }

    /**
     * Build human-readable rationale for why this hub note should be created
     */
    private String buildRationale(NoteCluster cluster, List<NoteMetadata> notes,
                                  String themeAnalysis) {
        StringBuilder rationale = new StringBuilder();
        rationale.append("Discovered cluster of ").append(cluster.size())
                 .append(" orphaned notes with semantic similarity ")
                 .append(String.format("%.2f", cluster.getAvgSimilarity()))
                 .append(".\n\n");

        rationale.append("Theme analysis:\n").append(themeAnalysis).append("\n\n");

        rationale.append("Creating this hub note will:\n");
        rationale.append("- Provide organizational structure for ").append(cluster.size())
                 .append(" currently isolated notes\n");
        rationale.append("- Make the implicit connection between these concepts explicit\n");
        rationale.append("- Improve discoverability through centralized navigation\n");
        rationale.append("- Reduce knowledge fragmentation in your knowledge base\n");

        return rationale.toString();
    }

    /**
     * Create a proposal file for human review
     */
    private void createHubProposal(HubNoteCandidate candidate, CorpusState state)
            throws Exception {
        // Generate a note ID for the proposed hub
        String proposedNoteId = generateNoteId();
        String proposedFileName = sanitizeTitle(candidate.getProposedTitle()) + "-"
            + proposedNoteId + ".org";

        // Build full org file content
        String orgContent = buildOrgFileContent(candidate, proposedNoteId);

        // Since this is a new file creation, not a patch, we'll create a special proposal
        Map<String, Object> beforeStats = new HashMap<>();
        beforeStats.put("exists", false);
        beforeStats.put("note_count_in_cluster", candidate.linkedNoteIds().size());

        Map<String, Object> afterStats = new HashMap<>();
        afterStats.put("exists", true);
        afterStats.put("hub_note", true);
        afterStats.put("links_count", candidate.linkCount());

        // Create proposal with the new note content as "revised"
        patchWriter.createProposal(
            proposedNoteId,
            java.nio.file.Paths.get(notesPath, proposedFileName),
            "ProposeHubNote",
            candidate.getRationale(),
            "",  // No original content (new file)
            orgContent,  // Proposed content
            beforeStats,
            afterStats
        );
    }

    /**
     * Build complete org-mode file content for hub note
     */
    private String buildOrgFileContent(HubNoteCandidate candidate, String noteId) {
        StringBuilder org = new StringBuilder();

        // Properties drawer
        org.append(":PROPERTIES:\n");
        org.append(":ID: ").append(noteId).append("\n");
        org.append(":CREATED: [").append(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
           .append("]\n");
        org.append(":ROAM_ALIASES:\n");
        org.append(":NODE-TYPE: moc\n");  // Mark as Map of Content
        org.append(":END:\n\n");

        // Title
        org.append("#+title: ").append(candidate.getProposedTitle()).append("\n\n");

        // Hub content (generated by LLM)
        org.append(candidate.getProposedContent()).append("\n");

        return org.toString();
    }

    private String generateNoteId() {
        // Simple timestamp-based ID (matches org-roam convention)
        return String.valueOf(System.currentTimeMillis() / 1000);
    }

    private String sanitizeTitle(String title) {
        // Convert title to filename-safe format
        return title.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
    }

    @Override
    public boolean isSafe() {
        return false;  // Creating new notes requires human approval
    }
}
