package com.interviewlearning.codetask;

import com.fasterxml.jackson.databind.JsonNode;
import com.interviewlearning.challenge.ChallengeDtos.TestResult;
import com.interviewlearning.codetask.CodeTaskDtos.TaskRunResult;
import com.interviewlearning.runner.JavaCodeRunner;
import com.interviewlearning.runner.RunResult;
import com.interviewlearning.topics.TopicDtos.ProjectFile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs a learner's Solution against a task's hidden harness and decides the
 * verdict. The app and {@code CodeTaskContractTest} both go through here, so
 * what the test proves about a reference solution holds in the app.
 */
@Component
public class CodeTaskRunner {

    private static final String MAIN_CLASS = "Main";

    private final JavaCodeRunner runner;

    public CodeTaskRunner(JavaCodeRunner runner) {
        this.runner = runner;
    }

    public TaskRunResult run(List<ProjectFile> harness, String code) {
        List<ProjectFile> files = new ArrayList<>();
        files.add(new ProjectFile(CodeTaskRepository.SOLUTION_FILE, code == null ? "" : code));
        files.addAll(harness);
        return interpret(runner.runProject(files, MAIN_CLASS));
    }

    /**
     * Solved takes more than "every reported case is green": a run that stopped
     * early (System.exit in the learner's code, a killed JVM) reports only the
     * cases before it, so it must also have reached TEST_END with nothing left
     * unreported.
     */
    static TaskRunResult interpret(RunResult run) {
        List<String> planned = new ArrayList<>();
        List<TestResult> tests = new ArrayList<>();
        boolean ended = false;
        for (JsonNode ev : run.traceEvents()) {
            JsonNode state = ev.path("state");
            switch (ev.path("event").asText()) {
                case "TEST_PLAN" -> state.path("names").forEach(n -> planned.add(n.asText()));
                case "TEST" -> tests.add(new TestResult(
                        state.path("name").asText(""),
                        state.path("passed").asBoolean(false),
                        state.path("expected").asText(""),
                        state.path("actual").asText("")));
                case "TEST_END" -> ended = true;
                default -> { /* the learner's own trace events are not results */ }
            }
        }
        Set<String> reported = new HashSet<>();
        tests.forEach(t -> reported.add(t.name()));
        List<String> notRun = planned.stream().filter(n -> !reported.contains(n)).toList();

        boolean solved = run.error() == null && ended && !tests.isEmpty() && notRun.isEmpty()
                && tests.stream().allMatch(TestResult::passed);
        return new TaskRunResult(tests, notRun, run.error(), withoutBlankLines(run.output()), solved);
    }

    /** TestKit starts each report on a fresh line, which leaves blanks in the learner's output. */
    private static String withoutBlankLines(String output) {
        return output == null ? "" : String.join("\n", output.lines().filter(l -> !l.isBlank()).toList());
    }
}
