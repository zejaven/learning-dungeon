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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

    /** The categories that exist in the static catalog; a planned entry in any other is silently dropped. */
    private static final Set<String> CATEGORIES = Set.of(
            "java-core", "java-collections", "concurrency", "memory-gc", "oop-design", "exceptions", "streams",
            "algorithms", "databases", "spring", "hibernate", "design-patterns", "microservices", "rest",
            "networking", "security", "devops", "performance", "kotlin", "messaging");

    @Test
    void roadmapItemsAreValidAndListedOnce() throws IOException {
        JsonNode roadmap = JSON.readTree(Files.readString(root().resolve("frontend/src/roadmap.json")));
        List<String> errs = new ArrayList<>();
        Set<String> groupIds = new HashSet<>();
        Set<String> topicIds = new HashSet<>();
        Map<String, JsonNode> modules = new LinkedHashMap<>();

        JsonNode stages = roadmap.path("stages");
        assertTrue(stages.isArray() && !stages.isEmpty(), "roadmap.json: stages must be a non-empty array");
        for (JsonNode stage : stages) {
            checkGroup(stage, groupIds, errs);
            for (JsonNode module : stage.path("modules")) {
                checkGroup(module, groupIds, errs);
                modules.put(module.path("id").asText(), module);
                for (JsonNode item : module.path("topics")) {
                    if (item.isObject()) {
                        checkPlanned(item, topicIds, errs);
                    } else {
                        String id = item.asText();
                        if (!topicIds.add(id)) errs.add("topic listed twice: " + id);
                        checkTopic(id, errs);
                    }
                }
            }
        }
        checkMap(modules, errs);
        assertEquals(List.of(), errs);
    }

    /** The map draws modules at their cell and arrows from `requires`: both must make a drawable graph. */
    private void checkMap(Map<String, JsonNode> modules, List<String> errs) {
        Set<String> cells = new HashSet<>();
        Map<String, List<String>> requires = new HashMap<>();
        modules.forEach((id, module) -> {
            JsonNode pos = module.path("pos");
            if (pos.size() != 2 || !pos.get(0).isIntegralNumber() || !pos.get(1).isIntegralNumber()) {
                errs.add(id + ": pos must be [column, row]");
            } else if (!cells.add(pos.get(0).asInt() + ":" + pos.get(1).asInt())) {
                errs.add(id + ": shares its cell with another module: " + pos);
            }
            List<String> required = new ArrayList<>();
            for (JsonNode req : module.path("requires")) {
                if (!modules.containsKey(req.asText())) errs.add(id + ": requires unknown module " + req.asText());
                required.add(req.asText());
            }
            requires.put(id, required);
        });

        // Kahn: whatever cannot be peeled off is on a cycle.
        Set<String> left = new HashSet<>(requires.keySet());
        boolean peeled = true;
        while (peeled) {
            peeled = left.removeIf(id -> requires.get(id).stream().noneMatch(left::contains));
        }
        if (!left.isEmpty()) errs.add("requires has a cycle through: " + left);
    }

    private void checkGroup(JsonNode group, Set<String> ids, List<String> errs) {
        String id = group.path("id").asText();
        if (id.isBlank()) errs.add("stage or module without id: " + group.path("name"));
        if (!ids.add(id)) errs.add("stage or module id used twice: " + id);
        for (String lang : List.of("en", "ru")) {
            if (group.path("name").path(lang).asText().isBlank()) errs.add(id + ": name." + lang + " is blank");
        }
    }

    /** A planned question is a catalog entry waiting for theory, so it must be able to become one. */
    private void checkPlanned(JsonNode item, Set<String> ids, List<String> errs) {
        String id = item.path("planned").asText();
        if (!id.matches("plan-[a-z0-9]+(-[a-z0-9]+)*")) errs.add("bad planned id: '" + id + "'");
        if (!ids.add(id)) errs.add("planned question listed twice: " + id);
        for (String lang : List.of("en", "ru")) {
            if (item.path("question").path(lang).asText().isBlank()) errs.add(id + ": question." + lang + " is blank");
        }
        int difficulty = item.path("difficulty").asInt();
        if (difficulty < 1 || difficulty > 3) errs.add(id + ": difficulty must be 1-3, got " + difficulty);
        String category = item.path("categoryId").asText();
        if (!CATEGORIES.contains(category)) errs.add(id + ": unknown categoryId '" + category + "'");
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
