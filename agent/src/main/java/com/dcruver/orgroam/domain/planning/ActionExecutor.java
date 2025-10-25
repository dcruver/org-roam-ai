package com.dcruver.orgroam.domain.planning;

import com.dcruver.orgroam.domain.CorpusState;
import com.dcruver.orgroam.domain.actions.ComputeEmbeddingsAction;
import com.dcruver.orgroam.domain.actions.NormalizeFormattingAction;
import com.dcruver.orgroam.domain.actions.SuggestLinksAction;
import com.dcruver.orgroam.nlp.OrgRoamMcpClient;
import com.embabel.agent.core.action.Action;
import com.embabel.agent.core.action.ActionResult;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes GOAP action plans.
 * Handles safe actions (auto-apply) and proposal actions (create proposals).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ActionExecutor {

    private final ComputeEmbeddingsAction computeEmbeddingsAction;
    private final NormalizeFormattingAction normalizeFormattingAction;
    private final SuggestLinksAction suggestLinksAction;

    @Autowired(required = false)
    private OrgRoamMcpClient mcpClient;

    /**
     * Execute all actions in the plan.
     *
     * @param plan The action plan to execute
     * @param state The current corpus state
     * @param safeOnly If true, only execute safe actions
     * @return Execution results
     */
    public ExecutionResult executePlan(ActionPlan plan, CorpusState state, boolean safeOnly) {
        log.info("Executing plan with {} actions (safe only: {})", plan.getActions().size(), safeOnly);

        List<ActionExecution> executions = new ArrayList<>();
        CorpusState currentState = state;
        int successCount = 0;
        int failureCount = 0;
        int skippedCount = 0;

        for (PlannedAction plannedAction : plan.getActions()) {
            // Skip proposal actions if safe-only mode
            if (safeOnly && !plannedAction.isSafe()) {
                log.info("Skipping proposal action '{}' in safe-only mode", plannedAction.getActionName());
                skippedCount++;
                continue;
            }

            try {
                // Get the actual action implementation
                Action<CorpusState> action = getActionByName(plannedAction.getActionName());

                if (action == null) {
                    log.error("Action '{}' not found", plannedAction.getActionName());
                    executions.add(ActionExecution.builder()
                        .actionName(plannedAction.getActionName())
                        .success(false)
                        .message("Action implementation not found")
                        .build());
                    failureCount++;
                    continue;
                }

                // Check if action can still execute (conditions may have changed)
                if (!action.canExecute(currentState)) {
                    log.info("Action '{}' can no longer execute - preconditions not met", action.getName());
                    executions.add(ActionExecution.builder()
                        .actionName(action.getName())
                        .success(false)
                        .skipped(true)
                        .message("Preconditions no longer met")
                        .build());
                    skippedCount++;
                    continue;
                }

                // Execute the action
                log.info("Executing action '{}'...", action.getName());
                ActionResult<CorpusState> result = action.execute(currentState);

                // Record execution
                ActionExecution execution = ActionExecution.builder()
                    .actionName(action.getName())
                    .success(result.isSuccess())
                    .message(result.getMessage())
                    .build();

                executions.add(execution);

                if (result.isSuccess()) {
                    successCount++;
                    log.info("Action '{}' completed successfully: {}", action.getName(), result.getMessage());

                    // Update state if provided
                    if (result.getResultingState() != null) {
                        currentState = result.getResultingState();
                    }
                } else {
                    failureCount++;
                    log.error("Action '{}' failed: {}", action.getName(), result.getMessage());
                }

            } catch (Exception e) {
                log.error("Exception executing action '{}'", plannedAction.getActionName(), e);
                executions.add(ActionExecution.builder()
                    .actionName(plannedAction.getActionName())
                    .success(false)
                    .message("Exception: " + e.getMessage())
                    .build());
                failureCount++;
            }
        }

        log.info("Plan execution completed: {} succeeded, {} failed, {} skipped",
            successCount, failureCount, skippedCount);

        ExecutionResult result = ExecutionResult.builder()
            .executions(executions)
            .finalState(currentState)
            .successCount(successCount)
            .failureCount(failureCount)
            .skippedCount(skippedCount)
            .build();

        // Write summary to daily note
        writeDailyReport(result, plan);

        return result;
    }

    /**
     * Write execution summary to today's daily note via MCP.
     */
    private void writeDailyReport(ExecutionResult result, ActionPlan plan) {
        if (mcpClient == null || !mcpClient.isAvailable()) {
            log.debug("MCP not available - skipping daily report");
            return;
        }

        try {
            // Format timestamp
            String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

            // Build summary points
            List<String> points = new ArrayList<>();
            points.add(String.format("Executed %d actions: %d succeeded, %d failed, %d skipped",
                plan.getActions().size(), result.getSuccessCount(), result.getFailureCount(), result.getSkippedCount()));

            // Add details for each action
            for (ActionExecution exec : result.getExecutions()) {
                String status = exec.isSuccess() ? "✓" : "✗";
                points.add(String.format("%s %s: %s", status, exec.getActionName(), exec.getMessage()));
            }

            // Health improvement
            if (result.getFinalState() != null) {
                points.add(String.format("Final corpus health: %.1f/90", result.getFinalState().getMeanHealthScore()));
            }

            // Next steps (if any failures or proposals)
            List<String> nextSteps = new ArrayList<>();
            if (result.getFailureCount() > 0) {
                nextSteps.add("Review failed actions in agent logs");
            }
            long proposalCount = plan.getActions().stream().filter(a -> !a.isSafe()).count();
            if (proposalCount > 0) {
                nextSteps.add(String.format("Review %d pending proposals", proposalCount));
            }

            // Write to daily note
            boolean success = mcpClient.addDailyEntry(
                timestamp,
                "org-roam-agent: Nightly Audit Complete",
                points,
                nextSteps.isEmpty() ? null : nextSteps,
                List.of("agent", "automated")
            );

            if (success) {
                log.info("Audit summary written to daily note");
            } else {
                log.warn("Failed to write audit summary to daily note");
            }

        } catch (Exception e) {
            log.error("Error writing daily report", e);
        }
    }

    /**
     * Get action implementation by name.
     */
    private Action<CorpusState> getActionByName(String name) {
        return switch (name) {
            case "ComputeEmbeddings" -> computeEmbeddingsAction;
            case "NormalizeFormatting" -> normalizeFormattingAction;
            case "SuggestLinks" -> suggestLinksAction;
            default -> {
                log.warn("Unknown action name: {}", name);
                yield null;
            }
        };
    }

    /**
     * Result of executing a single action.
     */
    @Data
    @Builder
    public static class ActionExecution {
        private String actionName;
        private boolean success;
        private boolean skipped;
        private String message;
    }

    /**
     * Result of executing an entire plan.
     */
    @Data
    @Builder
    public static class ExecutionResult {
        private List<ActionExecution> executions;
        private CorpusState finalState;
        private int successCount;
        private int failureCount;
        private int skippedCount;

        public boolean hasFailures() {
            return failureCount > 0;
        }

        public boolean allSucceeded() {
            return failureCount == 0 && successCount > 0;
        }
    }
}
