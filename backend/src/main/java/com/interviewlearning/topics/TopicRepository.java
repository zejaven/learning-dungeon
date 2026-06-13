package com.interviewlearning.topics;

import com.interviewlearning.config.RepoPaths;
import com.interviewlearning.topics.TopicDtos.Example;
import com.interviewlearning.topics.TopicDtos.Mission;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Reads topic plugins from the {@code topics/} directory on each request, so
 * topics added at runtime (e.g. by the add-topic feature) appear without a
 * restart. Each topic is a folder with topic.yaml, explanation.md, an
 * examples/ folder and quiz.yaml.
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
                                    str(meta, "title", p.getFileName().toString()),
                                    str(meta, "category", ""),
                                    str(meta, "type", "OTHER"),
                                    str(meta, "summary", "")
                            ))));
        } catch (IOException e) {
            log.warn("Failed to list topics: {}", e.getMessage());
        }
        return result;
    }

    public Optional<TopicDetail> getTopic(String id) {
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

        String explanation = readFile(topicDir.resolve("explanation.md"));
        List<Example> examples = loadExamples(topicDir, meta);
        List<Mission> missions = loadMissions(topicDir, meta);
        List<String> primitives = stringList(meta.get("primitives"));

        return Optional.of(new TopicDetail(
                str(meta, "id", id),
                str(meta, "title", id),
                str(meta, "category", ""),
                str(meta, "type", "OTHER"),
                str(meta, "summary", ""),
                primitives,
                explanation,
                examples,
                str(meta, "defaultExample", examples.isEmpty() ? "" : examples.get(0).id()),
                missions
        ));
    }

    @SuppressWarnings("unchecked")
    private List<Example> loadExamples(Path topicDir, Map<String, Object> meta) {
        List<Example> examples = new ArrayList<>();
        Object raw = meta.get("examples");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> ex = (Map<String, Object>) m;
                    String file = str(ex, "file", "");
                    String code = file.isEmpty()
                            ? str(ex, "code", "")
                            : readFile(topicDir.resolve("examples").resolve(file));
                    examples.add(new Example(
                            str(ex, "id", file),
                            str(ex, "title", file),
                            code,
                            str(ex, "explanation", "")
                    ));
                }
            }
        }
        return examples;
    }

    @SuppressWarnings("unchecked")
    private List<Mission> loadMissions(Path topicDir, Map<String, Object> meta) {
        Path quiz = topicDir.resolve(str(meta, "missionsFile", "quiz.yaml"));
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
                                str(mm, "title", ""),
                                str(mm, "goal", ""),
                                str(mm, "event", "")
                        ));
                    }
                }
            }
            return missions;
        }).orElse(List.of());
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
