package com.dcruver.orgroam.domain.goals;

import com.dcruver.orgroam.domain.CorpusState;
import com.embabel.agent.core.goal.Goal;
import com.embabel.agent.core.goal.GoalStatus;
import org.springframework.stereotype.Component;

/**
 * GOAP Goal: Reduce knowledge fragmentation by identifying and connecting
 * clusters of related orphan notes.
 *
 * This goal is satisfied when:
 * 1. All notes have embeddings (prerequisite)
 * 2. Orphan clusters have been discovered
 * 3. Either: no significant orphan clusters exist, OR hub notes have been proposed for them
 */
@Component
public class ReduceFragmentationGoal implements Goal<CorpusState> {

    private static final int ORPHAN_CLUSTER_THRESHOLD = 3;  // Min notes to form significant cluster

    @Override
    public String getName() {
        return "ReduceFragmentation";
    }

    @Override
    public String getDescription() {
        return "Identify and connect clusters of related orphan notes to reduce knowledge fragmentation";
    }

    @Override
    public GoalStatus evaluate(CorpusState state) {
        // Prerequisite: notes must have embeddings to do semantic clustering
        if (state.getNotesNeedingEmbeddings() > 0) {
            return GoalStatus.BLOCKED;  // Can't proceed without embeddings
        }

        // Check if fragmentation analysis has been done
        if (state.getOrphanClusters() == null) {
            return GoalStatus.UNSATISFIED;  // Need to discover clusters first
        }

        // Check if there are significant orphan clusters without hub notes
        long unaddressedClusters = state.getOrphanClusters().stream()
            .filter(cluster -> cluster.size() >= ORPHAN_CLUSTER_THRESHOLD)
            .filter(cluster -> !cluster.hasTheme())  // No theme = not analyzed yet
            .count();

        if (unaddressedClusters > 0) {
            return GoalStatus.UNSATISFIED;
        }

        // Check if hub note candidates have been generated for clusters with themes
        long clustersNeedingHubs = state.getOrphanClusters().stream()
            .filter(cluster -> cluster.size() >= ORPHAN_CLUSTER_THRESHOLD)
            .filter(cluster -> cluster.hasTheme())
            .count();

        if (clustersNeedingHubs > 0 && (state.getHubCandidates() == null || state.getHubCandidates().isEmpty())) {
            return GoalStatus.UNSATISFIED;  // Themes discovered but no hub proposals yet
        }

        return GoalStatus.SATISFIED;
    }

    @Override
    public int getPriority() {
        return 90;  // High priority - fragmentation hurts discoverability
    }
}
