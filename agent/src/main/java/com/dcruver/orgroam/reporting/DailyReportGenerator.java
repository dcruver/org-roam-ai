package com.dcruver.orgroam.reporting;

import com.dcruver.orgroam.domain.CorpusState;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates daily Org-format reports of gardener activity.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DailyReportGenerator {

    @Value("${gardener.notes-path}")
    private String notesPath;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * Generate and save daily report
     */
    public Path generateReport(ReportData data) throws Exception {
        String date = LocalDate.now().format(DATE_FORMAT);
        String reportFilename = String.format("gardener-report-%s.org", date);
        Path reportPath = Paths.get(notesPath).resolve(reportFilename);

        String report = buildReport(data);

        Files.writeString(reportPath, report);
        log.info("Generated daily report: {}", reportPath);

        return reportPath;
    }

    /**
     * Build Org-format report content
     */
    private String buildReport(ReportData data) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append(":PROPERTIES:\n");
        sb.append(":ID:       ").append(java.util.UUID.randomUUID()).append("\n");
        sb.append(":CREATED:  [").append(LocalDate.now()).append("]\n");
        sb.append(":TAGS:     gardener report\n");
        sb.append(":END:\n");

        sb.append("* Org-Roam Gardener Daily Report - ").append(LocalDate.now()).append("\n\n");

        // Summary
        sb.append("** Summary\n\n");
        sb.append(String.format("- Total notes: %d\n", data.getTotalNotes()));
        sb.append(String.format("- Mean health score: %.1f / 100\n", data.getMeanHealthScore()));
        sb.append(String.format("- Health change: %+.1f\n\n", data.getHealthChange()));

        // Embeddings
        sb.append("** Embeddings\n\n");
        sb.append(String.format("- Updated: %d\n", data.getEmbeddingsUpdated()));
        sb.append(String.format("- Missing: %d\n", data.getEmbeddingsMissing()));
        sb.append(String.format("- Stale: %d\n\n", data.getEmbeddingsStale()));

        // Format
        sb.append("** Formatting\n\n");
        sb.append(String.format("- Fixes applied: %d\n", data.getFormatFixesApplied()));
        sb.append(String.format("- Remaining issues: %d\n\n", data.getFormatIssuesRemaining()));

        // Links
        sb.append("** Links\n\n");
        sb.append(String.format("- Auto-added: %d\n", data.getLinksAutoAdded()));
        sb.append(String.format("- Proposed: %d\n", data.getLinksProposed()));
        sb.append(String.format("- Orphan notes: %d\n\n", data.getOrphanNotes()));

        // Issues
        sb.append("** Issues\n\n");
        if (data.getIssues().isEmpty()) {
            sb.append("No issues reported.\n\n");
        } else {
            for (String issue : data.getIssues()) {
                sb.append("- ").append(issue).append("\n");
            }
            sb.append("\n");
        }

        // Actions Taken
        sb.append("** Actions Taken\n\n");
        if (data.getActionsTaken().isEmpty()) {
            sb.append("No actions taken.\n\n");
        } else {
            for (ActionLog action : data.getActionsTaken()) {
                sb.append(String.format("- %s: %s (cost: %.1f)\n",
                    action.getActionName(),
                    action.getDescription(),
                    action.getCost()));
            }
            sb.append("\n");
        }

        // Proposals
        sb.append("** Pending Proposals\n\n");
        sb.append(String.format("Total: %d proposals awaiting review\n\n", data.getPendingProposals()));

        // Footer
        sb.append("** Next Steps\n\n");
        sb.append("- Review pending proposals with =proposals list=\n");
        sb.append("- Run =audit= to check for new issues\n");
        sb.append("- Run =apply safe= to apply safe actions\n");

        return sb.toString();
    }

    /**
     * Data for report generation
     */
    @Data
    public static class ReportData {
        private int totalNotes;
        private double meanHealthScore;
        private double healthChange;

        private int embeddingsUpdated;
        private int embeddingsMissing;
        private int embeddingsStale;

        private int formatFixesApplied;
        private int formatIssuesRemaining;

        private int linksAutoAdded;
        private int linksProposed;
        private int orphanNotes;

        private List<String> issues;
        private List<ActionLog> actionsTaken;
        private int pendingProposals;
    }

    /**
     * Log of an action taken
     */
    @Data
    public static class ActionLog {
        private final String actionName;
        private final String description;
        private final double cost;
    }
}
