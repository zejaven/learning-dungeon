package com.interviewlearning.codetask;

import com.interviewlearning.codetask.CodeTaskDtos.CodeTask;
import com.interviewlearning.codetask.CodeTaskDtos.TaskRunResult;
import com.interviewlearning.topics.TopicDtos.Localized;
import com.interviewlearning.topics.TopicDtos.ProjectFile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Everything a code task must prove before a learner sees it. Shared by
 * {@code CodeTaskContractTest} (hand-written tasks) and {@link CodeTaskGenerator}
 * (generated ones), so both are held to the same bar.
 *
 * <p>Beyond the shape it runs the task through the same {@link CodeTaskRunner}
 * the app uses, because a test nobody has seen pass and fail proves nothing:
 * the reference must solve the task, the starter must compile and not solve it,
 * and each {@code wrong/*} must be rejected by a test, not by a compile error
 * or a timeout.
 */
@Component
public class CodeTaskValidator {

    public static final int MIN_HINTS = 2;

    private final CodeTaskRepository repository;
    private final CodeTaskRunner runner;

    public CodeTaskValidator(CodeTaskRepository repository, CodeTaskRunner runner) {
        this.repository = repository;
        this.runner = runner;
    }

    /**
     * @param dir       the task folder; its name is the task id
     * @param languages languages the topic declares; every text needs each of them
     * @return what is wrong, empty when the task is good
     */
    public List<String> problems(Path dir, List<String> languages) {
        List<String> errs = new ArrayList<>();
        String taskId = dir.getFileName().toString();
        if (!CodeTaskRepository.isSafeId(taskId)) {
            errs.add("task folder name '" + taskId + "' is not a kebab-case id");
            return errs;
        }
        Optional<CodeTask> loaded = repository.read(dir);
        if (loaded.isEmpty()) {
            errs.add("task did not load: needs task.yaml (a map), starter/Solution.java and reference/Solution.java");
            return errs;
        }
        CodeTask task = loaded.get();
        checkContent(task, languages, errs);

        List<ProjectFile> harness = repository.harnessFiles(dir);
        if (harness.stream().noneMatch(f -> f.path().equals("Main.java"))) {
            errs.add("missing harness/Main.java");
        } else if (harness.stream().noneMatch(f -> f.content().contains("TestKit.run"))) {
            errs.add("harness must report through TestKit.run (TestKit.expect cannot tell a finished run)");
        } else {
            checkVerdicts(dir, task, harness, errs);
        }
        return errs;
    }

    /**
     * Rules over a topic's whole set of tasks: one final task at most, since it
     * is the single block right before the Boss Fight.
     */
    public static List<String> topicProblems(List<CodeTask> tasks) {
        List<String> finals = tasks.stream().filter(CodeTask::isFinal).map(CodeTask::id).toList();
        return finals.size() > 1
                ? List.of("a topic has at most one final task, found " + finals)
                : List.of();
    }

    private static void checkContent(CodeTask task, List<String> languages, List<String> errs) {
        if (!CodeTask.PRACTICE.equals(task.kind()) && !task.isFinal()) {
            errs.add("kind must be 'practice' or 'final', found '" + task.kind() + "'");
        }
        checkText("title", task.title(), languages, errs);
        checkText("statement", task.statement(), languages, errs);
        checkText("debrief", task.debrief(), languages, errs);
        if (task.hints().size() < MIN_HINTS) {
            errs.add("needs at least " + MIN_HINTS + " hints");
        }
        for (int i = 0; i < task.hints().size(); i++) {
            checkText("hints[" + i + "]", task.hints().get(i), languages, errs);
        }
    }

    private static void checkText(String field, Localized text, List<String> languages, List<String> errs) {
        for (String lang : languages) {
            String value = text.get(lang);
            if (value == null || value.isBlank()) {
                errs.add(field + ": no text in '" + lang + "'");
            }
        }
    }

    private void checkVerdicts(Path dir, CodeTask task, List<ProjectFile> harness, List<String> errs) {
        TaskRunResult reference = runner.run(harness, task.reference());
        if (!reference.solved()) {
            errs.add("reference does not solve the task: " + describe(reference));
        }

        TaskRunResult starter = runner.run(harness, task.starter());
        if (starter.error() != null) {
            errs.add("starter must compile and run: " + starter.error());
        } else if (starter.solved()) {
            errs.add("starter already solves the task");
        }

        List<Path> wrongDirs = listDirs(dir.resolve("wrong"));
        if (wrongDirs.isEmpty()) {
            errs.add("needs at least one wrong/<name>/Solution.java");
        }
        for (Path d : wrongDirs) {
            String label = "wrong/" + d.getFileName();
            String code;
            try {
                code = Files.readString(d.resolve(CodeTaskRepository.SOLUTION_FILE), StandardCharsets.UTF_8);
            } catch (IOException e) {
                errs.add(label + ": cannot read Solution.java");
                continue;
            }
            TaskRunResult r = runner.run(harness, code);
            if (r.error() != null || !r.notRun().isEmpty()) {
                errs.add(label + " must be rejected by a test, not by a failed run: " + describe(r));
            } else if (r.solved()) {
                errs.add(label + " passes every test, so the tests do not catch it");
            }
        }
    }

    private static List<Path> listDirs(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> dirs = Files.list(dir)) {
            return dirs.filter(Files::isDirectory).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static String describe(TaskRunResult r) {
        String error = r.error() == null ? null : r.error().length() > 600 ? r.error().substring(0, 600) + "…" : r.error();
        return "error=" + error + ", notRun=" + r.notRun() + ", tests=" + r.tests();
    }
}
