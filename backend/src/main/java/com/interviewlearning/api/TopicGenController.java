package com.interviewlearning.api;

import com.interviewlearning.claude.ClaudeCodeService;
import com.interviewlearning.config.RepoPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Streams a Claude Code run that creates a new topic plugin from a pasted
 * interview question. The strict contract lives in prompts/add-topic.md; the
 * question is appended. File-editing permissions are granted because the run
 * must scaffold topics/&lt;id&gt;/ on the local machine.
 */
@RestController
@RequestMapping("/api/topics")
public class TopicGenController {

    private static final Logger log = LoggerFactory.getLogger(TopicGenController.class);

    private final ClaudeCodeService claude;
    private final RepoPaths repoPaths;
    private final String permissionMode;

    public TopicGenController(ClaudeCodeService claude,
                              RepoPaths repoPaths,
                              @Value("${app.claude.generate-permission-mode:bypassPermissions}") String permissionMode) {
        this.claude = claude;
        this.repoPaths = repoPaths;
        this.permissionMode = permissionMode;
    }

    public record GenerateRequest(String question) {
    }

    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generate(@RequestBody GenerateRequest request) {
        String prompt = buildPrompt(request.question());
        return claude.stream(prompt, List.of("--permission-mode", permissionMode));
    }

    private String buildPrompt(String question) {
        Path promptFile = repoPaths.promptsDir().resolve("add-topic.md");
        String contract;
        try {
            contract = Files.readString(promptFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("add-topic.md not found at {}: {}", promptFile, e.getMessage());
            contract = "Add a new Java interview learning topic following the existing "
                    + "topics/ schema (topic.yaml, explanation.md, examples/, visualizer.tsx, "
                    + "trace-schema.json, quiz.yaml). Reuse existing visual primitives and the "
                    + "existing engine; do not modify the shell or runner.";
        }
        return contract
                + "\n\n---\n\nINTERVIEW QUESTION TO TURN INTO A TOPIC:\n\n"
                + (question == null ? "" : question.trim())
                + "\n";
    }
}
