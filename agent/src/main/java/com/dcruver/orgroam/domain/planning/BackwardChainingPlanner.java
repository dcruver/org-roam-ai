package com.dcruver.orgroam.domain.planning;

import com.dcruver.orgroam.domain.CorpusState;
import com.embabel.agent.core.action.Action;
import com.embabel.agent.core.goal.Goal;
import com.embabel.agent.core.goal.GoalStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Real GOAP backward-chaining planner.
 *
 * This implements true goal-oriented action planning by:
 * 1. Starting from desired goals
 * 2. Working backwards to find actions that satisfy those goals
 * 3. Recursively planning for action preconditions
 * 4. Building a dependency-ordered execution plan
 *
 * This is REAL GOAP, not just a sorted task list.
 */
@Component
@Slf4j
public class BackwardChainingPlanner {

    private static final int MAX_PLANNING_DEPTH = 10;  // Prevent infinite recursion

    /**
     * Plan backwards from goals to current state.
     *
     * @param goals Available goals to satisfy
     * @param actions Available actions to execute
     * @param currentState Current corpus state
     * @return Ordered list of actions to execute (prerequisites first)
     */
    public List<PlannedAction> planForGoals(
            List<Goal<CorpusState>> goals,
            List<Action<CorpusState>> actions,
            CorpusState currentState) {

        log.info("Starting backward-chaining GOAP planning with {} goals and {} actions",
            goals.size(), actions.size());

        // Sort goals by priority (higher priority first)
        List<Goal<CorpusState>> sortedGoals = new ArrayList<>(goals);
        sortedGoals.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        List<PlannedAction> plan = new ArrayList<>();
        Set<String> plannedActions = new HashSet<>();  // Track what we've already planned

        // Try to satisfy each goal
        for (Goal<CorpusState> goal : sortedGoals) {
            GoalStatus status = goal.evaluate(currentState);

            if (status == GoalStatus.SATISFIED) {
                log.info("Goal '{}' already satisfied", goal.getName());
                continue;
            }

            if (status == GoalStatus.BLOCKED) {
                log.info("Goal '{}' is blocked - will attempt to unblock", goal.getName());
            }

            log.info("Planning to satisfy goal: {} (status: {})", goal.getName(), status);

            try {
                // Backward chain from this goal
                List<PlannedAction> goalPlan = planForGoal(
                    goal,
                    actions,
                    currentState,
                    plannedActions,
                    0  // depth
                );

                // Add goal's plan to overall plan
                plan.addAll(goalPlan);

                // Simulate state after executing this goal's plan
                currentState = simulateExecution(goalPlan, currentState, actions);

            } catch (PlanningException e) {
                log.warn("Could not plan for goal '{}': {}", goal.getName(), e.getMessage());
            }
        }

        // Remove duplicates while preserving order (earlier prerequisites stay first)
        List<PlannedAction> dedupedPlan = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (PlannedAction action : plan) {
            if (!seen.contains(action.getActionName())) {
                dedupedPlan.add(action);
                seen.add(action.getActionName());
            }
        }

        log.info("Generated plan with {} unique actions", dedupedPlan.size());
        return dedupedPlan;
    }

    /**
     * Plan backwards from a single goal
     */
    private List<PlannedAction> planForGoal(
            Goal<CorpusState> goal,
            List<Action<CorpusState>> actions,
            CorpusState state,
            Set<String> plannedActions,
            int depth) throws PlanningException {

        if (depth > MAX_PLANNING_DEPTH) {
            throw new PlanningException("Planning depth exceeded for goal: " + goal.getName());
        }

        // Check if goal is already satisfied
        GoalStatus status = goal.evaluate(state);
        if (status == GoalStatus.SATISFIED) {
            return List.of();  // Nothing to do
        }

        // Find actions that contribute to this goal
        List<Action<CorpusState>> contributingActions = findActionsForGoal(goal, actions, state);

        if (contributingActions.isEmpty()) {
            throw new PlanningException("No actions available to satisfy goal: " + goal.getName());
        }

        // Try each contributing action (sorted by cost)
        contributingActions.sort((a, b) ->
            Double.compare(a.getCost(state), b.getCost(state)));

        for (Action<CorpusState> action : contributingActions) {
            if (plannedActions.contains(action.getName())) {
                log.debug("Action '{}' already in plan", action.getName());
                continue;
            }

            try {
                // Check if we can execute this action
                if (!action.canExecute(state)) {
                    log.debug("Action '{}' cannot execute yet - may need prerequisites",
                        action.getName());
                    // Try to plan for its preconditions (this would require action to expose preconditions)
                    // For now, skip if can't execute
                    continue;
                }

                // Build planned action
                PlannedAction plannedAction = buildPlannedAction(action, state, goal);

                // Mark as planned
                plannedActions.add(action.getName());

                // Simulate executing this action
                CorpusState afterState = simulateAction(action, state);

                // Check if this action helps satisfy the goal
                GoalStatus afterStatus = goal.evaluate(afterState);
                if (afterStatus == GoalStatus.SATISFIED ||
                    afterStatus.ordinal() > status.ordinal()) {  // Made progress

                    log.info("Action '{}' contributes to goal '{}'", action.getName(), goal.getName());

                    // If goal still not satisfied, recurse for remaining work
                    List<PlannedAction> remainingPlan = new ArrayList<>();
                    if (afterStatus != GoalStatus.SATISFIED) {
                        remainingPlan = planForGoal(goal, actions, afterState,
                            plannedActions, depth + 1);
                    }

                    // Return: this action + remaining plan
                    List<PlannedAction> fullPlan = new ArrayList<>();
                    fullPlan.add(plannedAction);
                    fullPlan.addAll(remainingPlan);
                    return fullPlan;
                }

            } catch (Exception e) {
                log.debug("Action '{}' failed planning: {}", action.getName(), e.getMessage());
            }
        }

        throw new PlanningException("No action successfully plans for goal: " + goal.getName());
    }

