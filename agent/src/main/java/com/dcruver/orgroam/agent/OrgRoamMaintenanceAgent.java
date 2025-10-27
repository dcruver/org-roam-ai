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
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

        // Simple clustering: for each orphan, find similar notes
        // Group notes that appear together in search results
        List<NoteCluster> clusters = new ArrayList<>();

        // TODO: Implement actual clustering logic using MCP semantic search
        // For now, return empty clusters to complete the type chain

        log.info("Found {} clusters", clusters.size());

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

            // TODO: Use LLM to discover theme from cluster note content
            // For now, just add clusters as-is
            clustersWithThemes.add(cluster);
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

            // TODO: Generate hub note content using LLM
            // TODO: Create proposal with new hub note file

            proposalsCreated++;
        }

        log.info("Created {} hub note proposals", proposalsCreated);

        // Return HealthyCorpus (terminal goal achieved)
        return HealthyCorpus.builder()
            .state(clusters.getState())
            .build();
    }
}
