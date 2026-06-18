package com.interviewlearning.challenge;

import java.util.List;
import java.util.Map;

/** DTOs for the challenge (coding-task) topics. */
public final class ChallengeDtos {

    private ChallengeDtos() {
    }

    /** One test case result reported by the harness. */
    public record TestResult(String name, boolean passed, String expected, String actual) {
    }

    /**
     * Endpoint response.
     *
     * @param tests    the test cases the harness ran (empty on compile error)
     * @param error    compile/runtime error, or null
     * @param missions per-mission pass flags (a challenge mission passes when all its tests pass)
     */
    public record ChallengeResponse(List<TestResult> tests, String error, Map<String, Boolean> missions) {
    }
}
