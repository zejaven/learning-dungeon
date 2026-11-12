package com.interviewlearning.generation;

import com.interviewlearning.ai.AiCliService;
import com.interviewlearning.ai.AiTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tracks topic-generation tasks so they survive a client closing the dialog or
 * reloading the page. Tasks are keyed (one slot per key): {@code "add-topic"} for
 * the free-form flow, {@code "catalog:<id>"} for a specific tree question. The
 * selected AI process keeps running regardless of who is (or isn't) listening.
 */
@Service
public class GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationService.class);

    private static final long ATTACH_TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final AiCliService ai;
    private final Map<String, GenerationTask> byKey = new ConcurrentHashMap<>();
    private final Map<String, GenerationTask> byId = new ConcurrentHashMap<>();
    private final ExecutorService jobs = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "generation-job");
        t.setDaemon(true);
        return t;
    });

    public GenerationService(AiCliService ai) {
        this.ai = ai;
    }

    /** Starts a task for the key, or returns the one already running for it. */
    public GenerationTask startOrGet(String key, String provider, String prompt, AiTask aiTask) {
        return startOrGet(key, provider, prompt, aiTask, null);
    }

    /**
     * Same, with {@code onStopped} run once the CLI has exited after a stop and
     * before the task ends, to undo what the killed run had already written.
     */
    public GenerationTask startOrGet(String key, String provider, String prompt, AiTask aiTask,
                                     Runnable onStopped) {
        GenerationTask existing = byKey.get(key);
        if (existing != null && !existing.isTerminal()) {
            return existing;
        }
        GenerationTask task = new GenerationTask(UUID.randomUUID().toString(), key);
        byKey.put(key, task);
        byId.put(task.id(), task);
        ai.runDetached(provider, prompt, aiTask, new AiCliService.Sink() {
            @Override
            public void ai(String line) {
                task.addAi(line);
            }

            @Override
            public void started(Process process) {
                task.attachProcess(process);
            }

            @Override
            public void status(String status, String message) {
                if (task.isCancelled() && onStopped != null && ("done".equals(status) || "error".equals(status))) {
                    try {
                        onStopped.run();
                    } catch (RuntimeException e) {
                        log.warn("Cleanup after stopping {} failed: {}", key, e.getMessage());
                    }
                }
                task.addStatus(status, message);
            }
        });
        return task;
    }

    /**
     * Stops a running task: kills its CLI run, and a job's later AI steps fail
     * at once. The task ends with {@link GenerationTask#STOPPED}.
     *
     * @return false for an unknown or already finished task
     */
    public boolean cancel(String taskId) {
        GenerationTask task = byId.get(taskId);
        return task != null && task.cancel();
    }

    /**
     * A generation that is more than one CLI run: it calls the AI through
     * {@link Steps#ai}, does its own work in between, and its return value is the
     * final message. Throwing ends the task with {@code error}.
     */
    public interface Job {
        String run(Steps steps) throws Exception;
    }

    /** What a {@link Job} can do with its task. */
    public interface Steps {
        /** Runs the AI to completion; its output streams into the task. True on a clean exit. */
        boolean ai(String prompt);

        /** A progress line shown in the task log. */
        void say(String message);

        /** True once the user stopped the task: the job should undo its partial work and end. */
        default boolean cancelled() {
            return false;
        }
    }

    /**
     * Starts a multi-step job for the key, or returns the task already running
     * for it. Same task, same events stream, same bulk await as
     * {@link #startOrGet}; only the task ends when the job does, not when the
     * first CLI run exits.
     */
    public GenerationTask startJob(String key, String provider, AiTask aiTask, Job job) {
        GenerationTask existing = byKey.get(key);
        if (existing != null && !existing.isTerminal()) {
            return existing;
        }
        GenerationTask task = new GenerationTask(UUID.randomUUID().toString(), key);
        byKey.put(key, task);
        byId.put(task.id(), task);
        AiCliService.Sink sink = new AiCliService.Sink() {
            @Override
            public void ai(String line) {
                task.addAi(line);
            }

            @Override
            public void started(Process process) {
                task.attachProcess(process);
            }

            @Override
            public void status(String status, String message) {
                task.addStatus(status, message);
            }
        };
        Steps steps = new Steps() {
            @Override
            public boolean ai(String prompt) {
                return !task.isCancelled()
                        && GenerationService.this.ai.runStep(provider, prompt, aiTask, sink)
                        && !task.isCancelled();
            }

            @Override
            public void say(String message) {
                task.addStatus("running", message);
            }

            @Override
            public boolean cancelled() {
                return task.isCancelled();
            }
        };
        jobs.submit(() -> {
            try {
                task.addStatus("done", job.run(steps));
            } catch (Exception e) {
                task.addStatus("error", e.getMessage() == null ? e.toString() : e.getMessage());
            }
        });
        return task;
    }

    public GenerationTask byId(String taskId) {
        return byId.get(taskId);
    }

    /** Keys of the tasks still running. */
    public List<String> runningKeys() {
        return byKey.values().stream().filter(t -> !t.isTerminal()).map(GenerationTask::key).toList();
    }

    /** Tasks still running, for the frontend to reattach to after a reload. */
    public List<Map<String, String>> running() {
        return byKey.values().stream()
                .filter(t -> !t.isTerminal())
                .map(t -> Map.of("taskId", t.id(), "key", t.key(), "status", t.status()))
                .toList();
    }
}
