package com.dcruver.orgroam.app;

import com.dcruver.orgroam.domain.CorpusScanner;
import com.dcruver.orgroam.domain.CorpusState;
import com.dcruver.orgroam.domain.planning.ActionExecutor;
import com.dcruver.orgroam.domain.planning.ActionPlan;
import com.dcruver.orgroam.domain.planning.GOAPPlanner;
import com.dcruver.orgroam.domain.planning.PlannedAction;
import com.dcruver.orgroam.io.ChangeProposal;
import com.dcruver.orgroam.io.PatchWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.List;

/**
 * Spring Shell commands for the org-roam gardener.
 */
@ShellComponent
@Slf4j
@RequiredArgsConstructor
public class GardenerShellCommands {

    private final PatchWriter patchWriter;
    private final CorpusScanner corpusScanner;
    private final GOAPPlanner goapPlanner;
    private final ActionExecutor actionExecutor;

    @Value("${gardener.target-health:90}")
    private int targetHealth;

    // Cached state and plan from last audit
    private CorpusState lastCorpusState;
    private ActionPlan lastPlan;

    @ShellMethod(key = {"audit", "audit now"}, value = "Trigger immediate audit and produce plan")
    public String audit() {
        log.info("Running corpus audit...");

        try {
            // 1. Scan all notes in notes-path
            log.info("Scanning corpus...");
            lastCorpusState = corpusScanner.scanCorpus();

            // 2. Run GOAP planner to produce action plan
            log.info("Generating action plan...");
            lastPlan = goapPlanner.generatePlan(lastCorpusState);

            // 3. Format results
            StringBuilder result = new StringBuilder();
            result.append("Audit completed.\n\n");

            result.append("Corpus Statistics:\n");
            result.append(String.format("- Total notes: %d\n", lastCorpusState.getTotalNotes()));
            result.append(String.format("- Mean health score: %.1f / %d\n",
                lastCorpusState.getMeanHealthScore(), targetHealth));
            result.append(String.format("- Notes needing embeddings: %d\n",
                lastCorpusState.getNotesNeedingEmbeddings()));
            result.append(String.format("- Format issues: %d\n",
                lastCorpusState.getNotesWithFormatIssues()));
            result.append(String.format("- Orphan notes: %d\n",
                lastCorpusState.getOrphanNotes()));
            result.append(String.format("- Stale notes: %d\n",
                lastCorpusState.getStaleNotes()));
            result.append("\n");

            result.append(String.format("Plan: %d actions recommended\n\n",
                lastPlan.getActions().size()));

            if (!lastPlan.getActions().isEmpty()) {
                result.append("Recommended Actions:\n");
                for (int i = 0; i < lastPlan.getActions().size(); i++) {
                    PlannedAction action = lastPlan.getActions().get(i);
                    result.append(String.format("%d. [%s] %s\n",
                        i + 1,
                        action.isSafe() ? "SAFE" : "PROPOSAL",
                        action.getActionName()));
                    result.append(String.format("   %s\n", action.getRationale()));
                    result.append(String.format("   Affects %d notes (cost: %.1f)\n",
                        action.getAffectedNotes(), action.getCost()));
                }
                result.append("\n");

                result.append(lastPlan.getRationale()).append("\n\n");
                result.append(String.format("Safe actions: %d (auto-apply in auto mode)\n",
                    lastPlan.countSafeActions()));
                result.append(String.format("Proposals: %d (require human approval)\n\n",
                    lastPlan.countProposalActions()));

                result.append("Run 'execute' to execute the plan or 'apply safe' for safe actions only.\n");
            } else {
                result.append(lastPlan.getRationale()).append("\n");
            }

            return result.toString();

        } catch (Exception e) {
            log.error("Audit failed", e);
            return "Audit failed: " + e.getMessage();
        }
    }

