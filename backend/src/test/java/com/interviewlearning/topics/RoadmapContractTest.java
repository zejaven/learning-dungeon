package com.interviewlearning.topics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract check for {@code frontend/src/roadmap.json}, the Java domain's order of
 * stages, modules and topics. The frontend has no tests, and a mistyped id there
 * only shows up as a topic quietly sliding into "not placed", so this is where it
 * gets caught. Completeness is deliberately not required: a freshly generated
 * topic is legitimately unplaced until someone lists it.
 *
 * <p>Run just this: {@code ./gradlew :backend:test --tests "*RoadmapContractTest"}.
 */
class RoadmapContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void roadmapListsExistingJavaTopicsOnce() throws IOException {
        JsonNode roadmap = JSON.readTree(Files.readString(root().resolve("frontend/src/roadmap.json")));
        List<String> errs = new ArrayList<>();
        Set<String> groupIds = new HashSet<>();
        Set<String> topicIds = new HashSet<>();

        JsonNode stages = roadmap.path("stages");
        assertTrue(stages.isArray() && !stages.isEmpty(), "roadmap.json: stages must be a non-empty array");
        for (JsonNode stage : stages) {
            checkGroup(stage, groupIds, errs);
            for (JsonNode module : stage.path("modules")) {
                checkGroup(module, groupIds, errs);
                for (JsonNode topic : module.path("topics")) {
                    String id = topic.asText();
                    if (!topicIds.add(id)) errs.add("topic listed twice: " + id);
                    checkTopic(id, errs);
                }
            }
        }
        assertEquals(List.of(), errs);
    }

    private void checkGroup(JsonNode group, Set<String> ids, List<String> errs) {
        String id = group.path("id").asText();
        if (id.isBlank()) errs.add("stage or module without id: " + group.path("name"));
        if (!ids.add(id)) errs.add("stage or module id used twice: " + id);
        for (String lang : List.of("en", "ru")) {
            if (group.path("name").path(lang).asText().isBlank()) errs.add(id + ": name." + lang + " is blank");
        }
    }

    private void checkTopic(String id, List<String> errs) throws IOException {
        Path yaml = root().resolve("topics").resolve(id).resolve("topic.yaml");
        if (!Files.exists(yaml)) {
            errs.add("no such topic: " + id);
            return;
        }
        Map<String, Object> meta = new Yaml().load(Files.readString(yaml, StandardCharsets.UTF_8));
        Object domainId = meta.get("domainId");
        if (domainId != null && !"java".equals(domainId)) {
            errs.add(id + ": belongs to domain " + domainId + ", not java");
        }
    }

    private static Path root() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            if (Files.isDirectory(p.resolve("topics"))) return p;
            p = p.getParent();
        }
        throw new IllegalStateException("Could not locate topics/ from " + Paths.get("").toAbsolutePath());
    }
}
