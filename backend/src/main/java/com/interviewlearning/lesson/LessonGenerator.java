package com.interviewlearning.lesson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.interviewlearning.ai.AiTask;
import com.interviewlearning.config.RepoPaths;
import com.interviewlearning.generation.GenerationService;
import com.interviewlearning.generation.GenerationTask;
import com.interviewlearning.lesson.verify.SnippetCheck;
import com.interviewlearning.lesson.verify.SnippetFixPrompt;
import com.interviewlearning.runner.JavaCodeRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Generates a topic's lesson ({@code learning-atoms.json}) and then checks the
 * answer keys it can check: every NEW or CHANGED {@code predict_output} with Java
 * code is run through {@link SnippetCheck}. When the real output matches another
 * option than the marked one, the AI gets one repair round
 * ({@link SnippetFixPrompt}); a repair that breaks the file or loses an id is
 * rolled back. The lesson is kept either way, and the final message says what
 * the run confirmed, fixed, and could not settle.
 *
 * <p>Only new or changed exercises are checked: an augment run must not re-raise
 * an old exercise the check cannot settle (a deliberate false alarm) every time.
 */
@Service
public class LessonGenerator {

    private static final Logger log = LoggerFactory.getLogger(LessonGenerator.class);

    private final GenerationService generation;
    private final JavaCodeRunner runner;
    private final RepoPaths repoPaths;
    private final ObjectMapper mapper;

    public LessonGenerator(GenerationService generation, JavaCodeRunner runner, RepoPaths repoPaths,
                           ObjectMapper mapper) {
        this.generation = generation;
        this.runner = runner;
        this.repoPaths = repoPaths;
        this.mapper = mapper;
    }

    public static String key(String topicId) {
        return "atoms:" + topicId;
    }

    /** Starts (or returns the running) lesson generation for the topic with an already built prompt. */
    public GenerationTask start(String topicId, String provider, String prompt) {
        return generation.startJob(key(topicId), provider, AiTask.GENERATE_ATOMS,
                steps -> run(topicId, prompt, steps));
    }

    String run(String topicId, String prompt, GenerationService.Steps steps) throws IOException {
        Path file = repoPaths.topicsDir().resolve(topicId).resolve("learning-atoms.json");
        Map<String, String> before = fingerprints(readTree(file));
        if (!steps.ai(prompt)) {
            throw new IllegalStateException("The AI run failed.");
        }
        JsonNode written = readTree(file);
        if (written == null) {
            throw new IllegalStateException("The AI did not write a readable learning-atoms.json.");
        }

        List<JsonNode> toCheck = new ArrayList<>();
        exercises(written).forEach((id, ex) -> {
            if (SnippetCheck.checkable(ex) && !Objects.equals(before.get(id), fingerprint(ex))) {
                toCheck.add(ex);
            }
        });
        if (toCheck.isEmpty()) {
            return "Lesson written. No new code exercises to check by running.";
        }

        steps.say("Checking " + toCheck.size() + " code exercise(s) by running them...");
        Map<String, SnippetCheck.Verdict> verdicts = new LinkedHashMap<>();
        for (JsonNode ex : toCheck) {
            verdicts.put(ex.path("id").asText(), SnippetCheck.check(runner, ex));
        }
        List<SnippetFixPrompt.Item> contradicted = toCheck.stream()
                .filter(ex -> verdicts.get(ex.path("id").asText()).contradicts())
                .map(ex -> new SnippetFixPrompt.Item(ex, verdicts.get(ex.path("id").asText())))
                .toList();
        if (contradicted.isEmpty()) {
            return summary(verdicts, List.of(), false);
        }

        List<String> ids = contradicted.stream().map(i -> i.exercise().path("id").asText()).toList();
        steps.say("The run contradicts the answer of " + String.join(", ", ids) + ". Asking the AI to fix them...");
        byte[] backup = Files.readAllBytes(file);
        Set<String> idsBefore = allIds(written);
        boolean ran = steps.ai(SnippetFixPrompt.build(file, contradicted));
        JsonNode repaired = readTree(file);
        if (!ran || repaired == null || !allIds(repaired).equals(idsBefore)) {
            Files.write(file, backup);
            log.warn("Lesson {}: the repair round {}; restored the generated file.", topicId,
                    !ran ? "failed" : repaired == null ? "broke the JSON" : "changed the ids");
            return summary(verdicts, List.of(), true);
        }

        steps.say("Checking the fixed exercises again...");
        Map<String, JsonNode> now = exercises(repaired);
        List<String> fixed = new ArrayList<>();
        for (String id : ids) {
            JsonNode ex = now.get(id);
            SnippetCheck.Verdict again = SnippetCheck.checkable(ex) ? SnippetCheck.check(runner, ex) : null;
            if (again != null) {
                verdicts.put(id, again);
            }
            if (again == null || !again.contradicts()) {
                fixed.add(id);
            }
        }
        return summary(verdicts, fixed, false);
    }

