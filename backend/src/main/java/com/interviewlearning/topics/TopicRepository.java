package com.interviewlearning.topics;

import com.interviewlearning.config.RepoPaths;
import com.interviewlearning.lang.ContentLanguages;
import com.interviewlearning.topics.TopicDtos.BossQuestion;
import com.interviewlearning.topics.TopicDtos.Example;
import com.interviewlearning.topics.TopicDtos.Localized;
import com.interviewlearning.topics.TopicDtos.Mission;
import com.interviewlearning.topics.TopicDtos.ProjectFile;
import com.interviewlearning.topics.TopicDtos.TopicDetail;
import com.interviewlearning.topics.TopicDtos.TopicSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Reads topic plugins from the {@code topics/} directory on each request, so
 * topics added at runtime (e.g. by the add-topic feature) appear without a
 * restart. Each topic is a folder with topic.yaml, explanation.en.md /
 * explanation.ru.md, an examples/ folder and quiz.yaml.
 *
 * <p>Translatable fields in topic.yaml may be either a plain string (used for
 * both languages) or a map {@code { en: ..., ru: ... }}.
 */
@Repository
public class TopicRepository {

    private static final Logger log = LoggerFactory.getLogger(TopicRepository.class);

    private final RepoPaths repoPaths;

    public TopicRepository(RepoPaths repoPaths) {
        this.repoPaths = repoPaths;
    }

    public List<TopicSummary> listTopics() {
        Path dir = repoPaths.topicsDir();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<TopicSummary> result = new ArrayList<>();
        try (Stream<Path> entries = Files.list(dir)) {
            entries.filter(Files::isDirectory)
                    .filter(p -> Files.exists(p.resolve("topic.yaml")))
                    .sorted()
                    .forEach(p -> loadYaml(p.resolve("topic.yaml")).ifPresent(meta ->
                            result.add(new TopicSummary(
                                    str(meta, "id", p.getFileName().toString()),
                                    loc(meta, "title", p.getFileName().toString()),
                                    loc(meta, "category", ""),
                                    str(meta, "type", "OTHER"),
                                    loc(meta, "summary", ""),
                                    false,
                                    str(meta, "categoryId", ""),
                                    str(meta, "categoryName", ""),
                                    intVal(meta, "difficulty", 0),
                                    str(meta, "catalogId", ""),
                                    str(meta, "mode", "trace"),
                                    str(meta, "aiProvider", "claude"),
                                    str(meta, "aiModel", ""),
                                    Files.exists(p.resolve("learning-atoms.json")),
                                    str(meta, "domainId", "java"),
                                    intVal(meta, "order", 0)
                            ))));
        } catch (IOException e) {
            log.warn("Failed to list topics: {}", e.getMessage());
        }
        return result;
    }

