package com.interviewlearning.codetask;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewlearning.codetask.CodeTaskDtos.TaskRunResult;
import com.interviewlearning.runner.RunResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The verdict: what counts as solved, and what a run that died still shows. */
class CodeTaskRunnerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode plan(String... names) throws Exception {
        return mapper.readTree("{\"event\":\"TEST_PLAN\",\"state\":{\"names\":"
                + mapper.writeValueAsString(names) + "}}");
    }

    private JsonNode test(String name, boolean passed) throws Exception {
        return mapper.readTree("{\"event\":\"TEST\",\"state\":{\"name\":\"" + name
                + "\",\"passed\":" + passed + ",\"expected\":\"1\",\"actual\":\"2\"}}");
    }

    private JsonNode end() throws Exception {
        return mapper.readTree("{\"event\":\"TEST_END\",\"state\":{\"count\":2}}");
    }

    private TaskRunResult interpret(String error, JsonNode... events) {
        return CodeTaskRunner.interpret(new RunResult(error == null, "", new ArrayList<>(List.of(events)), error));
    }

    @Test
    void allGreenAndFinishedIsSolved() throws Exception {
        TaskRunResult r = interpret(null, plan("a", "b"), test("a", true), test("b", true), end());

        assertTrue(r.solved());
        assertTrue(r.notRun().isEmpty());
    }

    @Test
    void aRedCaseIsNotSolvedButAllCasesAreShown() throws Exception {
        TaskRunResult r = interpret(null, plan("a", "b"), test("a", false), test("b", true), end());

        assertFalse(r.solved());
        assertEquals(2, r.tests().size());
    }

    @Test
    void stoppingEarlyIsNotSolvedEvenWithEveryReportedCaseGreen() throws Exception {
        // What System.exit(0) in the learner's code looks like: a clean exit, no error.
        TaskRunResult r = interpret(null, plan("a", "b"), test("a", true));

        assertFalse(r.solved());
        assertEquals(List.of("b"), r.notRun());
    }

    @Test
    void aMissingEndIsNotSolvedEvenWithNothingLeftToRun() throws Exception {
        assertFalse(interpret(null, plan("a"), test("a", true)).solved());
    }

    @Test
    void aTimeoutKeepsTheCasesThatAlreadyReported() throws Exception {
        TaskRunResult r = interpret("Execution timed out", plan("a", "b"), test("a", true));

        assertFalse(r.solved());
        assertEquals(1, r.tests().size());
        assertEquals(List.of("b"), r.notRun());
        assertEquals("Execution timed out", r.error());
    }

    @Test
    void aCompileErrorNeverReachedTheTests() {
        TaskRunResult r = interpret("Solution.java:1: error");

        assertFalse(r.solved());
        assertFalse(r.reachedTests());
    }

    @Test
    void aRunThatDiedBeforeItsFirstCaseStillReachedTheTests() throws Exception {
        assertTrue(interpret("crashed", plan("a")).reachedTests());
    }

    @Test
    void noCasesAtAllIsNotSolved() throws Exception {
        assertFalse(interpret(null, plan(), end()).solved());
    }
}