    /**
     * One line for the task log and the lesson panel. "STILL CONTRADICTED" is
     * what the panel looks for to show it as a warning.
     */
    private static String summary(Map<String, SnippetCheck.Verdict> verdicts, List<String> fixed,
                                  boolean rolledBack) {
        long confirmed = verdicts.values().stream().filter(v -> SnippetCheck.CONFIRMED.equals(v.verdict())).count();
        List<String> still = verdicts.entrySet().stream()
                .filter(e -> e.getValue().contradicts())
                .map(e -> e.getKey() + " (marked " + e.getValue().correctId() + ", prints \""
                        + oneLine(e.getValue().output()) + "\")")
                .toList();
        long unsettled = verdicts.values().stream()
                .filter(v -> !SnippetCheck.CONFIRMED.equals(v.verdict()) && !v.contradicts())
                .count();
        StringBuilder sb = new StringBuilder("Lesson written. Code check: ")
                .append(confirmed).append(" of ").append(verdicts.size())
                .append(" new code exercise(s) confirmed by running");
        if (!fixed.isEmpty()) {
            sb.append("; fixed after the run contradicted them: ").append(String.join(", ", fixed));
        }
        if (rolledBack) {
            sb.append("; the repair round failed and was rolled back");
        }
        if (!still.isEmpty()) {
            sb.append("; STILL CONTRADICTED by the run: ").append(String.join("; ", still));
        }
        if (unsettled > 0) {
            sb.append("; ").append(unsettled).append(" could not be settled by running");
        }
        return sb.append('.').toString();
    }

    private static String oneLine(String output) {
        String s = output == null ? "" : output.strip().replaceAll("\\R", " / ");
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }

    private JsonNode readTree(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            JsonNode root = mapper.readTree(file.toFile());
            return root != null && root.path("atoms").isArray() ? root : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Every exercise of the lesson by id, in file order. */
    private static Map<String, JsonNode> exercises(JsonNode root) {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        if (root == null) {
            return out;
        }
        for (JsonNode atom : root.path("atoms")) {
            for (String list : List.of("discovery", "practice")) {
                for (JsonNode ex : atom.path(list)) {
                    out.put(ex.path("id").asText(), ex);
                }
            }
        }
        return out;
    }

    /** Atom, exercise and option ids: what a repair round must leave untouched. */
    private static Set<String> allIds(JsonNode root) {
        Set<String> ids = new LinkedHashSet<>();
        for (JsonNode atom : root.path("atoms")) {
            ids.add("atom:" + atom.path("id").asText());
        }
        exercises(root).forEach((id, ex) -> {
            ids.add("ex:" + id);
            ex.path("options").forEach(o -> ids.add("opt:" + id + "/" + o.path("id").asText()));
        });
        return ids;
    }

    private Map<String, String> fingerprints(JsonNode root) {
        Map<String, String> out = new HashMap<>();
        exercises(root).forEach((id, ex) -> out.put(id, fingerprint(ex)));
        return out;
    }

    /** What decides the check's verdict: type, code and the English options with their mark. */
    private String fingerprint(JsonNode ex) {
        ObjectNode f = mapper.createObjectNode();
        f.put("type", ex.path("type").asText());
        f.put("code", ex.path("code").asText());
        f.put("codeLang", ex.path("codeLang").asText());
        f.set("options", mapper.valueToTree(
                StreamSupport.stream(ex.path("options").spliterator(), false)
                        .map(o -> o.path("id").asText() + "|" + o.path("text").path("en").asText() + "|"
                                + o.path("correct").asBoolean())
                        .collect(Collectors.toList())));
        return f.toString();
    }
}
