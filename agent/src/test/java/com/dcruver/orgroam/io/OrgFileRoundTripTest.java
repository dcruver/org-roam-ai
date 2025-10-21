package com.dcruver.orgroam.io;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for idempotent round-trip of Org files.
 * Reading and writing the same file should preserve its content.
 */
class OrgFileRoundTripTest {

    private OrgFileReader reader;
    private OrgFileWriter writer;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        reader = new OrgFileReader();
        writer = new OrgFileWriter();
    }

    @Test
    void testRoundTripPreservesContent() throws Exception {
        // Create a well-formatted org file
        String originalContent = """
            :PROPERTIES:
            :ID:       test-123-456
            :CREATED:  [2025-01-15 10:00:00]
            :UPDATED:  [2025-01-15 10:30:00]
            :TAGS:     test example
            :END:
            * Test Note

            This is the body of the test note.

            It has multiple paragraphs and should be preserved exactly.

            ** Subsection

            With a subsection.
            """;

        Path testFile = tempDir.resolve("test-note.org");
        Files.writeString(testFile, originalContent);

        // Read the file
        OrgNote note = reader.read(testFile);

        // Verify parsed correctly
        assertNotNull(note);
        assertEquals("test-123-456", note.getNoteId());
        assertEquals("Test Note", note.getTitle());
        assertTrue(note.isHasPropertiesDrawer());

        // Write it back
        Path outputFile = tempDir.resolve("output-note.org");
        writer.write(note, outputFile);

        // Read the written content
        String writtenContent = Files.readString(outputFile);

        // Should preserve the original (with normalized spacing if needed)
        assertNotNull(writtenContent);
        assertTrue(writtenContent.contains(":ID:       test-123-456"));
        assertTrue(writtenContent.contains("* Test Note"));
        assertTrue(writtenContent.contains("This is the body of the test note"));
        assertTrue(writtenContent.endsWith("\n"), "Should end with newline");
    }

    @Test
    void testFormattingIdempotence() throws Exception {
        // Create a note that needs formatting
        String originalContent = """
            * Note Without Properties

            This note has no properties drawer.
            It should be normalized.
            """;

        Path testFile = tempDir.resolve("test-note.org");
        Files.writeString(testFile, originalContent);

        // Read and normalize
        OrgNote note = reader.read(testFile);
        OrgNote normalized = writer.normalizeFormatting(note);

        // Write normalized version
        Path outputFile = tempDir.resolve("output-note.org");
        writer.write(normalized, outputFile);

        // Read it again
        OrgNote secondRead = reader.read(outputFile);

        // Normalize again
        OrgNote secondNormalized = writer.normalizeFormatting(secondRead);

        // Second normalization should be idempotent - no changes
        assertEquals(normalized.getNoteId(), secondNormalized.getNoteId());
        assertTrue(secondNormalized.isHasPropertiesDrawer());
        assertNotNull(secondNormalized.getCreated());
        assertNotNull(secondNormalized.getUpdated());
    }

    @Test
    void testPreservesWhitespaceInBody() throws Exception {
        String originalContent = """
            :PROPERTIES:
            :ID:       test-whitespace
            :CREATED:  [2025-01-15 10:00:00]
            :UPDATED:  [2025-01-15 10:00:00]
            :END:
            * Test Whitespace

            Line 1


            Line 2 with double blank line above

                Indented line

            Line with trailing spaces
            """;

        Path testFile = tempDir.resolve("test-note.org");
        Files.writeString(testFile, originalContent);

        OrgNote note = reader.read(testFile);
        Path outputFile = tempDir.resolve("output-note.org");
        writer.write(note, outputFile);

        String writtenContent = Files.readString(outputFile);

        // Check body preservation
        assertTrue(writtenContent.contains("Line 1"));
        assertTrue(writtenContent.contains("Line 2 with double blank line above"));
        assertTrue(writtenContent.contains("    Indented line"));
    }

    @Test
    void testNormalizeFormattingAddsRequiredFields() throws Exception {
        String originalContent = "* Minimal Note\n\nJust some content.\n";

        Path testFile = tempDir.resolve("test-note.org");
        Files.writeString(testFile, originalContent);

        OrgNote note = reader.read(testFile);
        assertFalse(note.isHasPropertiesDrawer());
        assertNull(note.getNoteId());

        OrgNote normalized = writer.normalizeFormatting(note);

        assertTrue(normalized.isHasPropertiesDrawer());
        assertNotNull(normalized.getNoteId());
        assertNotNull(normalized.getCreated());
        assertNotNull(normalized.getUpdated());
    }

    @Test
    void testFinalNewlineIsPreserved() throws Exception {
        String contentWithNewline = """
            :PROPERTIES:
            :ID:       test-newline
            :CREATED:  [2025-01-15 10:00:00]
            :UPDATED:  [2025-01-15 10:00:00]
            :END:
            * Test

            Content.
            """;

        Path testFile = tempDir.resolve("test-note.org");
        Files.writeString(testFile, contentWithNewline);

        OrgNote note = reader.read(testFile);
        Path outputFile = tempDir.resolve("output-note.org");
        writer.write(note, outputFile);

        String written = Files.readString(outputFile);
        assertTrue(written.endsWith("\n"), "Should preserve final newline");
    }
}
