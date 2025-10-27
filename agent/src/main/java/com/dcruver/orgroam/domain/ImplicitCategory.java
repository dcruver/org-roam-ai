package com.dcruver.orgroam.domain;

import lombok.Builder;
import lombok.Data;
import lombok.With;

import java.util.List;

/**
 * A discovered category/theme that appears across multiple notes
 * but has no explicit organizational structure (no MOC or hub note).
 */
@Data
@Builder
@With
public class ImplicitCategory {
    private final String categoryName;  // LLM-discovered category name
    private final List<String> noteIds;  // Notes belonging to this category
    private final String description;  // LLM explanation of what connects these notes
    private final double confidence;  // How confident the LLM is (0.0-1.0)
    private final boolean hasMOC;  // Whether a Map of Content exists for this category

    public int size() {
        return noteIds.size();
    }

    /**
     * Check if this category is significant enough to warrant a hub note.
     * Criteria: at least 5 notes, confidence > 0.7
     */
    public boolean isSignificant() {
        return noteIds.size() >= 5 && confidence > 0.7;
    }
}
