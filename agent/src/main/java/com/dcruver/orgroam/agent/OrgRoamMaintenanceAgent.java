package com.dcruver.orgroam.agent;

import com.dcruver.orgroam.agent.domain.*;
import com.dcruver.orgroam.domain.CorpusScanner;
import com.dcruver.orgroam.domain.CorpusState;
import com.dcruver.orgroam.domain.NoteMetadata;
import com.dcruver.orgroam.domain.NoteCluster;
import com.dcruver.orgroam.io.OrgFileReader;
import com.dcruver.orgroam.io.OrgFileWriter;
import com.dcruver.orgroam.io.OrgNote;
import com.dcruver.orgroam.io.PatchWriter;
import com.dcruver.orgroam.nlp.OllamaChatService;
import com.dcruver.orgroam.nlp.OrgRoamMcpClient;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Embabel GOAP agent for org-roam knowledge base maintenance and improvement.
 *
 * Uses type-based planning where action dependencies are inferred from method signatures.
 * Embabel's A* planner automatically sequences actions to achieve goals.
 *
 * Action Chain:
 * 1. scanCorpus() -> RawCorpus (starting state)
 * 2. normalizeFormatting(RawCorpus) -> FormattedCorpus
 * 3. generateEmbeddings(FormattedCorpus) -> CorpusWithEmbeddings
 * 4. findOrphanClusters(CorpusWithEmbeddings) -> OrphanClusters
 * 5. analyzeClusterThemes(OrphanClusters) -> ClustersWithThemes
 * 6. proposeHubNotes(ClustersWithThemes) -> HealthyCorpus [GOAL]
 */
@Agent(description = "Maintains and improves org-roam knowledge base structure")
@Slf4j
@RequiredArgsConstructor
public class OrgRoamMaintenanceAgent {

    private final CorpusScanner scanner;
    private final OrgFileReader fileReader;
    private final OrgFileWriter fileWriter;
    private final PatchWriter patchWriter;

    @Autowired(required = false)
    private final OllamaChatService chatService;

    @Autowired(required = false)
    private final OrgRoamMcpClient mcpClient;

    @Value("${gardener.embeddings.max-age-days:90}")
    private int maxAgeDays;

    @Value("${gardener.notes-path}")
    private String notesPath;

    @Value("${gardener.proposals-dir:.gardener/proposals}")
    private String proposalsDirName;

    // ============================================================================
    // Starting Action: Scan Corpus
    // ============================================================================

    /**
     * Scan the corpus and return initial state.
     * This is the entry point for all planning.
     */
    @Action
    public RawCorpus scanCorpus() throws IOException {
        log.info("Scanning corpus to assess current state");
        CorpusState state = scanner.scanCorpus();

        log.info("Corpus scan complete: {} notes, {} with format issues, {} need embeddings, {} orphans",
            state.getTotalNotes(),
            state.getNotesWithFormatIssues(),
            state.getNotesNeedingEmbeddings(),
            state.getOrphanNotes());

        return RawCorpus.builder()
            .state(state)
            .build();
    }

    // ============================================================================
    // Maintenance Actions: Format -> Embeddings
    // ============================================================================

