package com.dcruver.orgroam.domain.planning;

import com.dcruver.orgroam.domain.CorpusState;
import com.embabel.agent.core.action.Action;
import com.embabel.agent.core.goal.Goal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Real GOAP (Goal-Oriented Action Planning) planner.
 * Uses backward chaining from goals to generate intelligent execution plans.
 *
 * This is the main planner interface used by the shell commands.
 * It coordinates between goals, actions, and the backward-chaining algorithm.
 */
@Component
@Slf4j
public class GOAPPlanner {

    private final BackwardChainingPlanner backwardChainingPlanner;
    private final List<Goal<CorpusState>> goals;
    private final List<Action<CorpusState>> actions;

    @Value("${gardener.target-health:90}")
    private int targetHealth;

    /**
     * Constructor that auto-discovers all goals and actions via Spring
     */
    public GOAPPlanner(
            BackwardChainingPlanner backwardChainingPlanner,
            List<Goal<CorpusState>> goals,
            List<Action<CorpusState>> actions) {
        this.backwardChainingPlanner = backwardChainingPlanner;
        this.goals = goals;
        this.actions = actions;

        log.info("GOAPPlanner initialized with {} goals and {} actions",
            goals.size(), actions.size());
        goals.forEach(g -> log.info("  Goal: {} (priority: {})", g.getName(), g.getPriority()));
        actions.forEach(a -> log.info("  Action: {}", a.getName()));
    }

    /**
     * Generate an action plan for the given corpus state using real GOAP backward chaining.
     */
    public ActionPlan generatePlan(CorpusState state) {
        log.info("Generating GOAP plan for corpus (mean health: {}, target: {})",
            state.getMeanHealthScore(), targetHealth);

        // Use backward chaining from goals
        List<PlannedAction> plannedActions = backwardChainingPlanner.planForGoals(
            goals,
            actions,
            state
        );

        // Calculate total cost
        double totalCost = plannedActions.stream()
            .mapToDouble(PlannedAction::getCost)
            .sum();

        // Sort: safe actions first (they're prerequisites), then by execution order
        plannedActions.sort((a, b) -> {
            if (a.isSafe() != b.isSafe()) {
                return a.isSafe() ? -1 : 1;  // Safe actions first
            }
            return 0;  // Maintain backward-chaining order within safe/proposal groups
        });

        String planRationale = generatePlanRationale(state, plannedActions);

        log.info("Generated GOAP plan with {} actions (total cost: {})",
            plannedActions.size(), totalCost);

        return ActionPlan.builder()
            .actions(plannedActions)
            .totalCost(totalCost)
            .rationale(planRationale)
            .build();
    }

    /**
     * Generate overall plan rationale
     */
    private String generatePlanRationale(CorpusState state, List<PlannedAction> actions) {
        if (actions.isEmpty()) {
            return String.format("Corpus health is %.1f/%d. All goals satisfied - no actions needed.",
                state.getMeanHealthScore(), targetHealth);
        }

        double healthGap = targetHealth - state.getMeanHealthScore();
        long safeCount = actions.stream().filter(PlannedAction::isSafe).count();
        long proposalCount = actions.size() - safeCount;

        // List which goals are being addressed
        StringBuilder goalSummary = new StringBuilder();
        goalSummary.append("Addressing goals: ");
        actions.stream()
            .map(PlannedAction::getRationale)
            .filter(r -> r != null && r.contains("goal:"))
            .map(r -> r.substring(r.indexOf("goal:") + 6).trim())
            .distinct()
            .forEach(goal -> goalSummary.append(goal).append(", "));

        String goalText = goalSummary.toString().replaceAll(", $", "");

        return String.format(
            "Corpus health is %.1f/%d (%.1f points below target). " +
            "Generated %d actions via backward chaining: %d safe (auto-apply), %d proposals (require approval). " +
            "%s",
            state.getMeanHealthScore(), targetHealth, healthGap,
            actions.size(), safeCount, proposalCount,
            goalText
        );
    }
}
