package com.dcruver.orgroam.domain.planning;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a single action in a plan with metadata.
 */
@Data
@Builder
public class PlannedAction {
    private final String actionName;
    private final String description;
    private final double cost;
    private final boolean safe;  // True if auto-applicable, false if requires approval
    private final String rationale;  // Why this action is recommended
    private final int affectedNotes;  // Number of notes this action would affect
}
