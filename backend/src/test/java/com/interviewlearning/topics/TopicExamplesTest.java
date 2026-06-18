package com.interviewlearning.topics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewlearning.config.RepoPaths;
import com.interviewlearning.runner.JavaCodeRunner;
import com.interviewlearning.runner.RunResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Behavioural check for every topic's example files. For each topic it compiles
 * and runs every {@code examples/*.java} through the SAME {@link JavaCodeRunner}
 * the app uses, then asserts two things "passing" should guarantee:
 *
 * <ol>
 *   <li>every example compiles and runs to completion (no compile/runtime error);</li>
 *   <li>every mission's trace {@code event} is actually emitted by some example —
 *       i.e. each mission is completable.</li>
 * </ol>
 *
 * <p>Needs the visual-runtime jar (the example classpath); the Gradle {@code test}
 * task depends on {@code :visual-runtime:jar}. Run just this:
 * {@code ./gradlew :backend:test --tests "*TopicExamplesTest"}.
 */
class TopicExamplesTest {

    private final RepoPaths repoPaths = new RepoPaths("");
    private final JavaCodeRunner runner = new JavaCodeRunner(repoPaths, new ObjectMapper(), 15, "128m");

    @TestFactory
    Stream<DynamicTest> everyExampleCompilesRunsAndCoversItsMissions() throws IOException {
        Path topicsDir = topicsDir();
        try (Stream<Path> dirs = Files.list(topicsDir)) {
            return dirs.filter(Files::isDirectory)
                    .filter(p -> Files.exists(p.resolve("topic.yaml")))
                    // Only trace topics have runnable examples (structural/theory don't).
                    .filter(TopicExamplesTest::isTraceTopic)
                    .sorted()
                    .map(dir -> DynamicTest.dynamicTest(dir.getFileName().toString(), () -> validate(dir)))
                    .toList()
                    .stream();
        }
    }

    @SuppressWarnings("unchecked")
    private void validate(Path dir) throws IOException {
        Assumptions.assumeTrue(repoPaths.visualRuntimeJar().isPresent(),
                "visual-runtime jar not built — run ./gradlew :visual-runtime:jar first");

        String name = dir.getFileName().toString();
        Map<String, Object> meta = loadYaml(dir.resolve("topic.yaml"));
        if (meta == null) {
            fail("topic.yaml did not parse (see TopicContractTest for details)");
            return;
        }

        List<String> errs = new ArrayList<>();
        Set<String> emitted = new TreeSet<>();

        Object exRaw = meta.get("examples");
        if (!(exRaw instanceof List<?> list) || list.isEmpty()) {
            fail("topic.yaml: examples must be a non-empty list");
            return;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) continue;
            Map<String, Object> ex = (Map<String, Object>) m;
            String file = str(ex, "file", "");
            String id = str(ex, "id", file.isBlank() ? "?" : file);
            String code;
            if (!file.isBlank()) {
                Path p = dir.resolve("examples").resolve(file);
                if (!Files.exists(p)) {
                    errs.add("example '" + id + "': file missing: examples/" + file);
                    continue;
                }
                code = Files.readString(p, StandardCharsets.UTF_8);
            } else {
                code = str(ex, "code", "");
            }

            RunResult r = runner.run(code);
            if (!r.success()) {
                errs.add("example '" + id + "' did not run cleanly: " + firstLines(r.error()));
            }
            for (JsonNode ev : r.traceEvents()) {
                JsonNode t = ev.get("event");
                if (t != null && t.isTextual()) emitted.add(t.asText());
            }
        }

        // Every mission's event must be producible by at least one example.
        Map<String, Object> quiz = loadYaml(dir.resolve(str(meta, "missionsFile", "quiz.yaml")));
        Object misRaw = quiz == null ? null : quiz.get("missions");
        if (misRaw instanceof List<?> missions) {
            for (Object item : missions) {
                if (!(item instanceof Map<?, ?> m)) continue;
                Map<String, Object> mm = (Map<String, Object>) m;
                String event = str(mm, "event", "");
                String id = str(mm, "id", "?");
                if (!event.isBlank() && !emitted.contains(event)) {
                    errs.add("mission '" + id + "' expects event '" + event
                            + "', but no example emits it. Emitted by examples: " + emitted);
                }
            }
        }

        if (!errs.isEmpty()) {
            fail("Topic '" + name + "' — " + errs.size() + " issue(s):\n  - "
                    + String.join("\n  - ", errs));
        }
    }

    // --- helpers -----------------------------------------------------------

    private static boolean isTraceTopic(Path topicDir) {
        Map<String, Object> meta = loadYaml(topicDir.resolve("topic.yaml"));
        return meta == null || "trace".equals(str(meta, "mode", "trace"));
    }

    private static Map<String, Object> loadYaml(Path path) {
        if (!Files.exists(path)) return null;
        try {
            Object parsed = new Yaml().load(Files.readString(path, StandardCharsets.UTF_8));
            return parsed instanceof Map<?, ?> map ? castMap(map) : null;
        } catch (RuntimeException | IOException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static String str(Map<String, Object> map, String key, String fallback) {
        Object v = map.get(key);
        return v == null ? fallback : String.valueOf(v).trim();
    }

    private static String firstLines(String s) {
        if (s == null) return "(no message)";
        String[] lines = s.split("\n", 5);
        return String.join("\n    ", List.of(lines).subList(0, Math.min(4, lines.length)));
    }

    private static Path topicsDir() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            Path t = p.resolve("topics");
            if (Files.isDirectory(t)) return t;
            p = p.getParent();
        }
        throw new IllegalStateException("Could not locate topics/ from " + Paths.get("").toAbsolutePath());
    }
}