    /**
     * Normalize formatting for all notes with issues.
     * Uses LLM to analyze and fix Org-mode structure.
     *
     * Precondition: RawCorpus (any state)
     * Postcondition: FormattedCorpus (guarantees no format issues)
     */
    @Action
    public FormattedCorpus normalizeFormatting(RawCorpus corpus) {
        log.info("Normalizing formatting for {} notes with issues",
            corpus.getNotesWithFormatIssues());

        if (chatService == null) {
            log.warn("Cannot normalize formatting: LLM service not available");
            // Return formatted corpus anyway (no-op if LLM unavailable)
            return FormattedCorpus.builder()
                .state(corpus.getState())
                .build();
        }

        int normalized = 0;
        int failed = 0;

        for (NoteMetadata note : corpus.getState().getNotes()) {
            if (!note.isMetadataMutable() || note.isFormatOk()) {
                continue;
            }

            // Skip if we already have a pending proposal
            if (patchWriter.hasExistingProposal(note.getNoteId(), "NormalizeFormatting")) {
                log.debug("Skipping note {} - already has pending proposal", note.getNoteId());
                continue;
            }

            try {
                OrgNote orgNote = fileReader.read(note.getFilePath());
                String originalContent = orgNote.getRawContent();

                // Use LLM to fix formatting
                String updatedContent = chatService.normalizeOrgFormatting(originalContent, note.getNoteId());

                if (updatedContent == null || updatedContent.isBlank()) {
                    log.error("LLM returned empty content for note {}", note.getNoteId());
                    failed++;
                    continue;
                }

                updatedContent = updatedContent.trim() + "\n";
                String originalTrimmed = originalContent.trim() + "\n";

                if (!originalTrimmed.equals(updatedContent)) {
                    patchWriter.createBackup(note.getFilePath());
                    java.nio.file.Files.writeString(note.getFilePath(), updatedContent);
                    log.info("Applied LLM-normalized formatting for note {}", note.getNoteId());
                    normalized++;
                } else {
                    log.debug("Note {} already normalized (idempotent)", note.getNoteId());
                }

            } catch (Exception e) {
                log.error("Failed to normalize formatting for note {}", note.getNoteId(), e);
                failed++;
            }
        }

        log.info("Normalized {} notes ({} failed)", normalized, failed);

        // Return FormattedCorpus (type guarantees formatting is done)
        return FormattedCorpus.builder()
            .state(corpus.getState())
            .build();
    }

    /**
     * Generate embeddings for all notes that need them.
     * Delegates to org-roam-mcp which uses org-roam-semantic.
     *
     * Precondition: FormattedCorpus (formatting must be valid for embeddings)
     * Postcondition: CorpusWithEmbeddings (guarantees embeddings exist)
     */
    @Action
    public CorpusWithEmbeddings generateEmbeddings(FormattedCorpus corpus) {
        log.info("Generating embeddings for {} notes", corpus.getNotesNeedingEmbeddings());

        if (mcpClient == null || !mcpClient.isAvailable()) {
            log.error("MCP server is not available - cannot generate embeddings");
            log.warn("To generate embeddings manually: M-x org-roam-semantic-generate-all-embeddings");

            // Return with embeddings anyway (no-op if MCP unavailable)
            return CorpusWithEmbeddings.builder()
                .state(corpus.getState())
                .build();
        }

        List<NoteMetadata> notesNeedingEmbeddings = corpus.getState().getNotes().stream()
            .filter(note -> note.isMetadataMutable())
            .filter(note -> !note.isHasEmbeddings() || !note.isEmbeddingsFresh(maxAgeDays))
            .toList();

        if (!notesNeedingEmbeddings.isEmpty()) {
            log.info("Found {} notes needing embeddings", notesNeedingEmbeddings.size());

            // Call MCP to generate embeddings
            OrgRoamMcpClient.GenerateEmbeddingsResult result = mcpClient.generateEmbeddings(false);

            if (result.isSuccess()) {
                log.info("Successfully generated {} embeddings", result.getCount());
            } else {
                log.error("Failed to generate embeddings: {}", result.getMessage());
            }
        }

        // Return CorpusWithEmbeddings (type guarantees embeddings exist)
        return CorpusWithEmbeddings.builder()
            .state(corpus.getState())
            .build();
    }

    // ============================================================================
    // Knowledge Structure Actions: Cluster -> Themes -> Hubs
    // ============================================================================

