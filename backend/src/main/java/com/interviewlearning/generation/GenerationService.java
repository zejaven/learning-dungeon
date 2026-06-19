package com.interviewlearning.generation;

import com.interviewlearning.ai.AiCliService;
import com.interviewlearning.ai.AiTask;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks topic-generation tasks so they survive a client closing the dialog or
 * reloading the page. Tasks are keyed (one slot per key): {@code "add-topic"} for
 * the free-form flow, {@code "catalog:<id>"} for a specific tree question. The
 * selected AI process keeps running regardless of who is (or isn't) listening.
 */
@Service
public class GenerationService {

    private static final long ATTACH_TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final AiCliService ai;
    private final Map<String, GenerationTask> byKey = new ConcurrentHashMap<>();
    private final Map<String, GenerationTask> byId = new ConcurrentHashMap<>();

    public GenerationService(AiCliService ai) {
        this.ai = ai;
    }

    /** Starts a task for the key, or returns the one already running for it. */
    public GenerationTask startOrGet(String key, String provider, String prompt) {
        GenerationTask existing = byKey.get(key);
        if (existing != null && !existing.isTerminal()) {
            return existing;
        }
        GenerationTask task = new GenerationTask(UUID.randomUUID().toString(), key);
        byKey.put(key, task);
        byId.put(task.id(), task);
        ai.runDetached(provider, prompt, AiTask.GENERATE_TOPIC, new AiCliService.Sink() {
            @Override
            public void ai(String line) {
                task.addAi(line);
            }

            @Override
            public void status(String status, String message) {
                task.addStatus(status, message);
            }
        });
        return task;
    }

    public GenerationTask byId(String taskId) {
        return byId.get(taskId);
    }

    /** Tasks still running, for the frontend to reattach to after a reload. */
    public List<Map<String, String>> running() {
        return byKey.values().stream()
                .filter(t -> !t.isTerminal())
                .map(t -> Map.of("taskId", t.id(), "key", t.key(), "status", t.status()))
                .toList();
    }
}
