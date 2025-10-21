package com.dcruver.orgroam.io;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a proposed change to a note.
 * Stored as JSON for review and approval.
 */
@Data
@Builder
public class ChangeProposal {
    private final String id;
    private final String noteId;
    private final String filePath;
    private final String actionName;
    private final String rationale;
    private final Instant proposedAt;
    private final ProposalStatus status;

    // Before/after stats
    private final Map<String, Object> beforeStats;
    private final Map<String, Object> afterStats;

    // The actual patch content
    private final String patchContent;

    public enum ProposalStatus {
        PENDING,
        APPROVED,
        REJECTED,
        APPLIED
    }
}