    @ShellMethod(key = {"execute", "x"}, value = "Execute the current plan")
    public String execute() {
        log.info("Executing plan...");

        try {
            // Check if we have a plan
            if (lastPlan == null || lastCorpusState == null) {
                return "No plan available. Run 'audit' first to generate a plan.";
            }

            if (lastPlan.getActions().isEmpty()) {
                return "Plan is empty - no actions to execute.";
            }

            // Execute the plan
            ActionExecutor.ExecutionResult result = actionExecutor.executePlan(
                lastPlan,
                lastCorpusState,
                false  // Execute all actions (safe + proposals)
            );

            // Update cached state
            lastCorpusState = result.getFinalState();

            // Build result message
            StringBuilder sb = new StringBuilder();
            sb.append("Plan execution completed.\n\n");

            sb.append(String.format("Actions executed: %d\n",
                result.getSuccessCount() + result.getFailureCount()));
            sb.append(String.format("- Succeeded: %d\n", result.getSuccessCount()));
            sb.append(String.format("- Failed: %d\n", result.getFailureCount()));
            sb.append(String.format("- Skipped: %d\n\n", result.getSkippedCount()));

            // Show details of each execution
            for (ActionExecutor.ActionExecution execution : result.getExecutions()) {
                String status = execution.isSuccess() ? "✓" :
                               execution.isSkipped() ? "⊘" : "✗";
                sb.append(String.format("%s %s: %s\n",
                    status,
                    execution.getActionName(),
                    execution.getMessage()));
            }

            sb.append("\nRun 'status' to see updated corpus health.\n");
            sb.append("Run 'proposals list' to review proposals.\n");

            return sb.toString();

        } catch (Exception e) {
            log.error("Execution failed", e);
            return "Execution failed: " + e.getMessage();
        }
    }

