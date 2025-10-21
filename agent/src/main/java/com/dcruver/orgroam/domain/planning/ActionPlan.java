package com.dcruver.orgroam.domain.planning;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Represents a plan of actions to execute to achieve goals.
 */
@Data
@Builder
public class ActionPlan {
    private final List<PlannedAction> actions;
    private final double totalCost;
    private final String rationale;

    /**
     * Count safe actions (auto-applicable)
     */
    public long countSafeActions() {
        return actions.stream()
            .filter(PlannedAction::isSafe)
            .count();
    }

    /**
     * Count proposal actions (require human approval)
     */
    public long countProposalActions() {
        return actions.stream()
            .filter(a -> !a.isSafe())
            .count();
    }

    /**
     * Get safe actions only
     */
    public List<PlannedAction> getSafeActions() {
        return actions.stream()
            .filter(PlannedAction::isSafe)
            .toList();
    }

    /**
     * Get proposal actions only
     */
    public List<PlannedAction> getProposalActions() {
        return actions.stream()
            .filter(a -> !a.isSafe())
            .toList();
    }
}
