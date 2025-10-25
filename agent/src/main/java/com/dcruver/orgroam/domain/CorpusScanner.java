package com.dcruver.orgroam.domain;

import com.dcruver.orgroam.io.OrgFileReader;
import com.dcruver.orgroam.io.OrgNote;
import com.dcruver.orgroam.nlp.OllamaChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/**
 * Scans the org-roam corpus directory and builds CorpusState.
 * Uses LLM to analyze formatting issues when chat service is available.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CorpusScanner {

    private final OrgFileReader fileReader;
    private final HealthScoreCalculator healthScoreCalculator;
    private final FormatCheckCache formatCheckCache;

    @Autowired(required = false)
    private OllamaChatService chatService;

    @Value("${gardener.notes-path}")
    private String notesPath;

    @Value("${gardener.embeddings.max-age-days:90}")
    private int embeddingsMaxAgeDays;

    @Value("${gardener.stale-threshold-days:90}")
    private int staleThresholdDays;

    /**
     * Scan the corpus directory and build state
     */
    public CorpusState scanCorpus() throws IOException {
        Path notesDir = Path.of(notesPath).toAbsolutePath().normalize();

        if (!Files.exists(notesDir)) {
            log.warn("Notes directory does not exist: {}", notesDir);
            return buildEmptyCorpusState();
        }

        if (!Files.isDirectory(notesDir)) {
            log.error("Notes path is not a directory: {}", notesDir);
            return buildEmptyCorpusState();
        }

        log.info("Scanning corpus at: {}", notesDir);

        List<NoteMetadata> notes = new ArrayList<>();
        Map<String, NoteMetadata> notesById = new HashMap<>();

        // Find all .org files recursively
        try (Stream<Path> paths = Files.walk(notesDir)) {
            List<Path> orgFiles = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".org"))
                .filter(p -> !p.toString().contains("/.gardener/"))  // Skip internal directory
                .toList();

            log.info("Found {} .org files", orgFiles.size());

            for (Path orgFile : orgFiles) {
                try {
                    NoteMetadata metadata = parseAndAnalyze(orgFile);
                    notes.add(metadata);

                    if (metadata.getNoteId() != null) {
                        notesById.put(metadata.getNoteId(), metadata);
                    }

                } catch (Exception e) {
                    log.error("Failed to parse note: {}", orgFile, e);
                }
            }
        }

        log.info("Successfully parsed {} notes", notes.size());

        // Build inbound links map
        Map<String, List<String>> inboundLinksMap = buildInboundLinksMap(notes);

        // Update notes with inbound links and orphan status
        notes = notes.stream()
            .map(note -> enrichNoteWithLinks(note, inboundLinksMap))
            .toList();

        // Rebuild notesById with enriched notes
        notesById.clear();
        for (NoteMetadata note : notes) {
            if (note.getNoteId() != null) {
                notesById.put(note.getNoteId(), note);
            }
        }

        // Calculate aggregate statistics
        return buildCorpusState(notes, notesById);
    }

    /**
     * Parse org file and convert to NoteMetadata
     */
    private NoteMetadata parseAndAnalyze(Path filePath) throws IOException {
        OrgNote orgNote = fileReader.read(filePath);

        // Determine note type (simplified - could be more sophisticated)
        NoteType noteType = determineNoteType(orgNote);

        // Check embedding status
        // Notes can have embeddings in :EMBEDDING: property (vector values)
        // or in EMBED_MODEL/EMBED_AT properties (metadata)
        boolean hasEmbeddings = false;
        String embedModel = null;
        Instant embedAt = null;

        String embeddingProp = orgNote.getProperty("EMBEDDING");
        String embedModelProp = orgNote.getProperty("EMBED_MODEL");
        String embedAtProp = orgNote.getProperty("EMBED_AT");

        if (embeddingProp != null && !embeddingProp.isBlank()) {
            // Found embedding vector data
            hasEmbeddings = true;
            // Try to get metadata if available
            embedModel = embedModelProp;
            if (embedAtProp != null) {
                try {
                    embedAt = Instant.parse(embedAtProp);
                } catch (Exception e) {
                    log.warn("Failed to parse EMBED_AT for note {}: {}", orgNote.getNoteId(), embedAtProp);
                }
            }
        } else if (embedModelProp != null && embedAtProp != null) {
            // Legacy format with just metadata
            hasEmbeddings = true;
            embedModel = embedModelProp;
            try {
                embedAt = Instant.parse(embedAtProp);
            } catch (Exception e) {
                log.warn("Failed to parse EMBED_AT for note {}: {}", orgNote.getNoteId(), embedAtProp);
            }
        }

        // Check format - use LLM if available (with caching), otherwise simple check
        boolean formatOk = checkFormatWithLLM(orgNote, filePath);
        boolean hasProperties = orgNote.isHasPropertiesDrawer();
        boolean hasTitle = orgNote.getTitle() != null && !orgNote.getTitle().isBlank();

        // Link analysis (outbound only for now, inbound computed later)
        List<String> outboundLinks = extractIdLinks(orgNote.getOutboundLinks());
        int linkCount = outboundLinks.size();

        // Tags
        Set<String> tags = orgNote.getTags() != null ? orgNote.getTags() : Set.of();
        boolean tagsCanonical = true; // TODO: Check against taxonomy map

        // Provenance
        boolean provenanceOk = orgNote.hasRequiredProperties();
        Instant createdAt = orgNote.getCreated();
        Instant updatedAt = orgNote.getUpdated();

        // Staleness - fall back to file mtime if UPDATED property is missing
        int staleDays = calculateStaleDays(updatedAt, createdAt, filePath);

        // Build metadata (without health score yet)
        NoteMetadata metadata = NoteMetadata.builder()
            .noteId(orgNote.getNoteId())
            .filePath(filePath)
            .noteType(noteType)
            .hasEmbeddings(hasEmbeddings)
            .embedModel(embedModel)
            .embedAt(embedAt)
            .formatOk(formatOk)
            .hasProperties(hasProperties)
            .hasTitle(hasTitle)
            .linkCount(linkCount)
            .orphan(linkCount == 0)  // Will be refined later with inbound links
            .outboundLinks(outboundLinks)
            .inboundLinks(List.of())  // Computed later
            .tags(tags)
            .tagsCanonical(tagsCanonical)
            .provenanceOk(provenanceOk)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .staleDays(staleDays)
            .agentsDisabled(orgNote.isAgentsDisabled())
            .healthScore(0)  // Computed after
            .build();

        // Calculate health score
        int healthScore = healthScoreCalculator.calculateScore(metadata);
        metadata = metadata.withHealthScore(healthScore);

        return metadata;
    }

    /**
     * Extract ID-based links from link list
     */
    private List<String> extractIdLinks(List<String> links) {
        if (links == null) {
            return List.of();
        }

        return links.stream()
            .filter(link -> link.startsWith("id:"))
            .map(link -> link.substring(3))  // Remove "id:" prefix
            .toList();
    }

    /**
     * Determine note type based on tags or properties
     */
    private NoteType determineNoteType(OrgNote orgNote) {
        Set<String> tags = orgNote.getTags();
        if (tags == null) {
            return NoteType.PERMANENT;
        }

        if (tags.contains("source")) {
            return NoteType.SOURCE;
        } else if (tags.contains("literature")) {
            return NoteType.LITERATURE;
        } else {
            return NoteType.PERMANENT;
        }
    }

    /**
     * Calculate staleness in days since last update
     * Falls back to createdAt or file modification time if updatedAt is missing
     */
    private int calculateStaleDays(Instant updatedAt, Instant createdAt, Path filePath) {
        Instant referenceTime = updatedAt;

        // Fall back to createdAt if updatedAt is missing
        if (referenceTime == null) {
            referenceTime = createdAt;
        }

        // Fall back to file modification time if both properties are missing
        if (referenceTime == null) {
            try {
                referenceTime = Files.getLastModifiedTime(filePath).toInstant();
            } catch (IOException e) {
                log.warn("Failed to get file modification time for {}: {}", filePath, e.getMessage());
                return Integer.MAX_VALUE;  // Unknown = infinitely stale
            }
        }

        Duration duration = Duration.between(referenceTime, Instant.now());
        return (int) duration.toDays();
    }

    /**
     * Build inbound links map
     */
    private Map<String, List<String>> buildInboundLinksMap(List<NoteMetadata> notes) {
        Map<String, List<String>> inboundMap = new HashMap<>();

        for (NoteMetadata note : notes) {
            if (note.getNoteId() == null) {
                continue;
            }

            for (String targetId : note.getOutboundLinks()) {
                inboundMap.computeIfAbsent(targetId, k -> new ArrayList<>())
                    .add(note.getNoteId());
            }
        }

        return inboundMap;
    }

    /**
     * Enrich note with inbound links and update orphan status
     */
    private NoteMetadata enrichNoteWithLinks(NoteMetadata note, Map<String, List<String>> inboundLinksMap) {
        if (note.getNoteId() == null) {
            return note;
        }

        List<String> inboundLinks = inboundLinksMap.getOrDefault(note.getNoteId(), List.of());
        int totalLinks = note.getOutboundLinks().size() + inboundLinks.size();
        boolean orphan = totalLinks == 0;

        return note
            .withInboundLinks(inboundLinks)
            .withLinkCount(totalLinks)
            .withOrphan(orphan);
    }

    /**
     * Build final CorpusState with all statistics
     */
    private CorpusState buildCorpusState(List<NoteMetadata> notes, Map<String, NoteMetadata> notesById) {
        int totalNotes = notes.size();
        int notesWithEmbeddings = 0;
        int notesWithStaleEmbeddings = 0;
        int notesWithFormatIssues = 0;
        int orphanNotes = 0;
        int notesWithNonCanonicalTags = 0;
        int staleNotes = 0;

        for (NoteMetadata note : notes) {
            if (note.isHasEmbeddings()) {
                notesWithEmbeddings++;
                // Only count as stale if we have a timestamp AND it's old
                // Notes without timestamps are treated as unknown age (not stale)
                if (note.getEmbedAt() != null && !note.isEmbeddingsFresh(embeddingsMaxAgeDays)) {
                    notesWithStaleEmbeddings++;
                }
            }

            if (!note.isFormatOk()) {
                notesWithFormatIssues++;
            }

            if (note.isOrphan()) {
                orphanNotes++;
            }

            if (!note.isTagsCanonical()) {
                notesWithNonCanonicalTags++;
            }

            if (note.isStale(staleThresholdDays)) {
                staleNotes++;
            }
        }

        double meanHealthScore = healthScoreCalculator.calculateMeanScore(notes);

        return CorpusState.builder()
            .notes(notes)
            .notesById(notesById)
            .totalNotes(totalNotes)
            .notesWithEmbeddings(notesWithEmbeddings)
            .notesWithStaleEmbeddings(notesWithStaleEmbeddings)
            .notesWithFormatIssues(notesWithFormatIssues)
            .orphanNotes(orphanNotes)
            .notesWithNonCanonicalTags(notesWithNonCanonicalTags)
            .staleNotes(staleNotes)
            .meanHealthScore(meanHealthScore)
            .build();
    }

    /**
     * Build empty corpus state when directory doesn't exist
     */
    private CorpusState buildEmptyCorpusState() {
        return CorpusState.builder()
            .notes(List.of())
            .notesById(Map.of())
            .totalNotes(0)
            .notesWithEmbeddings(0)
            .notesWithStaleEmbeddings(0)
            .notesWithFormatIssues(0)
            .orphanNotes(0)
            .notesWithNonCanonicalTags(0)
            .staleNotes(0)
            .meanHealthScore(0.0)
            .build();
    }

    /**
     * Check format using LLM if available (with caching), otherwise fall back to simple check.
     * This allows the audit to actually verify formatting with the LLM during scanning.
     * Results are cached based on file modification time to avoid redundant LLM calls.
     */
    private boolean checkFormatWithLLM(OrgNote orgNote, Path filePath) {
        // Always do the simple check first
        boolean simpleCheck = orgNote.isFormattingOk();

        // If no LLM service, return simple check
        if (chatService == null) {
            log.debug("No LLM service available, using simple format check for {}", orgNote.getNoteId());
            return simpleCheck;
        }

        // If simple check passes, skip expensive LLM call
        if (simpleCheck) {
            return true;
        }

        // Get file's last modified time for cache key
        Instant lastModified;
        try {
            lastModified = Files.getLastModifiedTime(filePath).toInstant();
        } catch (IOException e) {
            log.warn("Failed to get last modified time for {}, skipping cache", filePath);
            lastModified = Instant.now(); // Fallback, will cause cache miss
        }

        // Check cache first
        Boolean cachedResult = formatCheckCache.getCachedResult(filePath, lastModified);
        if (cachedResult != null) {
            log.debug("Using cached format check result for {}: {}", orgNote.getNoteId(), cachedResult);
            return cachedResult;
        }

        // Cache miss - use LLM to analyze formatting for notes that fail simple check
        try {
            log.info("Using LLM to analyze formatting for note: {} (cache miss)", orgNote.getNoteId());
            String analysis = chatService.analyzeOrgFormatting(orgNote.getRawContent());

            // Check if LLM found issues
            boolean hasIssues = analysis != null
                && !analysis.toLowerCase().contains("no issues")
                && !analysis.toLowerCase().contains("properly formatted");

            boolean formatOk = !hasIssues;

            if (hasIssues) {
                log.info("LLM found formatting issues in {}: {}",
                    orgNote.getNoteId(),
                    analysis.substring(0, Math.min(100, analysis.length())));
            } else {
                log.info("LLM says formatting is OK for {}", orgNote.getNoteId());
            }

            // Cache the result
            formatCheckCache.cacheResult(filePath, lastModified, formatOk, analysis);

            return formatOk;
        } catch (Exception e) {
            log.warn("LLM format analysis failed for {}, falling back to simple check: {}",
                orgNote.getNoteId(), e.getMessage());
            return simpleCheck;
        }
    }
}
