package com.interviewlearning.api;

import com.interviewlearning.ai.AiTask;
import com.interviewlearning.generation.GenerationService;
import com.interviewlearning.generation.GenerationTask;
import com.interviewlearning.generation.TopicPromptBuilder;
import com.interviewlearning.generation.TopicPromptBuilder.TopicGenSpec;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Streams a selected AI coding-agent run that creates a new topic plugin from a pasted
 * interview question. The strict contract lives in prompts/add-topic.md; the
 * question is appended. File-editing permissions are granted because the run
 * must scaffold topics/&lt;id&gt;/ on the local machine.
 */
@RestController
@RequestMapping("/api/topics")
public class TopicGenController {

    private final GenerationService generation;
    private final TopicPromptBuilder prompts;

    public TopicGenController(GenerationService generation, TopicPromptBuilder prompts) {
        this.generation = generation;
        this.prompts = prompts;
    }

    /**
     * @param question   the interview question to turn into a topic
     * @param catalogId  the originating catalog question id (when generated from a
     *                   tree question); null/blank for a free-form "Add topic"
     * @param categoryId the catalog category id to use; when blank, the selected AI decides
     * @param difficulty 1-3 to use; when null/0, the selected AI decides
     * @param languages  content languages to generate ("en"/"ru"); null/empty = both
     */
    public record GenerateRequest(String question, String catalogId, String categoryId,
                                  Integer difficulty, String style, String styleName, String provider,
                                  List<String> languages) {
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
        String prompt = prompts.build(new TopicGenSpec(request.question(), request.catalogId(),
                request.categoryId(), request.difficulty(), request.style(), request.styleName(),
                request.provider(), request.languages()));
        GenerationTask task = generation.startOrGet(key, request.provider(), prompt, AiTask.GENERATE_TOPIC);
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
}
