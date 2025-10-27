package com.dcruver.orgroam.agent.domain;

import com.dcruver.orgroam.domain.CorpusState;
import com.dcruver.orgroam.domain.NoteCluster;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Orphan clusters with LLM-discovered themes.
 * Ready for hub note generation - prerequisite for MOC proposals.
 */
@Value
@Builder
public class ClustersWithThemes {
    CorpusState state;
    List<NoteCluster> clusters;

    /**
     * Guarantee: all clusters have discovered themes
     */
    public boolean allClustersHaveThemes() {
        if (clusters == null || clusters.isEmpty()) return true;
        return clusters.stream().allMatch(NoteCluster::hasTheme);
    }

    public int getClusterCount() {
        return clusters != null ? clusters.size() : 0;
    }

    public List<NoteCluster> getClusters() {
        return clusters;
    }

    public CorpusState getState() {
        return state;
    }
}
