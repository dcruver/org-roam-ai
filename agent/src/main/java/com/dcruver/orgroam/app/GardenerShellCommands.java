package com.dcruver.orgroam.app;

import com.dcruver.orgroam.agent.OrgRoamMaintenanceAgent;
import com.dcruver.orgroam.agent.domain.*;
import com.dcruver.orgroam.domain.CorpusScanner;
import com.dcruver.orgroam.domain.CorpusState;
import com.dcruver.orgroam.io.ChangeProposal;
import com.dcruver.orgroam.io.PatchWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.List;

/**
 * Spring Shell commands for the org-roam gardener.
 * Now using Embabel GOAP agent for automatic planning.
 */
@ShellComponent
@Slf4j
@RequiredArgsConstructor
public class GardenerShellCommands {

    private final PatchWriter patchWriter;
    private final CorpusScanner corpusScanner;
    private final com.dcruver.orgroam.domain.FormatCheckCache formatCheckCache;

    @Autowired(required = false)
    private final OrgRoamMaintenanceAgent agent;

    @Value("${gardener.target-health:90}")
    private int targetHealth;

    // Cached state from last audit
    private CorpusState lastCorpusState;

    @ShellMethod(key = {"audit", "audit now"}, value = "Trigger immediate audit and produce plan")
    public String audit() {
        log.info("Running corpus audit...");

        try {
            // Scan all notes in notes-path
            log.info("Scanning corpus...");
            lastCorpusState = corpusScanner.scanCorpus();

            // Format results
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
                lastCorpusState.getOrphanNotes() != null ? lastCorpusState.getOrphanNotes().size() : 0));
            result.append(String.format("- Stale notes: %d\n",
                lastCorpusState.getStaleNotes()));
            result.append("\n");

            // Show Embabel agent action chain
            result.append("Embabel Agent Action Chain:\n");
            result.append("The agent will execute the following type-based action sequence:\n\n");

            int actionNum = 1;
            if (lastCorpusState.getNotesWithFormatIssues() > 0) {
                result.append(String.format("%d. [SAFE] NormalizeFormatting → FormattedCorpus\n", actionNum++));
                result.append(String.format("   Fix formatting issues for %d notes\n",
                    lastCorpusState.getNotesWithFormatIssues()));
            }

            if (lastCorpusState.getNotesNeedingEmbeddings() > 0) {
                result.append(String.format("%d. [SAFE] GenerateEmbeddings → CorpusWithEmbeddings\n", actionNum++));
                result.append(String.format("   Generate embeddings for %d notes via MCP\n",
                    lastCorpusState.getNotesNeedingEmbeddings()));
            }

            int orphanCount = lastCorpusState.getOrphanNotes() != null ?
                lastCorpusState.getOrphanNotes().size() : 0;
            if (orphanCount > 0) {
                result.append(String.format("%d. [ANALYSIS] FindOrphanClusters → OrphanClusters\n", actionNum++));
                result.append(String.format("   Cluster %d orphan notes by semantic similarity\n", orphanCount));

                result.append(String.format("%d. [ANALYSIS] AnalyzeClusterThemes → ClustersWithThemes\n", actionNum++));
                result.append("   Use LLM to discover implicit themes in clusters\n");

                result.append(String.format("%d. [PROPOSAL] ProposeHubNotes → HealthyCorpus\n", actionNum++));
                result.append("   Generate MOC/hub note proposals for organizing knowledge\n");
            }

            if (actionNum == 1) {
                result.append("No actions needed - corpus is healthy!\n");
            }

            result.append("\nRun 'run' to run the agent's action chain.\n");
            result.append("Run 'run-safe' to run only safe (non-proposal) actions.\n");

