package com.interviewlearning.topics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewlearning.lang.ContentLanguages;
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
        String mode = meta == null ? "trace" : str(meta, "mode");
        if (mode.isBlank()) mode = "trace";
        boolean structural = "structural".equals(mode);
        boolean theory = "theory".equals(mode);
        boolean sqlMode = "sql".equals(mode);
        boolean challenge = "challenge".equals(mode);
        List<String> langs = declaredLanguages(meta, errs);
        if (meta != null) {
            if (!name.equals(str(meta, "id"))) {
                errs.add("topic.yaml: id '" + str(meta, "id") + "' must equal folder name '" + name + "'");
            }
            localized(meta.get("title"), "topic.yaml: title", langs, errs);
            localized(meta.get("category"), "topic.yaml: category", langs, errs);
            localized(meta.get("summary"), "topic.yaml: summary", langs, errs);
            String domainId = str(meta, "domainId");
            if (!domainId.isBlank() && !domainId.matches("[a-z0-9]+(-[a-z0-9]+)*")) {
                errs.add("topic.yaml: domainId '" + domainId + "' must be kebab-case lowercase");
            }
            if (str(meta, "categoryId").isBlank()) {
                errs.add("topic.yaml: categoryId is missing (required for catalog placement)");
            }
            if (str(meta, "difficulty").isBlank()) {
                errs.add("topic.yaml: difficulty is missing (1-5)");
            }
            if (structural) {
                validateStarter(dir, errs);
            } else if (sqlMode) {
                validateSqlStarter(dir, errs);
            } else if (challenge) {
                validateChallengeFiles(dir, errs);
            } else if (!theory) {
                // theory topics have no editable project at all.
                validateExamples(dir, meta, langs, errs);
            }
        }

        // Only the declared languages need an explanation file (default: both).
        for (String lang : langs) {
            requireNonEmptyFile(dir.resolve("explanation." + lang + ".md"), errs);
            validateImageLinks(dir.resolve("explanation." + lang + ".md"), errs);
        }
        // A file the topic does not declare would render nowhere; usually it means
        // `languages:` was not updated when a translation was added.
        for (String lang : ContentLanguages.ALL) {
            if (!langs.contains(lang) && Files.exists(dir.resolve("explanation." + lang + ".md"))) {
                errs.add("explanation." + lang + ".md exists but topic.yaml `languages:` "
                        + "does not declare " + lang);
            }
        }
        if (!structural && !theory && !sqlMode && !challenge) {
            // Behavioural topics drive a visualizer from trace events; the other
            // modes render a class diagram / a result table / nothing.
            requireExists(dir.resolve("visualizer.tsx"), errs);
            validateJson(dir.resolve("trace-schema.json"), errs);
        }

        String quizName = meta != null ? str(meta, "missionsFile") : null;
        if (quizName == null || quizName.isBlank()) quizName = "quiz.yaml";
        Map<String, Object> quiz = parseYaml(dir.resolve(quizName), quizName, errs);
        if (quiz != null) {
            // theory topics have a Boss Fight but no missions.
            if (!theory) {
                validateMissions(quiz, mode, langs, errs);
            }
            validateBossFight(quiz, langs, errs);
        }

        if (!errs.isEmpty()) {
            fail("Topic '" + name + "' has " + errs.size() + " contract violation(s):\n  - "
                    + String.join("\n  - ", errs));
        }
    }

    // --- field checks ------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void validateExamples(Path dir, Map<String, Object> meta, List<String> langs,
                                  List<String> errs) {
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
            localized(ex.get("title"), where + ".title", langs, errs);
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
    private void validateMissions(Map<String, Object> quiz, String mode, List<String> langs,
                                  List<String> errs) {
        Object raw = quiz.get("missions");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            errs.add("quiz.yaml: missions must be a non-empty list");
            return;
        }
        String defaultType = switch (mode) {
            case "structural" -> "structure";
            case "sql" -> "sql";
            case "challenge" -> "challenge";
            default -> "event";
        };
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
            localized(m.get("title"), where + ".title", langs, errs);
            localized(m.get("goal"), where + ".goal", langs, errs);
            // structure/sql missions are checked against a graph / query result
            // (need a non-empty `requires`); a trace mission is checked by event.
            String typeRaw = str(m, "type");
            String type = typeRaw.isBlank() ? defaultType : typeRaw;
            if ("structure".equals(type) || "sql".equals(type) || "challenge".equals(type)) {
                if (!(m.get("requires") instanceof List<?> req) || req.isEmpty()) {
                    errs.add(where + ": a " + type + " mission needs a non-empty 'requires' list");
                }
            } else if (str(m, "event").isBlank()) {
                errs.add(where + ": missing event");
            }
        }
    }

    private void validateSqlStarter(Path dir, List<String> errs) {
        if (!Files.exists(dir.resolve("starter").resolve("schema.sql"))) {
            errs.add("sql topic: missing starter/schema.sql (DDL + seed data)");
        }
        if (!Files.exists(dir.resolve("starter").resolve("query.sql"))) {
            errs.add("sql topic: missing starter/query.sql (the editor's starting query)");
        }
    }

    private void validateChallengeFiles(Path dir, List<String> errs) {
        if (!Files.exists(dir.resolve("starter").resolve("Solution.java"))) {
            errs.add("challenge topic: missing starter/Solution.java (the editable stub)");
        }
        Path harness = dir.resolve("harness");
        boolean anyJava = false;
        if (Files.isDirectory(harness)) {
            try (Stream<Path> walk = Files.walk(harness)) {
                anyJava = walk.anyMatch(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"));
            } catch (IOException e) {
                errs.add("challenge topic: could not read harness/ — " + firstLine(e.getMessage()));
            }
        }
        if (!anyJava) {
            errs.add("challenge topic: missing harness/ with a Main.java test driver");
        }
    }

    private void validateStarter(Path dir, List<String> errs) {
        Path starter = dir.resolve("starter");
        if (!Files.isDirectory(starter)) {
            errs.add("structural topic: missing starter/ folder");
            return;
        }
        try (Stream<Path> walk = Files.walk(starter)) {
            boolean anyJava = walk.anyMatch(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"));
            if (!anyJava) errs.add("structural topic: starter/ has no .java files");
        } catch (IOException e) {
            errs.add("structural topic: could not read starter/ — " + firstLine(e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private void validateBossFight(Map<String, Object> quiz, List<String> langs, List<String> errs) {
        Object raw = quiz.get("bossFight");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            errs.add("quiz.yaml: bossFight must be a non-empty list of "
                    + "{ id, <lang> per declared language }");
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
            for (String lang : langs) {
                if (str(q, lang).isBlank()) errs.add(where + ": missing " + lang);
            }
        }
    }

    /**
     * Every relative image reference in an explanation (e.g. {@code ](images/x.png)},
     * served at runtime through {@code /api/topics/<id>/assets/}) must point to an
     * existing file inside the topic folder — catches broken copies early.
     */
    private void validateImageLinks(Path explanation, List<String> errs) {
        if (!Files.exists(explanation)) return;
        String text;
        try {
            text = Files.readString(explanation, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return; // unreadable files are reported by requireNonEmptyFile
        }
        var m = java.util.regex.Pattern.compile("!\\[[^\\]]*\\]\\(([^)]+)\\)").matcher(text);
        while (m.find()) {
            String src = m.group(1).trim();
            if (src.matches("(?i)^([a-z][a-z0-9+.-]*:|/|#).*")) continue; // absolute / external
            if (src.contains("..")) {
                errs.add(explanation.getFileName() + ": image link '" + src + "' must not leave the topic folder");
                continue;
            }
            String decoded = java.net.URLDecoder.decode(src, StandardCharsets.UTF_8);
            if (!Files.isRegularFile(explanation.getParent().resolve(decoded))) {
                errs.add(explanation.getFileName() + ": image '" + src + "' does not exist in the topic folder");
            }
        }
    }

    /**
     * A translatable field must be present and, if a map, have a non-blank value
     * for every language the topic declares (default: both en and ru). A plain
     * string is always acceptable — the loader uses it for all languages.
     */
    private void localized(Object v, String where, List<String> langs, List<String> errs) {
        if (v == null) {
            errs.add(where + ": missing");
            return;
        }
        if (v instanceof Map<?, ?> m) {
            for (String lang : langs) {
                if (blank(m.get(lang))) errs.add(where + "." + lang + ": missing");
            }
        } else if (blank(v)) {
            errs.add(where + ": blank");
        }
    }

    /**
     * The topic's declared content languages ({@code languages:} in topic.yaml):
     * validated to be a non-empty subset of [en, ru]; absent = both.
     */
    private List<String> declaredLanguages(Map<String, Object> meta, List<String> errs) {
        Object raw = meta == null ? null : meta.get("languages");
        if (raw == null) {
            return ContentLanguages.LEGACY_DEFAULT;
        }
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            errs.add("topic.yaml: languages must be a non-empty list drawn from "
                    + ContentLanguages.ALL);
            return ContentLanguages.LEGACY_DEFAULT;
        }
        List<String> result = new ArrayList<>();
        for (Object o : list) {
            String lang = ContentLanguages.normalizeCode(String.valueOf(o));
            if (lang == null) {
                errs.add("topic.yaml: languages entry '" + o + "' is not a supported content "
                        + "language " + ContentLanguages.ALL);
            } else if (!result.contains(lang)) {
                result.add(lang);
            }
        }
        return result.isEmpty() ? ContentLanguages.LEGACY_DEFAULT : result;
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
