package com.dcruver.orgroam.domain.actions;

import com.dcruver.orgroam.domain.CorpusState;
import com.dcruver.orgroam.domain.NoteMetadata;
import com.dcruver.orgroam.io.OrgFileReader;
import com.dcruver.orgroam.io.OrgFileWriter;
import com.dcruver.orgroam.io.OrgNote;
import com.dcruver.orgroam.io.PatchWriter;
import com.dcruver.orgroam.nlp.OllamaChatService;
import com.embabel.agent.core.action.Action;
import com.embabel.agent.core.action.ActionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Safe action: Normalize note formatting using LLM.
 *
 * Uses an LLM (via Ollama) to analyze and fix Org-mode formatting issues.
 *
 * Ensures:
 * - :PROPERTIES: drawer with :ID:, :CREATED:, :UPDATED:, :TAGS:
 * - H1 title exists
 * - Final newline
 * - Idempotent operation
 *
 * Preconditions:
 * - Note has formatting issues
 * - Agents not disabled
 * - LLM service available
 *
 * Effects:
 * - formatOk = true
 * - hasProperties = true
 * - hasTitle = true
 *
 * Cost: Medium (LLM API call)
 */
@Component
@Slf4j
public class NormalizeFormattingAction implements Action<CorpusState> {

    private final OrgFileReader fileReader;
    private final OrgFileWriter fileWriter;
    private final PatchWriter patchWriter;
    private final OllamaChatService chatService;

    @Value("${gardener.execution.mode:DRY_RUN}")
    private String executionMode;

    public NormalizeFormattingAction(
            OrgFileReader fileReader,
            OrgFileWriter fileWriter,
            PatchWriter patchWriter,
            @org.springframework.beans.factory.annotation.Autowired(required = false) OllamaChatService chatService) {
        this.fileReader = fileReader;
        this.fileWriter = fileWriter;
        this.patchWriter = patchWriter;
        this.chatService = chatService;
    }

    @Override
    public String getName() {
        return "NormalizeFormatting";
    }

    @Override
    public String getDescription() {
        return "Normalize note formatting using LLM (properties drawer, title, final newline)";
    }

    @Override
    public boolean canExecute(CorpusState state) {
        if (chatService == null) {
            log.warn("Cannot execute NormalizeFormatting: LLM service not available");
            return false;
        }
        return state.getNotesWithFormatIssues() > 0;
    }

    @Override
    public ActionResult<CorpusState> execute(CorpusState state) {
        log.info("Normalizing formatting for notes with issues using LLM");
        int normalized = 0;
        int failed = 0;

        for (NoteMetadata note : state.getNotes()) {
            if (!note.isMetadataMutable()) {
                log.debug("Skipping note {} - agents disabled", note.getNoteId());
                continue;
            }

            if (note.isFormatOk()) {
                continue;
            }

            try {
                // Read note
                OrgNote orgNote = fileReader.read(note.getFilePath());
                String originalContent = orgNote.getRawContent();

                // Use LLM to analyze and fix formatting
                log.debug("Asking LLM to normalize formatting for note {}", note.getNoteId());
                String updatedContent = chatService.normalizeOrgFormatting(originalContent, note.getNoteId());

                if (updatedContent == null || updatedContent.isBlank()) {
                    log.error("LLM returned empty content for note {}", note.getNoteId());
                    failed++;
                    continue;
                }

                // Trim any leading/trailing whitespace that might confuse comparison
                updatedContent = updatedContent.trim() + "\n";
                String originalTrimmed = originalContent.trim() + "\n";

                // Check if actually changed
                if (originalTrimmed.equals(updatedContent)) {
                    log.debug("Note {} already normalized (idempotent)", note.getNoteId());
                    continue;
                }

                // Get LLM analysis of what was fixed
                String analysis = chatService.analyzeOrgFormatting(originalContent);
                String rationale = String.format("LLM-based formatting normalization.\n\nIssues found:\n%s", analysis);

                // In dry-run, create patch only
                if ("DRY_RUN".equals(executionMode)) {
                    Map<String, Object> beforeStats = Map.of(
                        "formatOk", orgNote.isFormattingOk(),
                        "hasProperties", orgNote.isHasPropertiesDrawer()
                    );

                    Map<String, Object> afterStats = Map.of(
                        "formatOk", true,
                        "hasProperties", true
                    );

                    patchWriter.createProposal(
                        note.getNoteId(),
                        note.getFilePath(),
                        getName(),
                        rationale,
                        originalContent,
                        updatedContent,
                        beforeStats,
                        afterStats
                    );

                    log.info("Created LLM-based formatting proposal for note {}", note.getNoteId());
                } else {
                    // Auto mode: create backup and apply
                    patchWriter.createBackup(note.getFilePath());
                    // Write the LLM-corrected content directly
                    java.nio.file.Files.writeString(note.getFilePath(), updatedContent);
                    log.info("Applied LLM-normalized formatting for note {}", note.getNoteId());
                }

                normalized++;

            } catch (Exception e) {
                log.error("Failed to normalize formatting for note {}", note.getNoteId(), e);
                failed++;
            }
        }

        log.info("Normalized {} notes ({} failed)", normalized, failed);

        return ActionResult.<CorpusState>builder()
            .success(failed == 0)
            .resultingState(state)
            .message(String.format("Normalized %d notes (%d failed)", normalized, failed))
            .build();
    }

    @Override
    public double getCost(CorpusState state) {
        // Medium cost - LLM calls are more expensive than file I/O
        // Each note requires 2 LLM calls: one for normalization, one for analysis
        return state.getNotesWithFormatIssues() * 3.0;
    }

    @Override
    public CorpusState apply(CorpusState state) {
        // Update state to reflect formatting fixes
        List<NoteMetadata> updatedNotes = state.getNotes().stream()
            .map(note -> {
                if (!note.isFormatOk()) {
                    return note.withFormatOk(true)
                        .withHasProperties(true)
                        .withHasTitle(true);
                }
                return note;
            })
            .toList();

        return state.withNotes(updatedNotes)
            .withNotesWithFormatIssues(0);
    }
}
