package com.interviewlearning.codetask;

import com.interviewlearning.challenge.ChallengeDtos.TestResult;
import com.interviewlearning.topics.TopicDtos.Localized;

import java.util.List;

/** Data transfer objects for code tasks: one hand-checked coding task per learning atom. */
public final class CodeTaskDtos {

    private CodeTaskDtos() {
    }

    /**
     * A task as the client sees it. {@code harness/} and {@code wrong/} never
     * leave the server; the reference is sent along and held back on the client,
     * the same trust model as lesson grading.
     */
    public record CodeTask(
            String id,
            String atomId,
            int order,
            Localized title,
            Localized statement,
            List<Localized> hints,
            Localized debrief,
            String starter,
            String reference
    ) {
    }

    /**
     * Progress of one task. {@code attempts} counts runs that reached the tests,
     * so a typo that does not compile never spends a hint.
     */
    public record TaskProgress(int attempts, boolean solved) {
        public static final TaskProgress NONE = new TaskProgress(0, false);
    }

    /** A task plus what the practice tab shows around it. */
    public record TaskListItem(CodeTask task, Localized atomTitle, Localized atomSummary, TaskProgress progress) {
    }

    /**
     * What one run of the tests proved.
     *
     * @param tests  the cases that reported, in order
     * @param notRun cases the harness announced but never reported: the run died first
     * @param error  compile error, timeout or crash, or null
     * @param output what the learner's code printed
     * @param solved all announced cases passed and the run reached its end
     */
    public record TaskRunResult(List<TestResult> tests, List<String> notRun, String error, String output,
                                boolean solved) {

        /** False when the code never got as far as the tests, e.g. a compile error. */
        public boolean reachedTests() {
            return !tests.isEmpty() || !notRun.isEmpty();
        }
    }

    public record TaskRunRequest(String topicId, String taskId, String code) {
    }

    /** @param topicCompleted the topic is passed after this run (tasks are one of its gates) */
    public record TaskRunResponse(TaskRunResult result, TaskProgress progress, boolean topicCompleted) {
    }

    /**
     * Generating tasks for a topic.
     *
     * @param comment optional wishes ("a task on X"); tasks are added, never replaced
     */
    public record TaskGenerateRequest(String provider, String comment) {
    }
}
