package com.interviewlearning.api;

import com.interviewlearning.config.RepoPaths;
import com.interviewlearning.generation.GenerationService;
import com.interviewlearning.generation.GenerationTask;
import com.interviewlearning.topics.TopicDtos.TopicSummary;
import com.interviewlearning.topics.TopicRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import java.util.Map;

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

    private final GenerationService generation;
    private final RepoPaths repoPaths;
    private final TopicRepository topics;
    private final String permissionMode;
    private final String model;

    public TopicGenController(GenerationService generation,
                              RepoPaths repoPaths,
                              TopicRepository topics,
                              @Value("${app.claude.generate-permission-mode:bypassPermissions}") String permissionMode,
                              @Value("${app.claude.generate-model:}") String model) {
        this.generation = generation;
        this.repoPaths = repoPaths;
        this.topics = topics;
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

    /**
     * Starts a generation (or reuses the one already running for this key) and
     * returns its task id. The client then attaches to {@code /generate/{id}/events}.
     */
    @PostMapping("/generate")
    public Map<String, String> generate(@RequestBody GenerateRequest request) {
        String key = (request.catalogId() != null && !request.catalogId().isBlank())
                ? "catalog:" + request.catalogId().trim()
                : "add-topic";
        List<String> args = new ArrayList<>(List.of("--permission-mode", permissionMode));
        if (model != null && !model.isBlank()) {
            args.add("--model");
            args.add(model);
        }
        GenerationTask task = generation.startOrGet(key, buildPrompt(request), args);
        return Map.of("taskId", task.id(), "key", task.key(), "status", task.status());
    }

    /** Attaches to a task's event stream, replaying its history first. */
    @GetMapping(value = "/generate/{taskId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String taskId) {
        GenerationTask task = generation.byId(taskId);
        if (task == null) {
            // Unknown task (e.g. cleared by a backend restart): emit a clean error
            // status and complete, rather than a 500.
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event().name("status")
                        .data("{\"status\":\"error\",\"message\":\"No such generation task.\"}"));
            } catch (IOException ignored) {
                // client already gone
            }
            emitter.complete();
            return emitter;
        }
        return task.attach(30 * 60 * 1000L);
    }

    /** Tasks still running, so the frontend can reattach after a reload. */
    @GetMapping("/generate/active")
    public List<Map<String, String>> active() {
        return generation.running();
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

        if ("design-patterns".equals(categoryId)) {
            sb.append("- mode: if this question is a single GoF pattern defined by class "
                    + "relationships (Strategy, Observer, Factory, Decorator, Adapter, …), set "
                    + "`mode: structural` and follow the \"Structural topics\" contract "
                    + "(starter/ files + structure missions, no model/examples/visualizer). "
                    + "For an overview question (\"what patterns exist?\") or a trivial one-class "
                    + "pattern, keep the default `trace` mode or just write prose.\n");
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

        appendCrossLinkContext(sb);
        return sb.toString();
    }

    /**
     * Lists the topics that already exist so Claude can cross-link to them from the
     * new explanation via `[label](topic:&lt;id&gt;)` (see topic-contract.md). Only
     * real, existing ids are offered, so links never dangle.
     */
    private void appendCrossLinkContext(StringBuilder sb) {
        List<TopicSummary> existing;
        try {
            existing = topics.listTopics();
        } catch (RuntimeException e) {
            log.warn("Could not list topics for cross-link context: {}", e.getMessage());
            return;
        }
        if (existing.isEmpty()) {
            return;
        }
        sb.append("\n\n---\n\nEXISTING TOPICS YOU MAY CROSS-LINK TO. When the explanation "
                + "mentions one of these concepts, link to it with `[label](topic:<id>)` "
                + "so the reader can jump to that topic. Use only these exact ids; never "
                + "invent one. Do NOT link the new topic to itself.\n");
        for (TopicSummary t : existing) {
            String title = t.title() == null ? t.id() : t.title().en();
            sb.append("- topic:").append(t.id()).append(" — ").append(title).append("\n");
        }
    }
}
