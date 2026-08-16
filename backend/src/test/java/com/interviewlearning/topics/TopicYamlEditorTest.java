package com.interviewlearning.topics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * topic.yaml files are hand-maintained UTF-8 with comments, quoting and block
 * scalars, so the editor must change one line and nothing else. These tests are
 * the guard against a regex that quietly corrupts a topic.
 */
class TopicYamlEditorTest {

    private final TopicYamlEditor editor = new TopicYamlEditor();

    @TempDir
    Path dir;

    private Path write(String content) throws IOException {
        Path file = dir.resolve("topic.yaml");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    @Test
    void appendsToAFlowListKeepingEverythingElseByteIdentical() throws IOException {
        String before = "id: qa-test\r\n"
                + "domainId: qa\r\n"
                + "languages: [ru]\r\n"
                + "title: 'Задача: оценить время'   # comment\r\n"
                + "summary: >\r\n"
                + "  Многострочное описание\r\n";
        Path file = write(before);

        assertTrue(editor.addLanguage(file, "en"));

        assertEquals(before.replace("languages: [ru]", "languages: [ru, en]"), read(file),
                "only the languages line may change");
    }

    @Test
    void isIdempotent() throws IOException {
        Path file = write("id: t\nlanguages: [ru, en]\n");
        assertTrue(editor.addLanguage(file, "en"));
        assertEquals("id: t\nlanguages: [ru, en]\n", read(file));
    }

    @Test
    void appendsToABlockList() throws IOException {
        Path file = write("id: t\nlanguages:\n  - ru\ntitle: x\n");
        assertTrue(editor.addLanguage(file, "en"));
        assertEquals("id: t\nlanguages:\n  - ru\n  - en\ntitle: x\n", read(file));
    }

    @Test
    void refusesAFileWithoutTheKey() throws IOException {
        // Every topic declares `languages:`, so a file without it is broken and
        // guessing what it carries would be worse than refusing.
        String before = "id: t\ndomainId: qa\ntitle: x\n";
        Path file = write(before);
        assertFalse(editor.addLanguage(file, "en"));
        assertEquals(before, read(file));
    }

    @Test
    void refusesAnUnsupportedLanguage() throws IOException {
        String before = "id: t\nlanguages: [ru]\n";
        Path file = write(before);
        assertFalse(editor.addLanguage(file, "de"));
        assertEquals(before, read(file));
    }

    @Test
    void refusesAFileWithTwoLanguagesKeys() throws IOException {
        String before = "id: t\nlanguages: [ru]\nother: 1\nlanguages: [en]\n";
        Path file = write(before);
        assertFalse(editor.addLanguage(file, "en"));
        assertEquals(before, read(file));
    }

    @Test
    void handlesQuotedCodesAndPreservesLfEndings() throws IOException {
        Path file = write("id: t\nlanguages: ['ru']\n");
        assertTrue(editor.addLanguage(file, "en"));
        assertEquals("id: t\nlanguages: [ru, en]\n", read(file));
    }
}
