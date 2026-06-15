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
import java.util.ArrayList;
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
    private final String model;

    public TopicGenController(ClaudeCodeService claude,
                              RepoPaths repoPaths,
                              @Value("${app.claude.generate-permission-mode:bypassPermissions}") String permissionMode,
                              @Value("${app.claude.generate-model:}") String model) {
        this.claude = claude;
        this.repoPaths = repoPaths;
        this.permissionMode = permissionMode;
        this.model = model;
    }

    /**
     * @param question   the interview question to turn into a topic
     * @param catalogId  the originating catalog question id (when generated from a
     *                   tree question); null/blank for a free-form "Add topic"
     * @param categoryId the catalog category id to use; when blank, Claude decides
     * @param difficulty 1-3 to use; when null/0, Claude decides
     */
    public record GenerateRequest(String question, String catalogId, String categoryId, Integer difficulty) {
    }

    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generate(@RequestBody GenerateRequest request) {
        String prompt = buildPrompt(request);
        List<String> args = new ArrayList<>(List.of("--permission-mode", permissionMode));
        if (model != null && !model.isBlank()) {
            args.add("--model");
            args.add(model);
        }
        return claude.stream(prompt, args);
    }

    private String buildPrompt(GenerateRequest request) {
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

        StringBuilder sb = new StringBuilder(contract);
        sb.append("\n\n---\n\nINTERVIEW QUESTION TO TURN INTO A TOPIC:\n\n")
                .append(request.question() == null ? "" : request.question().trim())
                .append("\n\n---\n\nTOPIC METADATA TO SET IN topic.yaml:\n");

        String categoryId = request.categoryId();
        if (categoryId != null && !categoryId.isBlank()) {
            sb.append("- categoryId: ").append(categoryId.trim()).append("\n");
        } else {
            sb.append("- categoryId: choose the single best-fitting id from the allowed "
                    + "list in the contract above (use `other` only if nothing fits).\n");
        }

        Integer difficulty = request.difficulty();
        if (difficulty != null && difficulty >= 1 && difficulty <= 3) {
            sb.append("- difficulty: ").append(difficulty).append("\n");
        } else {
            sb.append("- difficulty: decide yourself — 1 (Junior), 2 (Middle) or 3 (Senior).\n");
        }

        String catalogId = request.catalogId();
        if (catalogId != null && !catalogId.isBlank()) {
            sb.append("- catalogId: ").append(catalogId.trim())
                    .append("   (this links the topic back to the source question — set it exactly)\n");
        }
        return sb.toString();
    }
}
