package com.interviewlearning.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.interviewlearning.challenge.ChallengeDtos.ChallengeResponse;
import com.interviewlearning.challenge.ChallengeDtos.TestResult;
import com.interviewlearning.runner.JavaCodeRunner;
import com.interviewlearning.runner.RunResult;
import com.interviewlearning.topics.TopicDtos.Mission;
import com.interviewlearning.topics.TopicDtos.ProjectFile;
import com.interviewlearning.topics.TopicDtos.TopicDetail;
import com.interviewlearning.topics.TopicRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Compiles and runs a challenge topic's solution against its hidden test harness,
 * returning each test case's result plus which missions pass. The harness reports
 * cases via {@code visual.TestKit} (TEST trace events), so we read them from the
 * run's trace events.
 */
@RestController
@RequestMapping("/api/challenge")
public class ChallengeController {

    /** The solution file the learner edits; the harness references class Solution. */
    private static final String SOLUTION_FILE = "Solution.java";
    /** The harness entry point class. */
    private static final String MAIN_CLASS = "Main";

    private final TopicRepository topics;
    private final JavaCodeRunner runner;

    public ChallengeController(TopicRepository topics, JavaCodeRunner runner) {
        this.topics = topics;
        this.runner = runner;
    }

    public record ChallengeRequest(String topicId, String code) {
    }

    @PostMapping
    public ChallengeResponse run(@RequestBody ChallengeRequest request) {
        Optional<TopicDetail> opt = request.topicId() == null
                ? Optional.empty()
                : topics.getTopic(request.topicId());
        if (opt.isEmpty() || !"challenge".equals(opt.get().mode())) {
            return new ChallengeResponse(List.of(), "Not a challenge topic.", Map.of());
        }
        TopicDetail topic = opt.get();

        List<ProjectFile> files = new ArrayList<>();
        files.add(new ProjectFile(SOLUTION_FILE, request.code() == null ? "" : request.code()));
        files.addAll(topics.harnessFiles(topic.id()));

        RunResult result = runner.runProject(files, MAIN_CLASS);
        List<TestResult> tests = collectTests(result);

        Map<String, Boolean> missions = new LinkedHashMap<>();
        boolean allPass = result.error() == null && !tests.isEmpty() && tests.stream().allMatch(TestResult::passed);
        for (Mission m : topic.missions()) {
            if ("challenge".equals(m.type()) && hasTestsRule(m)) {
                missions.put(m.id(), allPass);
            }
        }
        return new ChallengeResponse(tests, result.error(), missions);
    }

    private List<TestResult> collectTests(RunResult result) {
        List<TestResult> tests = new ArrayList<>();
        for (JsonNode ev : result.traceEvents()) {
            if (!"TEST".equals(ev.path("event").asText())) {
                continue;
            }
            JsonNode s = ev.path("state");
            tests.add(new TestResult(
                    s.path("name").asText(""),
                    s.path("passed").asBoolean(false),
                    s.path("expected").asText(""),
                    s.path("actual").asText("")
            ));
        }
        return tests;
    }

    private boolean hasTestsRule(Mission m) {
        for (Object o : m.requires()) {
            if (o instanceof Map<?, ?> map && "tests".equals(String.valueOf(map.get("kind")))) {
                return true;
            }
        }
        return false;
    }
}
