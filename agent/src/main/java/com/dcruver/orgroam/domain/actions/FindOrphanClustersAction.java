package com.dcruver.orgroam.domain.actions;

import com.dcruver.orgroam.domain.CorpusState;
import com.dcruver.orgroam.domain.NoteCluster;
import com.dcruver.orgroam.domain.NoteMetadata;
import com.dcruver.orgroam.nlp.OrgRoamMcpClient;
import com.dcruver.orgroam.nlp.OrgRoamMcpClient.SemanticSearchResponse;
import com.embabel.agent.core.action.Action;
import com.embabel.agent.core.action.ActionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GOAP Action: Find clusters of semantically related orphan notes.
 *
 * This action discovers implicit groupings by:
 * 1. Getting all orphan notes (notes with no links)
 * 2. For each orphan, finding semantically similar orphans via MCP
 * 3. Clustering orphans that are mutually similar
 *
 * Preconditions: All notes have embeddings
 * Effects: state.orphanClusters is populated
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FindOrphanClustersAction implements Action<CorpusState> {

    private final OrgRoamMcpClient mcpClient;

    private static final double SIMILARITY_THRESHOLD = 0.75;  // Min similarity to cluster
    private static final int MIN_CLUSTER_SIZE = 3;  // Min notes to form a cluster
    private static final int MAX_SIMILAR_NOTES = 10;  // How many similar notes to check per orphan

    @Override
    public String getName() {
        return "FindOrphanClusters";
    }

    @Override
    public String getDescription() {
        return "Discover clusters of semantically related orphan notes";
    }

    @Override
    public boolean canExecute(CorpusState state) {
        // Precondition: all notes must have embeddings
        if (state.getNotesNeedingEmbeddings() > 0) {
            log.debug("Cannot find orphan clusters: {} notes still need embeddings",
                state.getNotesNeedingEmbeddings());
            return false;
        }

        // Must have orphan notes to cluster
        if (state.getOrphanNotes() <= 0) {
            log.debug("Cannot find orphan clusters: no orphan notes");
            return false;
        }

        // Don't re-run if clusters already discovered
        if (state.getOrphanClusters() != null && !state.getOrphanClusters().isEmpty()) {
            log.debug("Orphan clusters already discovered");
            return false;
        }

        return true;
    }

    @Override
    public double estimateCost(CorpusState state) {
        // Cost is proportional to number of orphans (semantic search calls)
        int orphanCount = state.getOrphanNotes();
        return orphanCount * 2.0;  // 2 units per orphan (semantic search is cheap)
    }

    @Override
    public ActionResult<CorpusState> execute(CorpusState state) {
        log.info("Finding clusters of orphan notes...");

        List<NoteMetadata> orphans = state.getOrphanNotes();
        if (orphans.isEmpty()) {
            log.info("No orphan notes to cluster");
            return ActionResult.success(state, "No orphan notes found");
        }

        log.info("Analyzing {} orphan notes for semantic clusters", orphans.size());

        // Build similarity graph
        Map<String, List<SimilarNote>> similarityGraph = buildSimilarityGraph(orphans);

        // Find clusters using connected components
        List<NoteCluster> clusters = extractClusters(similarityGraph);

        log.info("Discovered {} orphan clusters", clusters.size());

        // Update state with discovered clusters
        CorpusState newState = state.withOrphanClusters(clusters)
            .withTotalOrphanClusters(clusters.size());

        String message = String.format("Found %d clusters of orphaned notes (min size %d, similarity > %.2f)",
            clusters.size(), MIN_CLUSTER_SIZE, SIMILARITY_THRESHOLD);

        return ActionResult.success(newState, message);
    }

    /**
     * Build a graph of orphan notes and their semantic similarities
     */
    private Map<String, List<SimilarNote>> buildSimilarityGraph(List<NoteMetadata> orphans) {
        Map<String, List<SimilarNote>> graph = new HashMap<>();

        for (NoteMetadata orphan : orphans) {
            try {
                // Use MCP semantic search to find similar notes
                SemanticSearchResponse response = mcpClient.semanticSearch(
                    orphan.getContent(),
                    MAX_SIMILAR_NOTES,
                    SIMILARITY_THRESHOLD
                );

                List<SimilarNote> similarOrphans = new ArrayList<>();
                for (var similar : response.getNotes()) {
                    // Only include if it's also an orphan and above threshold
                    if (isOrphan(similar.getId(), orphans) && similar.getSimilarity() >= SIMILARITY_THRESHOLD) {
                        similarOrphans.add(new SimilarNote(similar.getId(), similar.getSimilarity()));
                    }
                }

                graph.put(orphan.getNoteId(), similarOrphans);

            } catch (Exception e) {
                log.warn("Failed to find similar notes for {}: {}", orphan.getNoteId(), e.getMessage());
                graph.put(orphan.getNoteId(), List.of());
            }
        }

        return graph;
    }

    /**
     * Extract clusters from similarity graph using connected components algorithm
     */
    private List<NoteCluster> extractClusters(Map<String, List<SimilarNote>> graph) {
        List<NoteCluster> clusters = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        for (String noteId : graph.keySet()) {
            if (!visited.contains(noteId)) {
                List<String> cluster = new ArrayList<>();
                double totalSimilarity = 0.0;
                int edgeCount = 0;

                // DFS to find connected component
                dfs(noteId, graph, visited, cluster, (similarity) -> {
                    // Accumulate similarity scores
                    return similarity;
                });

                // Calculate average similarity within cluster
                for (String id : cluster) {
                    for (SimilarNote similar : graph.get(id)) {
                        if (cluster.contains(similar.id)) {
                            totalSimilarity += similar.similarity;
                            edgeCount++;
                        }
                    }
                }

                double avgSimilarity = edgeCount > 0 ? totalSimilarity / edgeCount : 0.0;

                // Only keep clusters that meet minimum size
                if (cluster.size() >= MIN_CLUSTER_SIZE) {
                    clusters.add(NoteCluster.builder()
                        .noteIds(cluster)
                        .avgSimilarity(avgSimilarity)
                        .type(NoteCluster.ClusterType.ORPHAN_GROUP)
                        .build());
                }
            }
        }

        return clusters;
    }

    private void dfs(String noteId, Map<String, List<SimilarNote>> graph,
                     Set<String> visited, List<String> cluster,
                     java.util.function.Function<Double, Double> similarityCollector) {
        visited.add(noteId);
        cluster.add(noteId);

        for (SimilarNote similar : graph.getOrDefault(noteId, List.of())) {
            if (!visited.contains(similar.id)) {
                similarityCollector.apply(similar.similarity);
                dfs(similar.id, graph, visited, cluster, similarityCollector);
            }
        }
    }

    private boolean isOrphan(String noteId, List<NoteMetadata> orphans) {
        return orphans.stream().anyMatch(o -> o.getNoteId().equals(noteId));
    }

    @Override
    public boolean isSafe() {
        return true;  // Discovery action - doesn't modify files
    }

    private record SimilarNote(String id, double similarity) {}
}