    public Optional<TopicDetail> getTopic(String id) {
        if (id == null || !id.matches("[a-zA-Z0-9_-]+")) {
            return Optional.empty(); // guard against path traversal in the topic id
        }
        Path topicDir = repoPaths.topicsDir().resolve(id);
        Path yaml = topicDir.resolve("topic.yaml");
        if (!Files.exists(yaml)) {
            return Optional.empty();
        }
        Optional<Map<String, Object>> metaOpt = loadYaml(yaml);
        if (metaOpt.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> meta = metaOpt.get();

        Localized explanation = readExplanation(topicDir);
        List<Example> examples = loadExamples(topicDir, meta);
        List<Mission> missions = loadMissions(topicDir, meta);
        List<BossQuestion> bossFight = loadBossFight(topicDir, meta);
        List<String> primitives = stringList(meta.get("primitives"));
        String mode = str(meta, "mode", "trace");
        // structural / sql / challenge topics seed an editor from starter/.
        List<ProjectFile> starterFiles =
                "structural".equals(mode) || "sql".equals(mode) || "challenge".equals(mode)
                        ? loadStarterFiles(topicDir)
                        : List.of();

        return Optional.of(new TopicDetail(
                str(meta, "id", id),
                loc(meta, "title", id),
                loc(meta, "category", ""),
                str(meta, "type", "OTHER"),
                loc(meta, "summary", ""),
                primitives,
                explanation,
                examples,
                str(meta, "defaultExample", examples.isEmpty() ? "" : examples.get(0).id()),
                missions,
                loc(meta, "assistantExample", ""),
                bossFight,
                mode,
                starterFiles,
                str(meta, "style", "Default"),
                str(meta, "aiProvider", "claude"),
                str(meta, "aiModel", ""),
                Files.exists(topicDir.resolve("learning-atoms.json")),
                str(meta, "domainId", "java"),
                languages(meta)
        ));
    }

    /**
     * Reads one explanation.&lt;lang&gt;.md per registered language, falling back to a
     * legacy explanation.md. A language with no file is left absent rather than
     * filled from another one, so the UI can say the translation is missing.
     */
    private Localized readExplanation(Path topicDir) {
        Map<String, String> texts = new LinkedHashMap<>();
        for (String lang : ContentLanguages.ALL) {
            String text = readFile(topicDir.resolve("explanation." + lang + ".md"));
            if (!text.isBlank()) {
                texts.put(lang, text);
            }
        }
        if (texts.isEmpty()) {
            // Pre-bilingual topics: one file that serves every language.
            String legacy = readFile(topicDir.resolve("explanation.md"));
            return legacy.isBlank() ? Localized.empty() : Localized.of(legacy);
        }
        return new Localized(texts);
    }

    @SuppressWarnings("unchecked")
    private List<Example> loadExamples(Path topicDir, Map<String, Object> meta) {
        List<Example> examples = new ArrayList<>();
        Path examplesDir = topicDir.resolve("examples");
        Object raw = meta.get("examples");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> ex = (Map<String, Object>) m;
                    String file = str(ex, "file", "");
                    String code = file.isEmpty()
                            ? str(ex, "code", "")
                            : readContainedFile(examplesDir, file);
                    examples.add(new Example(
                            str(ex, "id", file),
                            loc(ex, "title", file),
                            code,
                            loc(ex, "explanation", "")
                    ));
                }
            }
        }
        return examples;
    }

    /** Reads {@code rel} inside {@code baseDir}, refusing paths that escape it (e.g. {@code ../}). */
    private String readContainedFile(Path baseDir, String rel) {
        Path p = baseDir.resolve(rel).normalize();
        if (!p.startsWith(baseDir)) {
            log.warn("Refusing file outside topic dir: {}", rel);
            return "";
        }
        return readFile(p);
    }

    /** Resolves missionsFile inside the topic dir, falling back to quiz.yaml if it escapes. */
    private Path resolveMissionsFile(Path topicDir, Map<String, Object> meta) {
        Path p = topicDir.resolve(str(meta, "missionsFile", "quiz.yaml")).normalize();
        if (!p.startsWith(topicDir)) {
            log.warn("missionsFile escapes topic dir, using quiz.yaml instead");
            return topicDir.resolve("quiz.yaml");
        }
        return p;
    }

    @SuppressWarnings("unchecked")
    private List<Mission> loadMissions(Path topicDir, Map<String, Object> meta) {
        Path quiz = resolveMissionsFile(topicDir, meta);
        if (!Files.exists(quiz)) {
            return List.of();
        }
        return loadYaml(quiz).map(q -> {
            List<Mission> missions = new ArrayList<>();
            Object raw = q.get("missions");
            if (raw instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        Map<String, Object> mm = (Map<String, Object>) m;
                        missions.add(new Mission(
                                str(mm, "id", ""),
                                loc(mm, "title", ""),
                                loc(mm, "goal", ""),
                                str(mm, "event", ""),
                                str(mm, "type", "event"),
                                objectList(mm.get("requires"))
                        ));
                    }
                }
            }
            return missions;
        }).orElse(List.of());
    }

    /**
     * Reads the {@code bossFight} interview questions from quiz.yaml. The canonical
     * format is a list of {@code { id, en, ru }} objects. For backward
     * compatibility a legacy {@code { en: [...], ru: [...] }} map (or a plain list)
     * is also accepted; synthetic ids {@code q1, q2, ...} are assigned then.
     */
    @SuppressWarnings("unchecked")
    private List<BossQuestion> loadBossFight(Path topicDir, Map<String, Object> meta) {
        Path quiz = resolveMissionsFile(topicDir, meta);
        if (!Files.exists(quiz)) {
            return List.of();
        }
        return loadYaml(quiz).map(q -> {
            Object raw = q.get("bossFight");
            List<BossQuestion> result = new ArrayList<>();
            if (raw instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?>) {
                int i = 0;
                for (Object item : list) {
                    i++;
                    if (item instanceof Map<?, ?> m) {
                        Map<String, Object> qm = (Map<String, Object>) m;
                        String id = str(qm, "id", "q" + i);
                        Map<String, String> texts = new LinkedHashMap<>();
                        for (String lang : ContentLanguages.ALL) {
                            String text = str(qm, lang, "");
                            if (!text.isBlank()) {
                                texts.put(lang, text);
                            }
                        }
                        result.add(new BossQuestion(id, new Localized(texts)));
                    }
                }
            } else if (raw instanceof Map<?, ?> m) {
                Map<String, List<String>> perLang = new LinkedHashMap<>();
                int n = 0;
                for (String lang : ContentLanguages.ALL) {
                    List<String> questions = stringList(m.get(lang));
                    perLang.put(lang, questions);
                    n = Math.max(n, questions.size());
                }
                for (int i = 0; i < n; i++) {
                    Map<String, String> texts = new LinkedHashMap<>();
                    for (String lang : ContentLanguages.ALL) {
                        List<String> questions = perLang.get(lang);
                        if (i < questions.size() && !questions.get(i).isBlank()) {
                            texts.put(lang, questions.get(i));
                        }
                    }
                    result.add(new BossQuestion("q" + (i + 1), new Localized(texts)));
                }
            } else if (raw instanceof List<?> list) {
                int i = 0;
                for (Object o : list) {
                    i++;
                    // A bare list has no language at all: the same text serves every one.
                    result.add(new BossQuestion("q" + i, Localized.of(String.valueOf(o))));
                }
            }
            return result;
        }).orElse(List.of());
    }

    /**
     * Reads a structural topic's starter project: every file under
     * {@code topics/<id>/starter/}, with its path relative to that folder
     * (forward slashes), so the editor can seed the file tree.
     */
    private List<ProjectFile> loadStarterFiles(Path topicDir) {
        Path starter = topicDir.resolve("starter");
        if (!Files.isDirectory(starter)) {
            return List.of();
        }
        List<ProjectFile> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(starter)) {
            walk.filter(Files::isRegularFile).sorted().forEach(p -> {
                String rel = starter.relativize(p).toString().replace('\\', '/');
                files.add(new ProjectFile(rel, readFile(p)));
            });
        } catch (IOException e) {
            log.warn("Failed to read starter files in {}: {}", starter, e.getMessage());
        }
        return files;
    }

    /**
     * Reads a challenge topic's hidden test harness: every file under
     * {@code topics/<id>/harness/}. It is compiled and run together with the
     * learner's solution but is never sent to the frontend.
     */
    public List<ProjectFile> harnessFiles(String topicId) {
        Path harness = repoPaths.topicsDir().resolve(topicId).resolve("harness");
        if (!Files.isDirectory(harness)) {
            return List.of();
        }
        List<ProjectFile> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(harness)) {
            walk.filter(Files::isRegularFile).sorted().forEach(p ->
                    files.add(new ProjectFile(harness.relativize(p).toString().replace('\\', '/'), readFile(p))));
        } catch (IOException e) {
            log.warn("Failed to read harness in {}: {}", harness, e.getMessage());
        }
        return files;
    }

    private static List<Object> objectList(Object raw) {
        if (raw instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }

    private Optional<Map<String, Object>> loadYaml(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            Object parsed = new Yaml().load(content);
            if (parsed instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                return Optional.of(typed);
            }
            return Optional.empty();
        } catch (IOException e) {
            log.warn("Failed to read {}: {}", path, e.getMessage());
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("Failed to parse {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    private String readFile(Path path) {
        try {
            return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            log.warn("Failed to read {}: {}", path, e.getMessage());
            return "";
        }
    }

    private static String str(Map<String, Object> map, String key, String fallback) {
        Object v = map.get(key);
        return v == null ? fallback : String.valueOf(v).trim();
    }

    private static int intVal(Map<String, Object> map, String key, int fallback) {
        Object v = map.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v != null) {
            try {
                return Integer.parseInt(String.valueOf(v).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    /**
     * Reads the topic's declared content languages ({@code languages:} in
     * topic.yaml). Absent or invalid means the legacy bilingual pair, NOT every
     * registered language: a newly registered language must not be claimed
     * retroactively by topics that carry no text in it.
     */
    private static List<String> languages(Map<String, Object> meta) {
        List<String> declared = stringList(meta.get("languages")).stream()
                .map(ContentLanguages::normalizeCode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return declared.isEmpty() ? ContentLanguages.LEGACY_DEFAULT : declared;
    }

    /**
     * Reads a translatable field: a {@code { <lang>: ... }} map carrying one entry
     * per language it was written in, or a plain scalar, which means the same text
     * serves every language (that is how single-language topics are authored).
     */
    private static Localized loc(Map<String, Object> map, String key, String fallback) {
        Object v = map.get(key);
        if (v instanceof Map<?, ?> m) {
            Map<String, String> texts = new LinkedHashMap<>();
            for (String lang : ContentLanguages.ALL) {
                Object text = m.get(lang);
                if (text != null && !String.valueOf(text).isBlank()) {
                    texts.put(lang, String.valueOf(text).trim());
                }
            }
            return texts.isEmpty() ? Localized.of(fallback) : new Localized(texts);
        }
        String s = v == null ? fallback : String.valueOf(v).trim();
        return Localized.of(s);
    }

    private static List<String> stringList(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object o : list) {
                result.add(String.valueOf(o));
            }
            return result;
        }
        return List.of();
    }
}
