package com.dcruver.orgroam.domain.planning;

import com.dcruver.orgroam.domain.CorpusState;
import com.dcruver.orgroam.domain.actions.ComputeEmbeddingsAction;
import com.dcruver.orgroam.domain.actions.NormalizeFormattingAction;
import com.dcruver.orgroam.domain.actions.SuggestLinksAction;
import com.embabel.agent.core.action.Action;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple GOAP (Goal-Oriented Action Planning) planner.
 * Evaluates available actions and generates an execution plan.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class GOAPPlanner {

    private final ComputeEmbeddingsAction computeEmbeddingsAction;
    private final NormalizeFormattingAction normalizeFormattingAction;
    private final SuggestLinksAction suggestLinksAction;

    @Value("${gardener.target-health:90}")
    private int targetHealth;

    /**
     * Generate an action plan for the given corpus state.
     * Returns actions sorted by priority (safe actions first, then by cost).
     */
    public ActionPlan generatePlan(CorpusState state) {
        log.info("Generating action plan for corpus (mean health: {}, target: {})",
            state.getMeanHealthScore(), targetHealth);

        List<PlannedAction> plannedActions = new ArrayList<>();
        double totalCost = 0.0;

        // List of all available actions
        List<Action<CorpusState>> availableActions = List.of(
            normalizeFormattingAction,  // Highest priority - fixes basic structure
            computeEmbeddingsAction,    // Next - enables semantic search
            suggestLinksAction          // Last - requires embeddings
        );

        // Evaluate each action
        for (Action<CorpusState> action : availableActions) {
            if (action.canExecute(state)) {
                double cost = action.getCost(state);

                PlannedAction plannedAction = PlannedAction.builder()
                    .actionName(action.getName())
                    .description(action.getDescription())
                    .cost(cost)
                    .safe(isSafeAction(action))
                    .rationale(generateRationale(action, state))
                    .affectedNotes(estimateAffectedNotes(action, state))
                    .build();

                plannedActions.add(plannedAction);
                totalCost += cost;

                log.info("Action '{}' is executable (cost: {}, safe: {}, affects {} notes)",
                    action.getName(), cost, plannedAction.isSafe(), plannedAction.getAffectedNotes());
            } else {
                log.debug("Action '{}' cannot execute - preconditions not met", action.getName());
            }
        }

        // Sort: safe actions first, then by cost (lower cost first)
        plannedActions.sort((a, b) -> {
            if (a.isSafe() != b.isSafe()) {
                return a.isSafe() ? -1 : 1;  // Safe actions first
            }
            return Double.compare(a.getCost(), b.getCost());  // Then by cost
        });

        String planRationale = generatePlanRationale(state, plannedActions);

        log.info("Generated plan with {} actions (total cost: {})", plannedActions.size(), totalCost);

        return ActionPlan.builder()
            .actions(plannedActions)
            .totalCost(totalCost)
            .rationale(planRationale)
            .build();
    }

    /**
     * Check if an action is safe (auto-applicable)
     */
    private boolean isSafeAction(Action<CorpusState> action) {
        // Safe actions: ComputeEmbeddings, NormalizeFormatting
        // Proposal actions: SuggestLinks
        String name = action.getName();
        return name.equals("ComputeEmbeddings") || name.equals("NormalizeFormatting");
    }

    /**
     * Generate rationale for why this action should be executed
     */
    private String generateRationale(Action<CorpusState> action, CorpusState state) {
        return switch (action.getName()) {
            case "ComputeEmbeddings" -> {
                int needsEmbeddings = state.getNotesNeedingEmbeddings();
                yield String.format("Compute embeddings for %d notes (missing or stale)", needsEmbeddings);
            }
            case "NormalizeFormatting" -> {
                int formatIssues = state.getNotesWithFormatIssues();
                yield String.format("Fix formatting issues in %d notes", formatIssues);
            }
            case "SuggestLinks" -> {
                int orphans = state.getOrphanNotes();
                yield String.format("Suggest links for %d orphan notes to improve connectivity", orphans);
            }
            default -> action.getDescription();
        };
    }

    /**
     * Estimate how many notes this action would affect
     */
    private int estimateAffectedNotes(Action<CorpusState> action, CorpusState state) {
        return switch (action.getName()) {
            case "ComputeEmbeddings" -> state.getNotesNeedingEmbeddings();
            case "NormalizeFormatting" -> state.getNotesWithFormatIssues();
            case "SuggestLinks" -> state.getOrphanNotes();
            default -> 0;
        };
    }

    /**
     * Generate overall plan rationale
     */
    private String generatePlanRationale(CorpusState state, List<PlannedAction> actions) {
        if (actions.isEmpty()) {
            return String.format("Corpus health is %.1f/%d. No actions needed at this time.",
                state.getMeanHealthScore(), targetHealth);
        }

        double healthGap = targetHealth - state.getMeanHealthScore();
        long safeCount = actions.stream().filter(PlannedAction::isSafe).count();
        long proposalCount = actions.size() - safeCount;

        return String.format(
            "Corpus health is %.1f/%d (%.1f points below target). " +
            "Generated %d actions: %d safe (auto-apply), %d proposals (require approval).",
            state.getMeanHealthScore(), targetHealth, healthGap,
            actions.size(), safeCount, proposalCount
        );
    }
}
