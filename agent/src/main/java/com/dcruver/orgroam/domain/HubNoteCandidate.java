package com.dcruver.orgroam.domain;

import lombok.Builder;
import lombok.Data;
import lombok.With;

import java.util.List;

/**
 * A proposal to create a hub note (MOC) to organize a cluster of related notes.
 */
@Data
@Builder
@With
public class HubNoteCandidate {
    private final String proposedTitle;  // Suggested title for hub note
    private final String proposedContent;  // LLM-generated hub note content
    private final List<String> linkedNoteIds;  // Notes that would be linked from hub
    private final String rationale;  // Why this hub note would improve structure
    private final HubType type;  // What kind of hub this is
    private final double priority;  // How important this hub is (based on cluster size, isolation)

    public enum HubType {
        MOC,                // Map of Content (broad topic overview)
        CONCEPT_INDEX,      // Index of related concepts
        DOMAIN_BRIDGE       // Connects notes across different domains
    }

    public int linkCount() {
        return linkedNoteIds.size();
    }
}
