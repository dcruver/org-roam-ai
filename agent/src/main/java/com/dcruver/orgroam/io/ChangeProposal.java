package com.dcruver.orgroam.io;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonCreator
    public ChangeProposal(
            @JsonProperty("id") String id,
            @JsonProperty("noteId") String noteId,
            @JsonProperty("filePath") String filePath,
            @JsonProperty("actionName") String actionName,
            @JsonProperty("rationale") String rationale,
            @JsonProperty("proposedAt") Instant proposedAt,
            @JsonProperty("status") ProposalStatus status,
            @JsonProperty("beforeStats") Map<String, Object> beforeStats,
            @JsonProperty("afterStats") Map<String, Object> afterStats,
            @JsonProperty("patchContent") String patchContent) {
        this.id = id;
        this.noteId = noteId;
        this.filePath = filePath;
        this.actionName = actionName;
        this.rationale = rationale;
        this.proposedAt = proposedAt;
        this.status = status;
        this.beforeStats = beforeStats;
        this.afterStats = afterStats;
        this.patchContent = patchContent;
    }

    public enum ProposalStatus {
        PENDING,
        APPROVED,
        REJECTED,
        APPLIED
    }
}
