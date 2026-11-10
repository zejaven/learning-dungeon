package com.interviewlearning.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The parts of VoiceService that decide something, without starting python. */
class VoiceServiceTest {

    @TempDir
    Path tmp;

    @Test
    void theUiLanguagePicksTheModel() {
        assertEquals("ru", VoiceService.languageOf("ru"));
        assertEquals("ru", VoiceService.languageOf(" RU "));
        assertEquals("en", VoiceService.languageOf("en"));
        assertEquals("en", VoiceService.languageOf("de"));
        assertEquals("en", VoiceService.languageOf(null));
    }

    @Test
    void theRussianPromptAsksForLatinTermsAndCarriesTheQuestion() {
        String prompt = VoiceService.promptFor("ru", "Что вернёт  get\nу HashMap?");
        assertTrue(prompt.contains("по-английски"), prompt);
        assertTrue(prompt.endsWith("Что вернёт get у HashMap?"), prompt);
        assertTrue(VoiceService.promptFor("en", null).startsWith("An answer"));
    }

    @Test
    void aLongQuestionIsCut() {
        String prompt = VoiceService.promptFor("en", "x".repeat(2000));
        assertEquals(VoiceService.PROMPT_MAX_CHARS, prompt.length());
    }

    @Test
    void aWorkerErrorBecomesAnException() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        IOException e = assertThrows(IOException.class,
                () -> VoiceService.checkReply(mapper.readTree("{\"id\":\"1\",\"error\":\"ffmpeg failed\"}")));
        assertTrue(e.getMessage().contains("ffmpeg failed"));
        assertEquals("hi", VoiceService.checkReply(mapper.readTree("{\"id\":\"1\",\"text\":\"hi\"}"))
                .get("text").asText());
    }

    @Test
    void pythonIsTheConfiguredOneOrTheSiblingTranscriberVenv() throws IOException {
        Path repo = Files.createDirectories(tmp.resolve("java-interview-dungeon"));
        assertNull(VoiceService.resolvePython("", repo));
        assertEquals(Path.of("C:/py/python.exe"), VoiceService.resolvePython(" C:/py/python.exe ", repo));

        Path venvPython = tmp.resolve("zeveon-transcriber/.venv/Scripts/python.exe");
        Files.createDirectories(venvPython.getParent());
        Files.createFile(venvPython);
        assertEquals(venvPython, VoiceService.resolvePython(null, repo));
    }
}
