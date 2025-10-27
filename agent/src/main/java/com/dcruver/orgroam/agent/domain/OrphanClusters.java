package com.dcruver.orgroam.agent.domain;

import com.dcruver.orgroam.domain.CorpusState;
import com.dcruver.orgroam.domain.NoteCluster;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Corpus with orphan notes clustered by semantic similarity.
 * Result of clustering analysis - prerequisite for theme discovery.
 */
@Value
@Builder
public class OrphanClusters {
    CorpusState state;
    List<NoteCluster> clusters;

    public int getClusterCount() {
        return clusters != null ? clusters.size() : 0;
    }

    public boolean hasClusters() {
        return clusters != null && !clusters.isEmpty();
    }

    /**
     * Count clusters without discovered themes
     */
    public long getClustersNeedingAnalysis() {
        if (clusters == null) return 0;
        return clusters.stream()
            .filter(c -> !c.hasTheme())
            .count();
    }
}
