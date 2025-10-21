package com.dcruver.orgroam.io;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Writes Org-mode files while preserving structure.
 * Ensures idempotent round-trip.
 */
@Component
@Slf4j
public class OrgFileWriter {

    private static final DateTimeFormatter ORG_TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    /**
     * Write an OrgNote to file
     */
    public void write(OrgNote note, Path outputPath) throws IOException {
        // Create backup if file exists
        if (Files.exists(outputPath)) {
            Path backup = outputPath.resolveSibling(outputPath.getFileName() + ".bak");
            Files.copy(outputPath, backup, StandardCopyOption.REPLACE_EXISTING);
        }

        String content = buildContent(note);
        Files.writeString(outputPath, content);
        log.debug("Wrote note to: {}", outputPath);
    }

    /**
     * Build file content from OrgNote
     */
    private String buildContent(OrgNote note) {
        StringBuilder sb = new StringBuilder();

        // Write properties drawer
        if (note.isHasPropertiesDrawer() && note.getProperties() != null) {
            sb.append(":PROPERTIES:\n");

            // Write ID first if present
            if (note.getNoteId() != null) {
                sb.append(":ID:       ").append(note.getNoteId()).append("\n");
            }

            // Write CREATED
            if (note.getCreated() != null) {
                sb.append(":CREATED:  ").append(formatTimestamp(note.getCreated())).append("\n");
            }

            // Write UPDATED
            if (note.getUpdated() != null) {
                sb.append(":UPDATED:  ").append(formatTimestamp(note.getUpdated())).append("\n");
            }

            // Write TAGS if present
            if (note.getTags() != null && !note.getTags().isEmpty()) {
                sb.append(":TAGS:     ").append(String.join(" ", note.getTags())).append("\n");
            }

            // Write other properties
            for (Map.Entry<String, String> entry : note.getProperties().entrySet()) {
                String key = entry.getKey();
                // Skip properties we already wrote
                if (!key.equals("ID") && !key.equals("CREATED") && !key.equals("UPDATED") && !key.equals("TAGS")) {
                    sb.append(":").append(key).append(":       ").append(entry.getValue()).append("\n");
                }
            }

            sb.append(":END:\n");
        }

        // Write title
        if (note.getTitle() != null) {
            sb.append("* ").append(note.getTitle()).append("\n");
        }

        // Write body
        if (note.getBody() != null) {
            sb.append(note.getBody());
        }

        // Ensure final newline
        if (!sb.toString().endsWith("\n")) {
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Format Instant as Org timestamp
     */
    private String formatTimestamp(Instant instant) {
        return "[" + ORG_TIMESTAMP_FORMAT.format(instant) + "]";
    }

    /**
     * Update properties in an existing note
     */
    public OrgNote updateProperties(OrgNote note, Map<String, String> updates) {
        Map<String, String> newProperties = new java.util.HashMap<>(note.getProperties());
        newProperties.putAll(updates);

        return note.withProperties(newProperties)
            .withUpdated(Instant.now());
    }

    /**
     * Normalize formatting of a note (idempotent operation)
     */
    public OrgNote normalizeFormatting(OrgNote note) {
        // Ensure properties drawer exists
        boolean hasPropertiesDrawer = note.isHasPropertiesDrawer();
        Map<String, String> properties = note.getProperties();

        if (properties == null) {
            properties = new java.util.HashMap<>();
        }

        if (!hasPropertiesDrawer) {
            hasPropertiesDrawer = true;
        }

        // Ensure ID exists
        String noteId = note.getNoteId();
        if (noteId == null || noteId.isBlank()) {
            noteId = java.util.UUID.randomUUID().toString();
            properties.put("ID", noteId);
        }

        // Ensure timestamps
        Instant created = note.getCreated();
        if (created == null) {
            created = Instant.now();
            properties.put("CREATED", formatTimestamp(created));
        }

        Instant updated = Instant.now();
        properties.put("UPDATED", formatTimestamp(updated));

        return note.withHasPropertiesDrawer(hasPropertiesDrawer)
            .withProperties(properties)
            .withNoteId(noteId)
            .withCreated(created)
            .withUpdated(updated);
    }
}
