package com.interviewlearning.api;

import com.interviewlearning.voice.VoiceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Voice input for Boss Fight answers. Reachable from the phone (an
 * authenticated remote client) on purpose and NOT in
 * {@code RemoteAccessFilter.CODE_EXECUTION_PATHS}: the process it drives is a
 * fixed program, and what the caller sends is audio and a text hint, never code.
 */
@RestController
@RequestMapping("/api/voice")
public class VoiceController {

    private final VoiceService voice;

    public VoiceController(VoiceService voice) {
        this.voice = voice;
    }

    @GetMapping("/status")
    public VoiceService.Status status() {
        return voice.status();
    }

    public record WarmupRequest(String lang) {
    }

    /** Called when recording starts, so the model loads while the user speaks. */
    @PostMapping("/warmup")
    public ResponseEntity<Map<String, Object>> warmup(@RequestBody WarmupRequest request) {
        try {
            voice.warmup(request.lang());
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IOException | IllegalStateException e) {
            return ResponseEntity.status(503).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    /**
     * @param audio    the recording, any format ffmpeg reads (browsers send webm/opus or mp4)
     * @param lang     UI language; picks the model ("ru" or "en")
     * @param question the Boss Fight question, a hint for spelling its terms
     */
    @PostMapping("/transcribe")
    public ResponseEntity<Map<String, String>> transcribe(@RequestParam("audio") MultipartFile audio,
                                                          @RequestParam(value = "lang", required = false) String lang,
                                                          @RequestParam(value = "question", required = false)
                                                          String question) {
        if (audio.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Empty recording."));
        }
        Path file = null;
        try {
            file = Files.createTempFile("voice-", ".audio");
            audio.transferTo(file);
            return ResponseEntity.ok(Map.of("text", voice.transcribe(file, lang, question)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(Map.of("error", String.valueOf(e.getMessage())));
        } catch (IOException e) {
            return ResponseEntity.status(502).body(Map.of("error", String.valueOf(e.getMessage())));
        } finally {
            if (file != null) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ignored) {
                    // a temp file; the OS cleans it up eventually
                }
            }
        }
    }
}
