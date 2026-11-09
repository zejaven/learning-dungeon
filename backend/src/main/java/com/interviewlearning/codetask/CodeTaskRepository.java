package com.interviewlearning.codetask;

import com.interviewlearning.codetask.CodeTaskDtos.CodeTask;
import com.interviewlearning.config.RepoPaths;
import com.interviewlearning.topics.TopicDtos.Localized;
import com.interviewlearning.topics.TopicDtos.ProjectFile;
import com.interviewlearning.topics.TopicRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads {@code topics/<id>/tasks/<task-id>/} on each request (same no-restart
 * philosophy as {@link TopicRepository}). Lenient at runtime: a broken task is
 * skipped with a warning. {@code CodeTaskContractTest} is the strict validator.
 */
@Repository
public class CodeTaskRepository {

    public static final String TASKS_DIR = "tasks";
    public static final String SOLUTION_FILE = "Solution.java";

    /** Ids reach file paths from the request, so nothing else may get through. */
    private static final Pattern SAFE_ID = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private static final Logger log = LoggerFactory.getLogger(CodeTaskRepository.class);

    private final RepoPaths repoPaths;

    public CodeTaskRepository(RepoPaths repoPaths) {
        this.repoPaths = repoPaths;
    }

    /** The topic's tasks in display order; empty when it has none. */
    public List<CodeTask> list(String topicId) {
        if (!SAFE_ID.matcher(topicId).matches()) {
            return List.of();
        }
        Path tasks = tasksDir(topicId);
        if (!Files.isDirectory(tasks)) {
            return List.of();
        }
        try (Stream<Path> dirs = Files.list(tasks)) {
            return dirs.filter(Files::isDirectory)
                    .map(this::read)
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparingInt(CodeTask::order).thenComparing(CodeTask::id))
                    .toList();
        } catch (IOException e) {
            log.warn("Failed to list {}: {}", tasks, e.getMessage());
            return List.of();
        }
    }

    public Optional<CodeTask> find(String topicId, String taskId) {
        if (topicId == null || taskId == null
                || !SAFE_ID.matcher(topicId).matches() || !SAFE_ID.matcher(taskId).matches()) {
            return Optional.empty();
        }
        Path dir = tasksDir(topicId).resolve(taskId);
        return Files.isDirectory(dir) ? read(dir) : Optional.empty();
    }

    /** The hidden test driver: compiled with the learner's Solution, never sent to the client. */
    public List<ProjectFile> harnessFiles(String topicId, String taskId) {
        return harnessFiles(tasksDir(topicId).resolve(taskId));
    }

    /** Same, for a task folder anywhere, e.g. a generator's draft. */
    public List<ProjectFile> harnessFiles(Path taskDir) {
        Path harness = taskDir.resolve("harness");
        if (!Files.isDirectory(harness)) {
            return List.of();
        }
        List<ProjectFile> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(harness)) {
            for (Path p : walk.filter(Files::isRegularFile).sorted().toList()) {
                files.add(new ProjectFile(harness.relativize(p).toString().replace('\\', '/'),
                        Files.readString(p, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            log.warn("Failed to read harness in {}: {}", harness, e.getMessage());
            return List.of();
        }
        return files;
    }

    public static boolean isSafeId(String id) {
        return id != null && SAFE_ID.matcher(id).matches();
    }

    public Path tasksDir(String topicId) {
        return repoPaths.topicsDir().resolve(topicId).resolve(TASKS_DIR);
    }

    /** Reads one task folder, wherever it lives; empty (with a warning) when it is broken. */
    @SuppressWarnings("unchecked")
    public Optional<CodeTask> read(Path dir) {
        try {
            Object raw = new Yaml().load(Files.readString(dir.resolve("task.yaml"), StandardCharsets.UTF_8));
            if (!(raw instanceof Map<?, ?>)) {
                log.warn("Ignoring {}: task.yaml is not a map", dir);
                return Optional.empty();
            }
            Map<String, Object> meta = (Map<String, Object>) raw;
            List<Localized> hints = new ArrayList<>();
            if (meta.get("hints") instanceof List<?> list) {
                for (Object hint : list) {
                    if (hint instanceof Map<?, ?> m) {
                        hints.add(TopicRepository.loc(Map.<String, Object>of("text", m), "text", ""));
                    } else {
                        hints.add(Localized.of(String.valueOf(hint).trim()));
                    }
                }
            }
            return Optional.of(new CodeTask(
                    dir.getFileName().toString(),
                    String.valueOf(meta.getOrDefault("kind", "")),
                    meta.get("order") instanceof Number n ? n.intValue() : Integer.MAX_VALUE,
                    TopicRepository.loc(meta, "title", dir.getFileName().toString()),
                    TopicRepository.loc(meta, "statement", ""),
                    hints,
                    TopicRepository.loc(meta, "debrief", ""),
                    Files.readString(dir.resolve("starter").resolve(SOLUTION_FILE), StandardCharsets.UTF_8),
                    Files.readString(dir.resolve("reference").resolve(SOLUTION_FILE), StandardCharsets.UTF_8)
            ));
        } catch (IOException | RuntimeException e) {
            log.warn("Ignoring task {}: {}", dir, e.getMessage());
            return Optional.empty();
        }
    }
}