    @ShellMethod(key = "apply safe", value = "Apply all safe actions from current plan")
    public String applySafe() {
        log.info("Applying safe actions only...");

        try {
            // Check if we have a plan
            if (lastPlan == null || lastCorpusState == null) {
                return "No plan available. Run 'audit' first to generate a plan.";
            }

            if (lastPlan.getActions().isEmpty()) {
                return "Plan is empty - no actions to execute.";
            }

            long safeCount = lastPlan.countSafeActions();
            if (safeCount == 0) {
                return "No safe actions in plan. All actions require human approval.";
            }

            // Execute safe actions only
            ActionExecutor.ExecutionResult result = actionExecutor.executePlan(
                lastPlan,
                lastCorpusState,
                true  // Safe actions only
            );

            // Update cached state
            lastCorpusState = result.getFinalState();

            // Build result message
            StringBuilder sb = new StringBuilder();
            sb.append("Safe actions applied.\n\n");

            sb.append(String.format("Actions executed: %d\n",
                result.getSuccessCount() + result.getFailureCount()));
            sb.append(String.format("- Succeeded: %d\n", result.getSuccessCount()));
            sb.append(String.format("- Failed: %d\n", result.getFailureCount()));
            sb.append(String.format("- Skipped (proposals): %d\n\n", result.getSkippedCount()));

            // Show details of safe actions
            for (ActionExecutor.ActionExecution execution : result.getExecutions()) {
                if (!execution.isSkipped()) {
                    String status = execution.isSuccess() ? "✓" : "✗";
                    sb.append(String.format("%s %s: %s\n",
                        status,
                        execution.getActionName(),
                        execution.getMessage()));
                }
            }

            sb.append("\nRun 'status' to see updated corpus health.\n");

            if (result.getSkippedCount() > 0) {
                sb.append(String.format("Note: %d proposal actions skipped.\n", result.getSkippedCount()));
                sb.append("Run 'execute' to process proposals, or 'proposals list' to review them.\n");
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("Safe action application failed", e);
            return "Failed: " + e.getMessage();
        }
    }

    @ShellMethod(key = "proposals list", value = "List all pending proposals")
    public String proposalsList() {
        try {
            List<ChangeProposal> proposals = patchWriter.listProposals();

            if (proposals.isEmpty()) {
                return "No pending proposals.";
            }

            StringBuilder sb = new StringBuilder("Pending Proposals:\n\n");

            for (ChangeProposal proposal : proposals) {
                sb.append(String.format("ID: %s\n", proposal.getId()));
                sb.append(String.format("  Note: %s\n", proposal.getNoteId()));
                sb.append(String.format("  Action: %s\n", proposal.getActionName()));
                sb.append(String.format("  Proposed: %s\n", proposal.getProposedAt()));
                sb.append(String.format("  Rationale: %s\n",
                    truncate(proposal.getRationale(), 100)));
                sb.append("\n");
            }

            sb.append(String.format("\nTotal: %d proposals\n", proposals.size()));
            sb.append("Use 'proposals show <id>' to see details\n");
            sb.append("Use 'proposals apply <id>' to apply a proposal\n");

            return sb.toString();

        } catch (Exception e) {
            log.error("Failed to list proposals", e);
            return "Failed to list proposals: " + e.getMessage();
        }
    }

    @ShellMethod(key = "proposals show", value = "Show details of a specific proposal")
    public String proposalsShow(@ShellOption String id) {
        try {
            ChangeProposal proposal = patchWriter.getProposal(id);

            if (proposal == null) {
                return "Proposal not found: " + id;
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Proposal: %s\n\n", proposal.getId()));
            sb.append(String.format("Note: %s\n", proposal.getNoteId()));
            sb.append(String.format("File: %s\n", proposal.getFilePath()));
            sb.append(String.format("Action: %s\n", proposal.getActionName()));
            sb.append(String.format("Status: %s\n", proposal.getStatus()));
            sb.append(String.format("Proposed: %s\n\n", proposal.getProposedAt()));

            sb.append("Rationale:\n");
            sb.append(proposal.getRationale()).append("\n\n");

            sb.append("Before Stats:\n");
            proposal.getBeforeStats().forEach((k, v) ->
                sb.append(String.format("  %s: %s\n", k, v)));

            sb.append("\nAfter Stats:\n");
            proposal.getAfterStats().forEach((k, v) ->
                sb.append(String.format("  %s: %s\n", k, v)));

            sb.append("\nPatch:\n");
            sb.append(proposal.getPatchContent());

            return sb.toString();

        } catch (Exception e) {
            log.error("Failed to show proposal", e);
            return "Failed to show proposal: " + e.getMessage();
        }
    }

    @ShellMethod(key = "proposals apply", value = "Apply a specific proposal")
    public String proposalsApply(@ShellOption String id) {
        try {
            // TODO: Implement proposal application
            // 1. Get proposal
            // 2. Apply patch
            // 3. Mark as applied

            return "Proposal application not yet implemented";

        } catch (Exception e) {
            log.error("Failed to apply proposal", e);
            return "Failed to apply proposal: " + e.getMessage();
        }
    }

    @ShellMethod(key = {"report", "report daily"}, value = "Generate daily Org report")
    public String report() {
        log.info("Generating daily report...");

        try {
            // TODO: Implement report generation
            // Use reporting module to generate Org-formatted report

            return """
                Daily Report (TBD)

                Generated report saved to: TBD
                """;

        } catch (Exception e) {
            log.error("Report generation failed", e);
            return "Failed to generate report: " + e.getMessage();
        }
    }

    @ShellMethod(key = "status", value = "Show current corpus health status")
    public String status() {
        try {
            // Scan corpus to get current state
            log.info("Checking corpus status...");
            CorpusState state = corpusScanner.scanCorpus();

            StringBuilder result = new StringBuilder();
            result.append("Corpus Health Status\n\n");

            result.append(String.format("Overall Health: %.1f / %d\n\n",
                state.getMeanHealthScore(), targetHealth));

            result.append("Statistics:\n");
            result.append(String.format("- Total notes: %d\n", state.getTotalNotes()));
            result.append(String.format("- With embeddings: %d (fresh) + %d (stale)\n",
                state.getNotesWithEmbeddings() - state.getNotesWithStaleEmbeddings(),
                state.getNotesWithStaleEmbeddings()));
            result.append(String.format("- Missing embeddings: %d\n",
                state.getTotalNotes() - state.getNotesWithEmbeddings()));
            result.append(String.format("- With format issues: %d\n",
                state.getNotesWithFormatIssues()));
            result.append(String.format("- Orphan notes: %d\n", state.getOrphanNotes()));
            result.append(String.format("- Stale notes: %d\n", state.getStaleNotes()));
            result.append("\n");

            double healthGap = targetHealth - state.getMeanHealthScore();
            result.append(String.format("Target health: %d\n", targetHealth));
            result.append(String.format("Improvement needed: %.1f points\n\n",
                Math.max(0, healthGap)));

            if (healthGap > 0) {
                result.append("Run 'audit' to generate improvement plan.\n");
            } else {
                result.append("Corpus health meets target! 🎉\n");
            }

            return result.toString();

        } catch (Exception e) {
            log.error("Status check failed", e);
            return "Failed to get status: " + e.getMessage();
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