            return result.toString();

        } catch (Exception e) {
            log.error("Audit failed", e);
            return "Audit failed: " + e.getMessage();
        }
    }

    @ShellMethod(key = {"run", "run-agent"}, value = "Execute the agent's action chain")
    public String runAgent() {
        log.info("Executing Embabel agent action chain...");

        try {
            if (agent == null) {
                return "Agent not available. Check that Embabel is properly configured.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Executing agent action chain...\n\n");

            // 1. Scan corpus
            log.info("Step 1: Scanning corpus");
            RawCorpus rawCorpus = agent.scanCorpus();
            sb.append("✓ Scanned corpus: ").append(rawCorpus.getTotalNotes()).append(" notes\n");

            // 2. Normalize formatting (if needed)
            if (rawCorpus.getNotesWithFormatIssues() > 0) {
                log.info("Step 2: Normalizing formatting for {} notes", rawCorpus.getNotesWithFormatIssues());
                FormattedCorpus formattedCorpus = agent.normalizeFormatting(rawCorpus);
                sb.append("✓ Normalized formatting: ").append(rawCorpus.getNotesWithFormatIssues()).append(" notes fixed\n");

                // 3. Generate embeddings (if needed)
                if (formattedCorpus.getNotesNeedingEmbeddings() > 0) {
                    log.info("Step 3: Generating embeddings for {} notes", formattedCorpus.getNotesNeedingEmbeddings());
                    CorpusWithEmbeddings embeddedCorpus = agent.generateEmbeddings(formattedCorpus);
                    sb.append("✓ Generated embeddings: ").append(formattedCorpus.getNotesNeedingEmbeddings()).append(" notes\n");

                    // 4. Find orphan clusters (if any orphans)
                    if (embeddedCorpus.getOrphanCount() > 0) {
                        log.info("Step 4: Finding clusters among {} orphans", embeddedCorpus.getOrphanCount());
                        OrphanClusters clusters = agent.findOrphanClusters(embeddedCorpus);
                        sb.append("✓ Found ").append(clusters.getClusterCount()).append(" orphan clusters\n");

                        // 5. Analyze cluster themes
                        if (clusters.hasClusters()) {
                            log.info("Step 5: Analyzing themes for {} clusters", clusters.getClusterCount());
                            ClustersWithThemes themedClusters = agent.analyzeClusterThemes(clusters);
                            sb.append("✓ Analyzed themes for ").append(themedClusters.getClusterCount()).append(" clusters\n");

                            // 6. Propose hub notes
                            log.info("Step 6: Proposing hub notes");
                            HealthyCorpus healthyCorpus = agent.proposeHubNotes(themedClusters);
                            sb.append("✓ Generated hub note proposals\n");
                        }
                    }
                }
            }

            sb.append("\nAgent execution completed!\n");
            sb.append("Run 'status' to see updated corpus health.\n");
            sb.append("Run 'proposals list' to review any proposals.\n");

            return sb.toString();

        } catch (Exception e) {
            log.error("Execution failed", e);
            return "Execution failed: " + e.getMessage();
        }
    }

    @ShellMethod(key = {"run-safe", "apply-safe"}, value = "Execute only safe (maintenance) actions")
    public String runSafe() {
        log.info("Executing safe actions only (no proposals)...");

        try {
            if (agent == null) {
                return "Agent not available. Check that Embabel is properly configured.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Executing safe maintenance actions...\n\n");

            // 1. Scan corpus
            log.info("Step 1: Scanning corpus");
            RawCorpus rawCorpus = agent.scanCorpus();
            sb.append("✓ Scanned corpus: ").append(rawCorpus.getTotalNotes()).append(" notes\n");

            int actionsRun = 0;

            // 2. Normalize formatting (SAFE)
            if (rawCorpus.getNotesWithFormatIssues() > 0) {
                log.info("Step 2: Normalizing formatting for {} notes", rawCorpus.getNotesWithFormatIssues());
                FormattedCorpus formattedCorpus = agent.normalizeFormatting(rawCorpus);
                sb.append("✓ Normalized formatting: ").append(rawCorpus.getNotesWithFormatIssues()).append(" notes fixed\n");
                actionsRun++;

                // 3. Generate embeddings (SAFE)
                if (formattedCorpus.getNotesNeedingEmbeddings() > 0) {
                    log.info("Step 3: Generating embeddings for {} notes", formattedCorpus.getNotesNeedingEmbeddings());
                    CorpusWithEmbeddings embeddedCorpus = agent.generateEmbeddings(formattedCorpus);
                    sb.append("✓ Generated embeddings: ").append(formattedCorpus.getNotesNeedingEmbeddings()).append(" notes\n");
                    actionsRun++;
                }
            }

            if (actionsRun == 0) {
                sb.append("No safe actions needed - formatting and embeddings are up to date!\n");
            }

            sb.append("\nSafe actions completed!\n");
            sb.append("Run 'status' to see updated corpus health.\n");
            sb.append("Run 'execute' to include knowledge structure analysis and proposals.\n");

            return sb.toString();

        } catch (Exception e) {
            log.error("Safe action execution failed", e);
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
            // Get proposal details for display
            ChangeProposal proposal = patchWriter.getProposal(id);
            if (proposal == null) {
                return "Proposal not found: " + id;
            }

            // Apply the proposal
            patchWriter.applyProposal(id);

            StringBuilder sb = new StringBuilder();
            sb.append("✓ Proposal applied successfully\n\n");
            sb.append(String.format("Proposal: %s\n", proposal.getId()));
            sb.append(String.format("Note: %s\n", proposal.getNoteId()));
            sb.append(String.format("File: %s\n", proposal.getFilePath()));
            sb.append(String.format("Action: %s\n\n", proposal.getActionName()));
            sb.append("A backup was created before applying the changes.\n");
            sb.append("The proposal has been marked as APPLIED.\n");

            return sb.toString();

        } catch (Exception e) {
            log.error("Failed to apply proposal", e);
            return "Failed to apply proposal: " + e.getMessage();
        }
    }

    @ShellMethod(key = "proposals apply-by-action", value = "Apply all proposals for a specific action type")
    public String proposalsApplyByAction(@ShellOption String actionName) {
        try {
            List<ChangeProposal> allProposals = patchWriter.listProposals();
            List<ChangeProposal> matchingProposals = allProposals.stream()
                .filter(p -> p.getActionName().equals(actionName))
                .toList();

            if (matchingProposals.isEmpty()) {
                return String.format("No pending proposals found for action: %s", actionName);
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Applying %d %s proposals...\n\n",
                matchingProposals.size(), actionName));

            int succeeded = 0;
            int failed = 0;

            for (ChangeProposal proposal : matchingProposals) {
                try {
                    patchWriter.applyProposal(proposal.getId());
                    sb.append(String.format("✓ %s (%s)\n",
                        proposal.getNoteId(), proposal.getFilePath()));
                    succeeded++;
                } catch (Exception e) {
                    sb.append(String.format("✗ %s: %s\n",
                        proposal.getNoteId(), e.getMessage()));
                    failed++;
                }
            }

            sb.append(String.format("\n%d applied, %d failed\n", succeeded, failed));

            return sb.toString();

        } catch (Exception e) {
            log.error("Failed to apply proposals by action", e);
            return "Failed to apply proposals: " + e.getMessage();
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
            result.append(String.format("- Orphan notes: %d\n",
                state.getOrphanNotes() != null ? state.getOrphanNotes().size() : 0));
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

    @ShellMethod(key = "cache stats", value = "Show format check cache statistics")
    public String cacheStats() {
        try {
            com.dcruver.orgroam.domain.FormatCheckCache.CacheStats stats = formatCheckCache.getStats();

            StringBuilder sb = new StringBuilder();
            sb.append("Format Check Cache Statistics\n\n");
            sb.append(String.format("Status: %s\n", stats.isEnabled() ? "Enabled" : "Disabled"));
            sb.append(String.format("Cache entries: %d\n", stats.getSize()));
            sb.append(String.format("- Format OK: %d\n", stats.getFormatOkCount()));
            sb.append(String.format("- Format issues: %d\n\n", stats.getFormatIssuesCount()));

            sb.append("Cache location: ~/.gardener/cache/format-check-cache.json\n\n");

            sb.append("Note: Cache entries are automatically invalidated when files are modified.\n");
            sb.append("Use 'cache clear' to force a full re-scan with LLM checks.\n");

            return sb.toString();

        } catch (Exception e) {
            log.error("Failed to get cache stats", e);
            return "Failed to get cache stats: " + e.getMessage();
        }
    }

    @ShellMethod(key = "cache clear", value = "Clear format check cache (force full re-scan)")
    public String cacheClear() {
        try {
            formatCheckCache.clearCache();
            return "Format check cache cleared. Next audit will perform full LLM analysis.";

        } catch (Exception e) {
            log.error("Failed to clear cache", e);
            return "Failed to clear cache: " + e.getMessage();
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
