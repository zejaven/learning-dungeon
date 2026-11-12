package com.interviewlearning.lesson.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.interviewlearning.config.RepoPaths;
import com.interviewlearning.runner.JavaCodeRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Stage one of verifying lesson exercises whose answer only a model ever
 * vouched for, for a whole roadmap module at once: every {@code predict_output}
 * and {@code spot_bug} snippet goes through {@link SnippetCheck} (the same check
 * that runs after every lesson generation). Everything the run cannot settle goes on
 * to stage two, {@code tools/verify/check_exercises_codex.py}.
 *
 * <p>Off by default: a lesson-wide run takes minutes. Turn it on with
 * {@code .\gradlew.bat :backend:test --tests "*ExerciseRunCheck" -Dverify.module=collections-streams}
 * ({@code -Dverify.topics=a,b} picks topics directly; {@code -Dverify.atomsRoot=<dir>}
 * reads {@code <dir>/<topic>/learning-atoms.json} instead of the topics folder,
 * for a calibration copy). Writes {@code backend/build/exercise-verify/<label>/run.json}.
 */
class ExerciseRunCheck {

    private static final List<String> TYPES = List.of("predict_output", "spot_bug");
    private static final Pattern ENV_HINT = Pattern.compile(
            "parallel|Thread|Executor|CompletableFuture|nanoTime|currentTimeMillis|identityHashCode|Random|Set\\.of|Map\\.of");

    static boolean enabled() {
        return System.getProperty("verify.module") != null || System.getProperty("verify.topics") != null;
    }

    @Test
    @EnabledIf("enabled")
    void runEveryCodeExerciseOfTheModule() throws IOException {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        RepoPaths repoPaths = new RepoPaths("");
        JavaCodeRunner runner = new JavaCodeRunner(repoPaths, mapper, 10, "128m");
        Path root = repoPaths.repoRoot();

        String module = System.getProperty("verify.module");
        List<String> topics = System.getProperty("verify.topics") != null
                ? Arrays.stream(System.getProperty("verify.topics").split(",")).map(String::trim).toList()
                : moduleTopics(mapper, root.resolve("frontend/src/roadmap.json"), module);
        Path atomsRoot = System.getProperty("verify.atomsRoot") != null
                ? Path.of(System.getProperty("verify.atomsRoot"))
                : repoPaths.topicsDir();
        String label = module != null ? module : String.join("+", topics);

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Integer> tally = new TreeMap<>();
        for (String topic : topics) {
            Path file = atomsRoot.resolve(topic).resolve("learning-atoms.json");
            if (!Files.isRegularFile(file)) {
                System.out.println("skip " + topic + ": no lesson");
                continue;
            }
            JsonNode atoms = mapper.readTree(file.toFile());
            for (JsonNode atom : atoms.path("atoms")) {
                for (String list : List.of("discovery", "practice")) {
                    for (JsonNode ex : atom.path(list)) {
                        if (!TYPES.contains(ex.path("type").asText())) {
                            continue;
                        }
                        Map<String, Object> row = check(runner, topic, atom.path("id").asText(), ex);
                        rows.add(row);
                        String key = row.get("group") + "/" + row.get("verdict");
                        tally.merge(key, 1, Integer::sum);
                        System.out.printf("%-28s %-40s %-13s %s%n", topic, ex.path("id").asText(),
                                row.get("group"), row.get("verdict"));
                    }
                }
            }
        }
        Path out = root.resolve("backend/build/exercise-verify").resolve(label);
        Files.createDirectories(out);
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("label", label);
        doc.put("topics", topics);
        doc.put("tally", tally);
        doc.put("exercises", rows);
        mapper.writeValue(out.resolve("run.json").toFile(), doc);
        System.out.println(rows.size() + " exercises -> " + out.resolve("run.json"));
        tally.forEach((k, v) -> System.out.println("  " + k + ": " + v));
    }

    /** One run.json row: the exercise, then what {@link SnippetCheck} found. */
    private static Map<String, Object> check(JavaCodeRunner runner, String topic, String atomId, JsonNode ex) {
        Map<String, Object> row = new LinkedHashMap<>();
        String code = ex.path("code").asText("");
        row.put("topic", topic);
        row.put("atom", atomId);
        row.put("id", ex.path("id").asText());
        row.put("type", ex.path("type").asText());
        row.put("prompt", ex.path("prompt").path("en").asText());
        row.put("code", code);
        List<Map<String, Object>> options = new ArrayList<>();
        for (JsonNode o : ex.path("options")) {
            Map<String, Object> opt = new LinkedHashMap<>();
            opt.put("id", o.path("id").asText());
            opt.put("text", o.path("text").path("en").asText());
            opt.put("correct", o.path("correct").asBoolean());
            options.add(opt);
        }
        row.put("options", options);
        row.put("envHint", ENV_HINT.matcher(code).find());

        SnippetCheck.Verdict v = SnippetCheck.check(runner, ex);
        row.put("correctId", v.correctId());
        if (v.wrapped() != null) {
            row.put("wrapped", v.wrapped());
        }
        if (v.compileError() != null) {
            row.put("compileError", v.compileError());
        }
        if (v.output() != null) {
            row.put("output", v.output());
        }
        if (v.output2() != null) {
            row.put("output2", v.output2());
        }
        row.put("group", v.group());
        row.put("verdict", v.verdict());
        if (SnippetCheck.RUNNABLE.equals(v.group()) && "predict_output".equals(ex.path("type").asText())
                && !"prints-nothing".equals(v.verdict())) {
            row.put("literalMatches", v.literalMatches());
        }
        return row;
    }

    private static List<String> moduleTopics(ObjectMapper mapper, Path roadmap, String module) throws IOException {
        JsonNode found = find(mapper.readTree(Files.readString(roadmap, StandardCharsets.UTF_8)), module);
        if (found == null) {
            throw new IllegalArgumentException("No roadmap module '" + module + "'");
        }
        for (String key : List.of("entries", "items", "topics")) {
            if (found.has(key)) {
                List<String> ids = new ArrayList<>();
                found.get(key).forEach(n -> {
                    if (n.isTextual()) {
                        ids.add(n.asText());
                    }
                });
                return ids;
            }
        }
        throw new IllegalArgumentException("Module '" + module + "' lists no topics");
    }

    private static JsonNode find(JsonNode node, String id) {
        if (node.isObject()) {
            if (id.equals(node.path("id").asText(null))) {
                return node;
            }
            for (JsonNode child : node) {
                JsonNode r = find(child, id);
                if (r != null) {
                    return r;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode r = find(child, id);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }
}
