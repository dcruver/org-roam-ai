package com.dcruver.orgroam.domain;

import lombok.Builder;
import lombok.Data;
import lombok.With;

import java.util.List;

/**
 * A cluster of semantically related notes discovered by analysis.
 * Used for identifying implicit groupings that lack organizational structure.
 */
@Data
@Builder
@With
public class NoteCluster {
    private final List<String> noteIds;  // IDs of notes in this cluster
    private final double avgSimilarity;  // Average cosine similarity within cluster
    private final String discoveredTheme;  // LLM-identified theme (null until analyzed)
    private final ClusterType type;  // What kind of cluster this is

    public enum ClusterType {
        ORPHAN_GROUP,        // Orphaned notes that belong together
        IMPLICIT_CATEGORY,   // Notes with common theme but no MOC
        CROSS_DOMAIN         // Notes bridging multiple domains
    }

    public int size() {
        return noteIds.size();
    }

    public boolean hasTheme() {
        return discoveredTheme != null && !discoveredTheme.isBlank();
    }
}
