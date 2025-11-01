package com.dcruver.orgroam.io;

import lombok.Builder;
import lombok.Data;
import lombok.With;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a parsed Org-mode note.
 * Preserves the original structure for round-trip fidelity.
 */
@Data
@Builder
@With
public class OrgNote {
    // File metadata
    private final Path filePath;
    private final String rawContent;  // Original file content for round-trip preservation

    // Properties drawer
    private final Map<String, String> properties;
    private final String noteId;  // :ID: property
    private final Instant created;  // :CREATED: property
    private final Instant updated;  // :UPDATED: property
    private final Set<String> tags;  // :TAGS: property

    // Content structure
    private final String title;  // First H1 heading
    private final String body;   // Content after properties and title
    private final boolean hasPropertiesDrawer;

    // Links
    private final List<String> outboundLinks;  // [[id:...]] or [[...]]

    // Special markers
    private final boolean agentsDisabled;  // Presence of #agents:off tag

    // Line-by-line structure for idempotent writing
    private final List<String> lines;

    /**
     * Get property value
     */
    public String getProperty(String key) {
        return properties != null ? properties.get(key) : null;
    }

    /**
     * Check if note has required properties
     */
    public boolean hasRequiredProperties() {
        return hasPropertiesDrawer
            && noteId != null && !noteId.isBlank()
            && created != null
            && updated != null;
    }

    /**
     * Check if formatting is OK
     * Checks for: properties drawer, title, ID, CREATED property, and final newline
     */
    public boolean isFormattingOk() {
        return hasPropertiesDrawer
            && title != null && !title.isBlank()
            && noteId != null && !noteId.isBlank()
            && created != null  // CRITICAL: Verify :CREATED: property exists
            && rawContent != null && rawContent.endsWith("\n");
    }
}
