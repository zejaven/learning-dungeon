package com.interviewlearning.topics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Contract check for every folder under {@code topics/}. One dynamic test per
 * topic, so a failure names the offending topic. It parses topic.yaml / quiz.yaml
 * with the SAME snakeyaml the app uses (strictly — a parse error fails the test,
 * unlike the lenient runtime loader which silently drops a broken file), then
 * checks the structure the UI relies on: bilingual fields, examples, referenced
 * files, and the mission / boss-fight shape.
 *
 * <p>Run just this: {@code ./gradlew :backend:test --tests "*TopicContractTest"}.
 */
class TopicContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TestFactory
    Stream<DynamicTest> everyTopicMatchesTheContract() throws IOException {
        Path topicsDir = topicsDir();
        try (Stream<Path> dirs = Files.list(topicsDir)) {
            return dirs.filter(Files::isDirectory)
                    .filter(p -> Files.exists(p.resolve("topic.yaml")))
                    .sorted()
                    .map(dir -> DynamicTest.dynamicTest(dir.getFileName().toString(), () -> validate(dir)))
                    // Collect eagerly so the directory stream can close.
                    .toList()
                    .stream();
        }
    }

    private void validate(Path dir) {
        String name = dir.getFileName().toString();
        List<String> errs = new ArrayList<>();

        Map<String, Object> meta = parseYaml(dir.resolve("topic.yaml"), "topic.yaml", errs);
        if (meta != null) {
            if (!name.equals(str(meta, "id"))) {
                errs.add("topic.yaml: id '" + str(meta, "id") + "' must equal folder name '" + name + "'");
            }
            bilingual(meta.get("title"), "topic.yaml: title", errs);
            bilingual(meta.get("category"), "topic.yaml: category", errs);
            bilingual(meta.get("summary"), "topic.yaml: summary", errs);
            validateExamples(dir, meta, errs);
        }

        requireNonEmptyFile(dir.resolve("explanation.en.md"), errs);
        requireNonEmptyFile(dir.resolve("explanation.ru.md"), errs);
        requireExists(dir.resolve("visualizer.tsx"), errs);
        validateJson(dir.resolve("trace-schema.json"), errs);

        String quizName = meta != null ? str(meta, "missionsFile") : null;
        if (quizName == null || quizName.isBlank()) quizName = "quiz.yaml";
        Map<String, Object> quiz = parseYaml(dir.resolve(quizName), quizName, errs);
        if (quiz != null) {
            validateMissions(quiz, errs);
            validateBossFight(quiz, errs);
        }

        if (!errs.isEmpty()) {
            fail("Topic '" + name + "' has " + errs.size() + " contract violation(s):\n  - "
                    + String.join("\n  - ", errs));
        }
    }

    // --- field checks ------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void validateExamples(Path dir, Map<String, Object> meta, List<String> errs) {
        Object raw = meta.get("examples");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            errs.add("topic.yaml: examples must be a non-empty list");
            return;
        }
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof Map<?, ?> mm)) {
                errs.add("topic.yaml: examples[" + i + "] must be a map");
                continue;
            }
            Map<String, Object> ex = (Map<String, Object>) mm;
            String id = str(ex, "id");
            String where = "topic.yaml: examples[" + i + "]";
            if (id.isBlank()) errs.add(where + ": missing id");
            else if (!ids.add(id)) errs.add(where + ": duplicate id '" + id + "'");
            bilingual(ex.get("title"), where + ".title", errs);
            String file = str(ex, "file");
            if (!file.isBlank()) {
                if (!Files.exists(dir.resolve("examples").resolve(file))) {
                    errs.add(where + ": file 'examples/" + file + "' does not exist");
                }
            } else if (str(ex, "code").isBlank()) {
                errs.add(where + ": needs either a 'file' or inline 'code'");
            }
        }
        String def = str(meta, "defaultExample");
        if (!def.isBlank() && !ids.contains(def)) {
            errs.add("topic.yaml: defaultExample '" + def + "' is not one of the example ids");
        }
    }

    @SuppressWarnings("unchecked")
    private void validateMissions(Map<String, Object> quiz, List<String> errs) {
        Object raw = quiz.get("missions");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            errs.add("quiz.yaml: missions must be a non-empty list");
            return;
        }
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof Map<?, ?> mm)) {
                errs.add("quiz.yaml: missions[" + i + "] must be a map");
                continue;
            }
            Map<String, Object> m = (Map<String, Object>) mm;
            String where = "quiz.yaml: missions[" + i + "]";
            String id = str(m, "id");
            if (id.isBlank()) errs.add(where + ": missing id");
            else if (!ids.add(id)) errs.add(where + ": duplicate id '" + id + "'");
            bilingual(m.get("title"), where + ".title", errs);
            bilingual(m.get("goal"), where + ".goal", errs);
            if (str(m, "event").isBlank()) errs.add(where + ": missing event");
        }
    }

    @SuppressWarnings("unchecked")
    private void validateBossFight(Map<String, Object> quiz, List<String> errs) {
        Object raw = quiz.get("bossFight");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            errs.add("quiz.yaml: bossFight must be a non-empty list of { id, en, ru }");
            return;
        }
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof Map<?, ?> mm)) {
                errs.add("quiz.yaml: bossFight[" + i + "] must be a map { id, en, ru }");
                continue;
            }
            Map<String, Object> q = (Map<String, Object>) mm;
            String where = "quiz.yaml: bossFight[" + i + "]";
            String id = str(q, "id");
            if (id.isBlank()) errs.add(where + ": missing id");
            else if (!ids.add(id)) errs.add(where + ": duplicate id '" + id + "'");
            if (str(q, "en").isBlank()) errs.add(where + ": missing en");
            if (str(q, "ru").isBlank()) errs.add(where + ": missing ru");
        }
    }

    /** A bilingual field must be present and, if a map, have non-blank en and ru. */
    private void bilingual(Object v, String where, List<String> errs) {
        if (v == null) {
            errs.add(where + ": missing");
            return;
        }
        if (v instanceof Map<?, ?> m) {
            if (blank(m.get("en"))) errs.add(where + ".en: missing");
            if (blank(m.get("ru"))) errs.add(where + ".ru: missing");
        } else if (blank(v)) {
            errs.add(where + ": blank");
        }
    }

    // --- io / parsing ------------------------------------------------------

    private Map<String, Object> parseYaml(Path path, String label, List<String> errs) {
        if (!Files.exists(path)) {
            errs.add(label + ": file is missing");
            return null;
        }
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            Object parsed = new Yaml().load(content);
            if (parsed instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                return typed;
            }
            errs.add(label + ": top level must be a mapping");
            return null;
        } catch (RuntimeException | IOException e) {
            // snakeyaml messages include the line/column of the syntax error.
            errs.add(label + ": YAML did not parse — " + firstLine(e.getMessage()));
            return null;
        }
    }

    private void validateJson(Path path, List<String> errs) {
        if (!Files.exists(path)) {
            errs.add("trace-schema.json: file is missing");
            return;
        }
        try {
            JSON.readTree(Files.readAllBytes(path));
        } catch (IOException e) {
            errs.add("trace-schema.json: invalid JSON — " + firstLine(e.getMessage()));
        }
    }

    private void requireExists(Path path, List<String> errs) {
        if (!Files.exists(path)) errs.add(path.getFileName() + ": file is missing");
    }

    private void requireNonEmptyFile(Path path, List<String> errs) {
        try {
            if (!Files.exists(path) || Files.readString(path, StandardCharsets.UTF_8).isBlank()) {
                errs.add(path.getFileName() + ": file is missing or empty");
            }
        } catch (IOException e) {
            errs.add(path.getFileName() + ": could not read — " + firstLine(e.getMessage()));
        }
    }

    // --- helpers -----------------------------------------------------------

    private static boolean blank(Object v) {
        return v == null || String.valueOf(v).isBlank();
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String firstLine(String s) {
        if (s == null) return "(no message)";
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }

    /** Walks up from the working dir until a folder containing topics/ is found. */
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
