package com.dcruver.orgroam.io;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads and parses Org-mode files.
 * Preserves structure for idempotent round-trip.
 */
@Component
@Slf4j
public class OrgFileReader {

    private static final Pattern PROPERTIES_START = Pattern.compile("^\\s*:PROPERTIES:\\s*$");
    private static final Pattern PROPERTIES_END = Pattern.compile("^\\s*:END:\\s*$");
    private static final Pattern PROPERTY_LINE = Pattern.compile("^\\s*:(\\w+):\\s*(.*)\\s*$");
    private static final Pattern HEADING = Pattern.compile("^(\\*+)\\s+(.+)$");
    private static final Pattern LINK = Pattern.compile("\\[\\[([^]]+)\\](?:\\[([^]]+)\\])?\\]");
    private static final Pattern ID_LINK = Pattern.compile("id:([a-zA-Z0-9-]+)");
    private static final Pattern TAG = Pattern.compile("#(\\w+(?::\\w+)*)");

    // Org timestamp formats
    private static final List<DateTimeFormatter> TIMESTAMP_FORMATS = List.of(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ISO_INSTANT
    );

    /**
     * Read and parse an Org file
     */
    public OrgNote read(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        List<String> lines = content.lines().toList();

        Map<String, String> properties = new HashMap<>();
        String title = null;
        StringBuilder bodyBuilder = new StringBuilder();
        List<String> outboundLinks = new ArrayList<>();
        Set<String> tags = new HashSet<>();
        boolean hasPropertiesDrawer = false;
        boolean agentsDisabled = false;

        // Parse content
        int i = 0;
        boolean inProperties = false;

        // Look for properties drawer
        for (; i < lines.size(); i++) {
            String line = lines.get(i);

            if (PROPERTIES_START.matcher(line).matches()) {
                hasPropertiesDrawer = true;
                inProperties = true;
                i++;
                break;
            } else if (!line.trim().isEmpty()) {
                // Properties drawer should be at top, if we hit content, stop looking
                break;
            }
        }

        // Parse properties drawer
        if (inProperties) {
            for (; i < lines.size(); i++) {
                String line = lines.get(i);

                if (PROPERTIES_END.matcher(line).matches()) {
                    inProperties = false;
                    i++;
                    break;
                }

                Matcher propMatcher = PROPERTY_LINE.matcher(line);
                if (propMatcher.matches()) {
                    String key = propMatcher.group(1);
                    String value = propMatcher.group(2);
                    properties.put(key, value);
                }
            }
        }

        // Parse title and body
        for (; i < lines.size(); i++) {
            String line = lines.get(i);

            if (title == null) {
                Matcher headingMatcher = HEADING.matcher(line);
                if (headingMatcher.matches() && headingMatcher.group(1).length() == 1) {
                    title = headingMatcher.group(2);
                    continue;
                }
            }

            bodyBuilder.append(line).append("\n");

            // Extract links
            Matcher linkMatcher = LINK.matcher(line);
            while (linkMatcher.find()) {
                String link = linkMatcher.group(1);
                outboundLinks.add(link);
            }

            // Extract tags
            Matcher tagMatcher = TAG.matcher(line);
            while (tagMatcher.find()) {
                String tag = tagMatcher.group(1);
                tags.add(tag);
                if ("agents:off".equals(tag)) {
                    agentsDisabled = true;
                }
            }
        }

        // Parse special properties
        String noteId = properties.get("ID");
        Instant created = parseTimestamp(properties.get("CREATED"));
        Instant updated = parseTimestamp(properties.get("UPDATED"));

        // Parse TAGS property
        String tagsProperty = properties.get("TAGS");
        if (tagsProperty != null && !tagsProperty.isBlank()) {
            String[] tagArray = tagsProperty.split("\\s+");
            tags.addAll(Arrays.asList(tagArray));
        }

        return OrgNote.builder()
            .filePath(filePath)
            .rawContent(content)
            .properties(properties)
            .noteId(noteId)
            .created(created)
            .updated(updated)
            .tags(tags)
            .title(title)
            .body(bodyBuilder.toString())
            .hasPropertiesDrawer(hasPropertiesDrawer)
            .outboundLinks(outboundLinks)
            .agentsDisabled(agentsDisabled)
            .lines(lines)
            .build();
    }

    /**
     * Parse Org timestamp to Instant
     */
    private Instant parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return null;
        }

        // Remove Org-mode timestamp brackets
        String clean = timestamp.replaceAll("[\\[\\]<>]", "").trim();

        for (DateTimeFormatter formatter : TIMESTAMP_FORMATS) {
            try {
                // Try to parse as Instant directly
                return Instant.from(formatter.parse(clean));
            } catch (Exception e) {
                // If direct Instant parsing fails, try as LocalDateTime and convert to Instant (assuming UTC)
                try {
                    java.time.LocalDateTime ldt = java.time.LocalDateTime.from(formatter.parse(clean));
                    return ldt.atZone(java.time.ZoneOffset.UTC).toInstant();
                } catch (Exception e2) {
                    // Try next format
                }
            }
        }

        log.warn("Failed to parse timestamp: {}", timestamp);
        return null;
    }
}