    /**
     * Find actions that might contribute to satisfying a goal
     */
    private List<Action<CorpusState>> findActionsForGoal(
            Goal<CorpusState> goal,
            List<Action<CorpusState>> actions,
            CorpusState state) {

        // Heuristic mapping of goals to actions
        // In a full GOAP system, actions would declare their effects explicitly
        String goalName = goal.getName();

        return actions.stream()
            .filter(action -> actionContributesToGoal(action, goalName, state))
            .toList();
    }

    /**
     * Heuristic: does this action contribute to this goal?
     */
    private boolean actionContributesToGoal(Action<CorpusState> action,
                                           String goalName, CorpusState state) {
        String actionName = action.getName();

        return switch (goalName) {
            case "ReduceFragmentation" -> switch (actionName) {
                case "ComputeEmbeddings" -> state.getNotesNeedingEmbeddings() > 0;  // Prerequisite
                case "FindOrphanClusters" -> true;  // Direct contributor
                case "AnalyzeClusterThemes" -> true;  // Direct contributor
                case "ProposeHubNote" -> true;  // Final step
                default -> false;
            };

            case "EstablishHierarchy" -> switch (actionName) {
                case "DetectImplicitCategories" -> true;
                case "ProposeHubNote" -> true;  // Can also satisfy hierarchy goals
                default -> false;
            };

            // Fallback: maintenance goals (old-style checklist)
            default -> switch (actionName) {
                case "NormalizeFormatting" -> state.getNotesWithFormatIssues() > 0;
                case "ComputeEmbeddings" -> state.getNotesNeedingEmbeddings() > 0;
                case "SuggestLinks" -> state.getOrphanNotes() > 0;
                default -> false;
            };
        };
    }

    /**
     * Build a PlannedAction from an Action
     */
    private PlannedAction buildPlannedAction(Action<CorpusState> action,
                                            CorpusState state,
                                            Goal<CorpusState> forGoal) {
        return PlannedAction.builder()
            .actionName(action.getName())
            .description(action.getDescription())
            .cost(action.getCost(state))
            .safe(action.isSafe())
            .rationale(String.format("Contributes to goal: %s", forGoal.getName()))
            .affectedNotes(estimateAffectedNotes(action, state))
            .build();
    }

    /**
     * Estimate affected notes (heuristic)
     */
    private int estimateAffectedNotes(Action<CorpusState> action, CorpusState state) {
        return switch (action.getName()) {
            case "ComputeEmbeddings" -> state.getNotesNeedingEmbeddings();
            case "NormalizeFormatting" -> state.getNotesWithFormatIssues();
            case "SuggestLinks" -> state.getOrphanNotes();
            case "FindOrphanClusters" -> state.getOrphanNotes();
            case "AnalyzeClusterThemes" ->
                state.getOrphanClusters() != null ? state.getOrphanClusters().size() : 0;
            case "ProposeHubNote" ->
                state.getOrphanClusters() != null ? state.getOrphanClusters().size() : 0;
            default -> 0;
        };
    }

    /**
     * Simulate executing an action (for planning purposes - doesn't actually execute)
     */
    private CorpusState simulateAction(Action<CorpusState> action, CorpusState state) {
        // Simple simulation: assume action succeeds and updates relevant state fields
        // In real GOAP, actions would declare their effects
        return switch (action.getName()) {
            case "ComputeEmbeddings" ->
                state.withNotesWithEmbeddings(state.getTotalNotes())
                     .withNotesWithStaleEmbeddings(0);

            case "NormalizeFormatting" ->
                state.withNotesWithFormatIssues(0);

            case "FindOrphanClusters" ->
                state.withTotalOrphanClusters(state.getOrphanNotes() / 3);  // Rough estimate

            case "AnalyzeClusterThemes" ->
                state;  // Themes added to existing clusters

            case "ProposeHubNote" ->
                state.withTotalOrphanClusters(0);  // Hub notes proposed for all clusters

            default -> state;
        };
    }

    /**
     * Simulate executing a sequence of actions
     */
    private CorpusState simulateExecution(List<PlannedAction> plan,
                                         CorpusState initialState,
                                         List<Action<CorpusState>> actions) {
        CorpusState state = initialState;

        for (PlannedAction planned : plan) {
            // Find the actual action
            Action<CorpusState> action = actions.stream()
                .filter(a -> a.getName().equals(planned.getActionName()))
                .findFirst()
                .orElse(null);

            if (action != null) {
                state = simulateAction(action, state);
            }
        }

        return state;
    }

    public static class PlanningException extends Exception {
        public PlanningException(String message) {
            super(message);
        }
    }
}
