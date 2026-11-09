package com.interviewlearning.api;

import com.interviewlearning.codetask.CodeTaskDtos.CodeTask;
import com.interviewlearning.codetask.CodeTaskDtos.TaskGenerateRequest;
import com.interviewlearning.codetask.CodeTaskDtos.TaskListItem;
import com.interviewlearning.codetask.CodeTaskDtos.TaskProgress;
import com.interviewlearning.codetask.CodeTaskDtos.TaskRunRequest;
import com.interviewlearning.codetask.CodeTaskDtos.TaskRunResponse;
import com.interviewlearning.codetask.CodeTaskDtos.TaskRunResult;
import com.interviewlearning.codetask.CodeTaskGenerator;
import com.interviewlearning.codetask.CodeTaskProgressRepository;
import com.interviewlearning.codetask.CodeTaskRepository;
import com.interviewlearning.codetask.CodeTaskRunner;
import com.interviewlearning.generation.GenerationTask;
import com.interviewlearning.progress.TopicCompletionService;
import com.interviewlearning.topics.TopicDtos.ProjectFile;
import com.interviewlearning.topics.TopicDtos.TopicDetail;
import com.interviewlearning.topics.TopicRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Code tasks of a topic. Running and generating stay under {@code /api/challenge}
 * on purpose: {@code RemoteAccessFilter} refuses that prefix to remote clients,
 * and both execute code (the caller's, or an AI CLI that writes files).
 */
@RestController
public class CodeTaskController {

    private final CodeTaskRepository tasks;
    private final CodeTaskRunner runner;
    private final CodeTaskProgressRepository progress;
    private final TopicCompletionService completion;
    private final CodeTaskGenerator generator;
    private final TopicRepository topics;

    public CodeTaskController(CodeTaskRepository tasks,
                              CodeTaskRunner runner,
                              CodeTaskProgressRepository progress,
                              TopicCompletionService completion,
                              CodeTaskGenerator generator,
                              TopicRepository topics) {
        this.tasks = tasks;
        this.runner = runner;
        this.progress = progress;
        this.completion = completion;
        this.generator = generator;
        this.topics = topics;
    }

    /** Why the generator wrote no tasks for this topic; 204 when it did not say so. */
    @GetMapping("/api/topics/{id}/code-tasks/none")
    public ResponseEntity<Map<String, String>> none(@PathVariable String id) {
        if (!CodeTaskRepository.isSafeId(id)) {
            return ResponseEntity.notFound().build();
        }
        String reason = generator.noneReason(id);
        return reason == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(Map.of("reason", reason));
    }

    /**
     * Generates more tasks for the topic: added to the existing ones, never
     * replacing them, and only those that pass {@code CodeTaskValidator}. The
     * client follows it on {@code /api/topics/generate/{taskId}/events}.
     */
    @PostMapping("/api/challenge/tasks/{id}/generate")
    public ResponseEntity<Map<String, String>> generate(@PathVariable String id,
                                                        @RequestBody TaskGenerateRequest request) {
        TopicDetail topic = topics.getTopic(id).orElse(null);
        if (topic == null) {
            return ResponseEntity.notFound().build();
        }
        String refusal = generator.refusal(topic);
        if (refusal != null) {
            return ResponseEntity.badRequest().body(Map.of("error", refusal));
        }
        String provider = request.provider() == null || request.provider().isBlank()
                ? "claude" : request.provider().trim().toLowerCase();
        GenerationTask task = generator.start(topic, provider, request.comment() == null ? "" : request.comment());
        return ResponseEntity.ok(Map.of("taskId", task.id(), "key", task.key(), "status", task.status()));
    }

    @GetMapping("/api/topics/{id}/code-tasks")
    public List<TaskListItem> list(@PathVariable String id) {
        List<CodeTask> list = tasks.list(id);
        if (list.isEmpty()) {
            return List.of();
        }
        Map<String, TaskProgress> saved = progress.forTopic(id);
        return list.stream()
                .map(task -> new TaskListItem(task, saved.getOrDefault(task.id(), TaskProgress.NONE)))
                .toList();
    }

    @PostMapping("/api/challenge/task")
    public ResponseEntity<TaskRunResponse> run(@RequestBody TaskRunRequest request) {
        Optional<CodeTask> task = tasks.find(request.topicId(), request.taskId());
        if (task.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<ProjectFile> harness = tasks.harnessFiles(request.topicId(), request.taskId());
        TaskRunResult result = runner.run(harness, request.code());
        TaskProgress updated = result.reachedTests()
                ? progress.recordRun(request.topicId(), request.taskId(), result.solved())
                : progress.forTopic(request.topicId()).getOrDefault(request.taskId(), TaskProgress.NONE);
        // Only a solve can close the topic; anything else leaves the mark as it was.
        boolean topicCompleted = result.solved()
                ? completion.recompute(request.topicId())
                : completion.isCompleted(request.topicId());
        return ResponseEntity.ok(new TaskRunResponse(result, updated, topicCompleted));
    }
}
