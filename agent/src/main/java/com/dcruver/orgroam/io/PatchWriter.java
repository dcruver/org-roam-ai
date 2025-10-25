package com.dcruver.orgroam.io;

import com.dcruver.orgroam.io.ChangeProposal.ProposalStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles patch generation, proposals, and backups for all note changes.
 */
@Component
@Slf4j
public class PatchWriter {

    private final ObjectMapper objectMapper;
    private final Path backupDir;
    private final Path proposalsDir;

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public PatchWriter(
        @Value("${gardener.notes-path}") String notesPath,
        @Value("${gardener.backup-dir:.gardener/backups}") String backupDirName,
        @Value("${gardener.proposals-dir:.gardener/proposals}") String proposalsDirName
    ) throws IOException {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        Path notesBasePath = Paths.get(notesPath).toAbsolutePath();
        this.backupDir = notesBasePath.resolve(backupDirName);
        this.proposalsDir = notesBasePath.resolve(proposalsDirName);

        // Ensure directories exist
        Files.createDirectories(backupDir);
        Files.createDirectories(proposalsDir);
    }

    /**
     * Create a backup of a file with timestamp
     */
    public Path createBackup(Path originalFile) throws IOException {
        if (!Files.exists(originalFile)) {
            log.warn("Cannot backup non-existent file: {}", originalFile);
            return null;
        }

        String timestamp = TIMESTAMP_FORMAT.format(Instant.now());
        String filename = originalFile.getFileName().toString();
        String backupFilename = filename + "." + timestamp + ".bak";
        Path backupFile = backupDir.resolve(backupFilename);

        Files.copy(originalFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
        log.info("Created backup: {}", backupFile);

        return backupFile;
    }

    /**
     * Generate a unified diff between two versions of content
     */
    public String generateDiff(String original, String revised, String noteId) {
        List<String> originalLines = original.lines().toList();
        List<String> revisedLines = revised.lines().toList();

        Patch<String> patch = DiffUtils.diff(originalLines, revisedLines);

        List<String> unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(
            "original/" + noteId,
            "revised/" + noteId,
            originalLines,
            patch,
            3  // context lines
        );

        return String.join("\n", unifiedDiff);
    }

    /**
     * Create a change proposal for human review
     */
    public ChangeProposal createProposal(
        String noteId,
        Path filePath,
        String actionName,
        String rationale,
        String originalContent,
        String revisedContent,
        Map<String, Object> beforeStats,
        Map<String, Object> afterStats
    ) throws IOException {
        String proposalId = UUID.randomUUID().toString();
        String diff = generateDiff(originalContent, revisedContent, noteId);

        ChangeProposal proposal = ChangeProposal.builder()
            .id(proposalId)
            .noteId(noteId)
            .filePath(filePath.toString())
            .actionName(actionName)
            .rationale(rationale)
            .proposedAt(Instant.now())
            .status(ProposalStatus.PENDING)
            .beforeStats(beforeStats)
            .afterStats(afterStats)
            .patchContent(diff)
            .build();

        // Write proposal JSON
        Path proposalFile = proposalsDir.resolve(noteId + "-" + proposalId + ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(proposalFile.toFile(), proposal);

        // Write patch file
        Path patchFile = proposalsDir.resolve(noteId + "-" + proposalId + ".patch");
        Files.writeString(patchFile, diff);

        log.info("Created proposal {} for note {}: {}", proposalId, noteId, actionName);

        return proposal;
    }

    /**
     * Check if a pending proposal already exists for a note and action.
     * This prevents duplicate proposals from being created.
     */
    public boolean hasExistingProposal(String noteId, String actionName) {
        try {
            if (!Files.exists(proposalsDir)) {
                log.debug("Proposals directory does not exist: {}", proposalsDir);
                return false;
            }

            try (var stream = Files.list(proposalsDir)) {
                boolean exists = stream
                    .filter(p -> p.toString().endsWith(".json"))
                    .filter(p -> {
                        String filename = p.getFileName().toString();
                        boolean matches = filename.startsWith(noteId + "-");
                        if (matches) {
                            log.debug("Found proposal file for note {}: {}", noteId, filename);
                        }
                        return matches;
                    })
                    .anyMatch(p -> {
                        try {
                            ChangeProposal proposal = objectMapper.readValue(p.toFile(), ChangeProposal.class);
                            boolean isPending = proposal != null
                                && proposal.getStatus() == ProposalStatus.PENDING
                                && proposal.getActionName().equals(actionName);
                            if (isPending) {
                                log.info("Found existing pending {} proposal for note {}", actionName, noteId);
                            }
                            return isPending;
                        } catch (IOException e) {
                            log.debug("Failed to read proposal file: {}", p, e);
                            return false;
                        }
                    });

                if (!exists) {
                    log.debug("No existing {} proposal found for note {}", actionName, noteId);
                }
                return exists;
            }
        } catch (IOException e) {
            log.error("Error checking for existing proposals for note {}", noteId, e);
            return false;
        }
    }

    /**
     * List all pending proposals
     */
    public List<ChangeProposal> listProposals() throws IOException {
        if (!Files.exists(proposalsDir)) {
            return List.of();
        }

        return Files.list(proposalsDir)
            .filter(p -> p.toString().endsWith(".json"))
            .map(p -> {
                try {
                    return objectMapper.readValue(p.toFile(), ChangeProposal.class);
                } catch (IOException e) {
                    log.error("Failed to read proposal: {}", p, e);
                    return null;
                }
            })
            .filter(p -> p != null && p.getStatus() == ProposalStatus.PENDING)
            .toList();
    }

    /**
     * Get a specific proposal by ID
     */
    public ChangeProposal getProposal(String proposalId) throws IOException {
        return Files.list(proposalsDir)
            .filter(p -> p.toString().endsWith(".json") && p.toString().contains(proposalId))
            .findFirst()
            .map(p -> {
                try {
                    return objectMapper.readValue(p.toFile(), ChangeProposal.class);
                } catch (IOException e) {
                    log.error("Failed to read proposal: {}", p, e);
                    return null;
                }
            })
            .orElse(null);
    }

    /**
     * Mark proposal as applied
     */
    public void markProposalApplied(String proposalId) throws IOException {
        ChangeProposal proposal = getProposal(proposalId);
        if (proposal == null) {
            throw new IOException("Proposal not found: " + proposalId);
        }

        ChangeProposal updated = ChangeProposal.builder()
            .id(proposal.getId())
            .noteId(proposal.getNoteId())
            .filePath(proposal.getFilePath())
            .actionName(proposal.getActionName())
            .rationale(proposal.getRationale())
            .proposedAt(proposal.getProposedAt())
            .status(ProposalStatus.APPLIED)
            .beforeStats(proposal.getBeforeStats())
            .afterStats(proposal.getAfterStats())
            .patchContent(proposal.getPatchContent())
            .build();

        Path proposalFile = proposalsDir.resolve(
            proposal.getNoteId() + "-" + proposalId + ".json"
        );
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(proposalFile.toFile(), updated);
    }
}