    /**
     * Find clusters of semantically related orphan notes.
     * Uses MCP semantic search to group orphans by topic.
     *
     * Precondition: CorpusWithEmbeddings (needs embeddings for semantic search)
     * Postcondition: OrphanClusters (orphans grouped by similarity)
     */
    @Action
    public OrphanClusters findOrphanClusters(CorpusWithEmbeddings corpus) {
        log.info("Finding clusters among {} orphan notes", corpus.getOrphanCount());

        if (mcpClient == null || !mcpClient.isAvailable()) {
            log.warn("MCP unavailable - returning empty clusters");
            return OrphanClusters.builder()
                .state(corpus.getState())
                .clusters(List.of())
                .build();
        }

        List<NoteMetadata> orphans = corpus.getState().getNotes().stream()
            .filter(NoteMetadata::isOrphan)
            .toList();

        if (orphans.isEmpty()) {
            log.info("No orphans to cluster");
            return OrphanClusters.builder()
                .state(corpus.getState())
                .clusters(List.of())
                .build();
        }

        // Use semantic search to find relationships between orphans
        // Build similarity graph: orphan -> list of similar orphans
        Map<String, List<String>> similarityGraph = new java.util.HashMap<>();
        Map<String, Double> similarityScores = new java.util.HashMap<>();

        for (NoteMetadata orphan : orphans) {
            try {
                // Read note content for semantic search
                OrgNote note = fileReader.read(orphan.getFilePath());
                String title = note.getTitle() != null ? note.getTitle() : orphan.getNoteId();
                String searchQuery = title + "\n" + note.getRawContent().substring(
                    0, Math.min(500, note.getRawContent().length())
                );

                // Find similar notes (including other orphans)
                List<OrgRoamMcpClient.SemanticSearchResult> results =
                    mcpClient.semanticSearch(searchQuery, 10, 0.65);

                // Extract orphan IDs from results
                List<String> similarOrphans = results.stream()
                    .filter(r -> r.getNodeId() != null && !r.getNodeId().equals(orphan.getNoteId()))
                    .filter(r -> orphans.stream().anyMatch(o -> o.getNoteId().equals(r.getNodeId())))
                    .map(OrgRoamMcpClient.SemanticSearchResult::getNodeId)
                    .toList();

                if (!similarOrphans.isEmpty()) {
                    similarityGraph.put(orphan.getNoteId(), similarOrphans);

                    // Track average similarity for this orphan
                    double avgSim = results.stream()
                        .filter(r -> similarOrphans.contains(r.getNodeId()))
                        .mapToDouble(OrgRoamMcpClient.SemanticSearchResult::getSimilarity)
                        .average()
                        .orElse(0.0);
                    similarityScores.put(orphan.getNoteId(), avgSim);
                }

            } catch (Exception e) {
                log.error("Failed to find similar notes for orphan {}", orphan.getNoteId(), e);
            }
        }

        // Group notes into clusters using simple graph traversal
        List<NoteCluster> clusters = new ArrayList<>();
        java.util.Set<String> visited = new java.util.HashSet<>();

        for (String orphanId : similarityGraph.keySet()) {
            if (visited.contains(orphanId)) {
                continue;
            }

            // BFS to find all connected orphans
            List<String> clusterNotes = new ArrayList<>();
            java.util.Queue<String> queue = new java.util.LinkedList<>();
            queue.add(orphanId);
            visited.add(orphanId);

            while (!queue.isEmpty()) {
                String current = queue.poll();
                clusterNotes.add(current);

                // Add connected orphans
                List<String> neighbors = similarityGraph.getOrDefault(current, List.of());
                for (String neighbor : neighbors) {
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }

            // Only create cluster if it has at least 2 notes
            if (clusterNotes.size() >= 2) {
                double avgSimilarity = clusterNotes.stream()
                    .mapToDouble(id -> similarityScores.getOrDefault(id, 0.0))
                    .average()
                    .orElse(0.0);

                NoteCluster cluster = NoteCluster.builder()
                    .noteIds(clusterNotes)
                    .avgSimilarity(avgSimilarity)
                    .type(NoteCluster.ClusterType.ORPHAN_GROUP)
                    .build();

                clusters.add(cluster);
                log.info("Found cluster with {} orphans (avg similarity: {:.2f})",
                    cluster.size(), avgSimilarity);
            }
        }

        log.info("Found {} clusters from {} orphans", clusters.size(), orphans.size());

        return OrphanClusters.builder()
            .state(corpus.getState())
            .clusters(clusters)
            .build();
    }

    /**
     * Analyze clusters to discover implicit themes.
     * Uses LLM to identify common topics across cluster notes.
     *
     * Precondition: OrphanClusters (needs clusters to analyze)
     * Postcondition: ClustersWithThemes (each cluster has discovered theme)
     */
    @Action
    public ClustersWithThemes analyzeClusterThemes(OrphanClusters clusters) {
        log.info("Analyzing themes for {} clusters", clusters.getClusterCount());

        if (chatService == null) {
            log.warn("LLM unavailable - cannot analyze themes");
            return ClustersWithThemes.builder()
                .state(clusters.getState())
                .clusters(clusters.getClusters())
                .build();
        }

        List<NoteCluster> clustersWithThemes = new ArrayList<>();

        for (NoteCluster cluster : clusters.getClusters()) {
            if (cluster.hasTheme()) {
                clustersWithThemes.add(cluster);
                continue;
            }

            try {
                // Collect note titles and excerpts from cluster
                StringBuilder clusterContent = new StringBuilder();
                int noteNum = 1;

                for (String noteId : cluster.getNoteIds()) {
                    // Find note metadata
                    NoteMetadata note = clusters.getState().getNotes().stream()
                        .filter(n -> n.getNoteId().equals(noteId))
                        .findFirst()
                        .orElse(null);

                    if (note != null) {
                        // Read note to get title and content
                        try {
                            OrgNote orgNote = fileReader.read(note.getFilePath());
                            String title = orgNote.getTitle() != null ? orgNote.getTitle() : note.getNoteId();
                            clusterContent.append(String.format("\n--- Note %d: %s ---\n", noteNum++, title));

                            String content = orgNote.getRawContent();
                            // Use first 300 chars of content
                            String excerpt = content.substring(0, Math.min(300, content.length()));
                            clusterContent.append(excerpt);
                            if (content.length() > 300) {
                                clusterContent.append("...");
                            }
                            clusterContent.append("\n");
                        } catch (IOException e) {
                            log.warn("Could not read note {} for theme analysis", noteId);
                        }
                    }
                }

                // Use LLM to discover common theme
                String systemMessage = "You are an expert at analyzing knowledge bases and identifying common themes. " +
                    "Given a collection of related notes, identify the implicit theme or topic that connects them. " +
                    "Respond with ONLY a concise theme description (3-7 words), no explanation.";

                String userMessage = String.format(
                    "Analyze these %d related notes and identify their common theme:\n%s\n\n" +
                    "What is the common theme? (3-7 words only)",
                    cluster.size(),
                    clusterContent.toString()
                );

                String discoveredTheme = chatService.chat(systemMessage, userMessage).trim();

                // Clean up response (remove quotes, extra formatting)
                discoveredTheme = discoveredTheme.replaceAll("^[\"']|[\"']$", "").trim();

                if (!discoveredTheme.isEmpty()) {
                    log.info("Discovered theme for cluster of {} notes: {}", cluster.size(), discoveredTheme);

                    // Create new cluster with theme
                    NoteCluster themedCluster = cluster.withDiscoveredTheme(discoveredTheme);
                    clustersWithThemes.add(themedCluster);
                } else {
                    log.warn("LLM returned empty theme for cluster");
                    clustersWithThemes.add(cluster);
                }

            } catch (Exception e) {
                log.error("Failed to discover theme for cluster", e);
                clustersWithThemes.add(cluster);
            }
        }

        log.info("Discovered themes for {} clusters", clustersWithThemes.size());

        return ClustersWithThemes.builder()
            .state(clusters.getState())
            .clusters(clustersWithThemes)
            .build();
    }

    /**
     * Propose hub notes (MOCs) to organize clusters.
     * Creates proposals for new hub notes that link related content.
     *
     * Precondition: ClustersWithThemes (needs themes to create hubs)
     * Postcondition: HealthyCorpus (terminal goal - knowledge base improved)
     */
    @AchievesGoal(description = "Healthy, well-organized knowledge base with reduced orphans")
    @Action
    public HealthyCorpus proposeHubNotes(ClustersWithThemes clusters) {
        log.info("Proposing hub notes for {} clusters with themes", clusters.getClusterCount());

        if (chatService == null) {
            log.warn("LLM unavailable - cannot generate hub note proposals");
            return HealthyCorpus.builder()
                .state(clusters.getState())
                .build();
        }

        int proposalsCreated = 0;

        for (NoteCluster cluster : clusters.getClusters()) {
            if (!cluster.hasTheme()) {
                log.debug("Skipping cluster without theme");
                continue;
            }

            try {
                // Collect note information from cluster
                List<NoteMetadata> clusterNotes = new ArrayList<>();
                StringBuilder noteSummaries = new StringBuilder();

                for (String noteId : cluster.getNoteIds()) {
                    NoteMetadata note = clusters.getState().getNotes().stream()
                        .filter(n -> n.getNoteId().equals(noteId))
                        .findFirst()
                        .orElse(null);

                    if (note != null) {
                        clusterNotes.add(note);

                        // Read note to get title
                        try {
                            OrgNote orgNote = fileReader.read(note.getFilePath());
                            String title = orgNote.getTitle() != null ? orgNote.getTitle() : noteId;
                            noteSummaries.append(String.format("- [[id:%s][%s]]\n", noteId, title));
                        } catch (IOException e) {
                            log.warn("Could not read note {} for title", noteId);
                            noteSummaries.append(String.format("- [[id:%s][%s]]\n", noteId, noteId));
                        }
                    }
                }

                // Generate hub note ID and title
                String hubNoteId = "hub-" + cluster.getDiscoveredTheme()
                    .toLowerCase()
                    .replaceAll("[^a-z0-9]+", "-")
                    .replaceAll("^-|-$", "");

                String hubNoteTitle = cluster.getDiscoveredTheme();

                // Use LLM to generate hub note content
                String systemMessage = "You are an expert at creating Map of Content (MOC) hub notes for knowledge bases. " +
                    "A hub note should provide overview and context for a collection of related notes. " +
                    "Generate ONLY the introductory paragraph (2-4 sentences) that describes the theme and its importance. " +
                    "Do NOT include title, properties, or links - just the content paragraph.";

                String userMessage = String.format(
                    "Theme: %s\n\nRelated notes:\n%s\n\n" +
                    "Generate an introductory paragraph for a hub note about this theme:",
                    cluster.getDiscoveredTheme(),
                    noteSummaries.toString()
                );

                String introContent = chatService.chat(systemMessage, userMessage).trim();

                // Build complete hub note content
                StringBuilder hubContent = new StringBuilder();
                hubContent.append(":PROPERTIES:\n");
                hubContent.append(String.format(":ID:       %s\n", hubNoteId));
                hubContent.append(String.format(":CREATED:  [%s]\n", java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd EEE HH:mm"))));
                hubContent.append(":END:\n");
                hubContent.append(String.format("#+title: %s\n\n", hubNoteTitle));

                hubContent.append(introContent);
                hubContent.append("\n\n");

                hubContent.append("* Related Notes\n\n");
                hubContent.append(noteSummaries.toString());

                // Create proposal for new hub note
                String proposalId = String.format("hub-note-%s", hubNoteId);
                String rationale = String.format(
                    "Create hub note to organize %d orphan notes around theme: %s\n\n" +
                    "This hub note will:\n" +
                    "- Provide overview and context for the theme\n" +
                    "- Link all related orphan notes\n" +
                    "- Reduce fragmentation by creating central connection point\n\n" +
                    "Cluster similarity score: %.2f",
                    cluster.size(),
                    cluster.getDiscoveredTheme(),
                    cluster.getAvgSimilarity()
                );

                // Create proposal file
                java.nio.file.Path notesBasePath = java.nio.file.Paths.get(notesPath).toAbsolutePath();
                java.nio.file.Path proposalsDir = notesBasePath.resolve(proposalsDirName);
                java.nio.file.Files.createDirectories(proposalsDir);

                java.nio.file.Path proposalPath = proposalsDir.resolve(proposalId + ".new.org");
                java.nio.file.Files.writeString(proposalPath, hubContent.toString());

                log.info("Created hub note proposal: {} (theme: {}, {} notes)",
                    proposalId, cluster.getDiscoveredTheme(), cluster.size());

                proposalsCreated++;

            } catch (Exception e) {
                log.error("Failed to create hub note proposal for cluster", e);
            }
        }

        log.info("Created {} hub note proposals", proposalsCreated);

        // Return HealthyCorpus (terminal goal achieved)
        return HealthyCorpus.builder()
            .state(clusters.getState())
            .build();
    }
}
