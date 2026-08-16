package com.interviewlearning.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewlearning.ai.AiCliService;
import com.interviewlearning.ai.AiTask;
import com.interviewlearning.config.RepoPaths;
import com.interviewlearning.questions.QuestionRepository;
import com.interviewlearning.questions.QuestionRepository.ManualQuestion;
import com.interviewlearning.topics.TopicDtos.Localized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Hand-added catalog questions. On add, the AI classifies the question into an
 * existing or new category, rates its difficulty and produces a bilingual
 * version. The selected AI provider writes the JSON to a file (not stdout) so Cyrillic isn't
 * mangled by the Windows console encoding.
 */
@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private static final Logger log = LoggerFactory.getLogger(QuestionController.class);

    private static final String CATEGORY_IDS = "java-core, java-collections, concurrency, memory-gc, "
            + "oop-design, exceptions, streams, algorithms, databases, spring, hibernate, "
            + "design-patterns, microservices, rest, networking, security, devops, performance, "
            + "kotlin, messaging, other";

    private final QuestionRepository questions;
    private final AiCliService ai;
    private final ObjectMapper mapper;
    private final RepoPaths repoPaths;

    public QuestionController(QuestionRepository questions,
                              AiCliService ai,
                              ObjectMapper mapper,
                              RepoPaths repoPaths) {
        this.questions = questions;
        this.ai = ai;
        this.mapper = mapper;
        this.repoPaths = repoPaths;
    }

    public record QuestionDto(String id, String categoryId, String categoryName,
                              int difficulty, Localized question) {
    }

    public record AddRequest(String text, String provider) {
    }

    @GetMapping
    public List<QuestionDto> list() {
        try {
            return questions.list().stream().map(QuestionController::toDto).toList();
        } catch (RuntimeException e) {
            log.warn("Could not list manual questions: {}", e.getMessage());
            return List.of();
        }
    }

    @PostMapping
    public ResponseEntity<?> add(@RequestBody AddRequest request) {
        if (request.text() == null || request.text().isBlank()) {
            return ResponseEntity.badRequest().body("Empty question.");
        }
        Path dir;
        try {
            dir = Files.createTempDirectory("classify-");
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Could not create temp dir: " + e.getMessage());
        }
        Path jsonPath = dir.resolve("out.json");
        try {
            ai.runForResult(request.provider(), classifyPrompt(request.text().trim(), jsonPath), AiTask.CLASSIFY);
            JsonNode c = mapper.readTree(Files.readString(jsonPath, StandardCharsets.UTF_8));

            String categoryId = text(c, "categoryId", "other");
            String categoryName = c.hasNonNull("categoryName") ? c.get("categoryName").asText() : null;
            int difficulty = clampDifficulty(c.path("difficulty").asInt(2));
            String en = text(c, "en", request.text().trim());
            String ru = text(c, "ru", en);

            ManualQuestion saved = questions.add(categoryId, categoryName, difficulty, en, ru);
            return ResponseEntity.ok(toDto(saved));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Classification failed: " + e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body("Classification failed: " + e.getMessage());
        } finally {
            deleteDir(dir);
        }
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        String numeric = id.startsWith("manual-") ? id.substring("manual-".length()) : id;
        try {
            questions.delete(Long.parseLong(numeric));
        } catch (NumberFormatException ignored) {
            // bad id — nothing to delete
        }
    }

    private static QuestionDto toDto(ManualQuestion q) {
        // manual_question is an en/ru table by design (java-domain catalog only).
        return new QuestionDto("manual-" + q.id(), q.categoryId(), q.categoryName(),
                q.difficulty(), Localized.fromJson(Map.of("en", q.en(), "ru", q.ru())));
    }

    private static int clampDifficulty(int d) {
        return d < 1 || d > 3 ? 2 : d;
    }

    private static String text(JsonNode node, String field, String fallback) {
        return node.hasNonNull(field) && !node.get(field).asText().isBlank()
                ? node.get(field).asText() : fallback;
    }

    private String classifyPrompt(String question, Path jsonPath) {
        return "Classify a Java interview question for a catalog. Write a JSON object (UTF-8) to the "
                + "file:\n" + jsonPath.toAbsolutePath() + "\n"
                + "with exactly these fields and nothing else:\n"
                + "{\"categoryId\":\"<id>\",\"categoryName\":\"<only if categoryId is a NEW id>\","
                + "\"difficulty\":<1|2|3>,\"en\":\"<question in English>\",\"ru\":\"<question in Russian>\"}\n"
                + "Rules:\n"
                + "- categoryId: pick the single best fit from: " + CATEGORY_IDS + ". If none truly fits, "
                + "invent a NEW kebab-case id (not in that list) and set categoryName to a short human label "
                + "(otherwise omit categoryName).\n"
                + "- difficulty: 1 = Junior, 2 = Middle, 3 = Senior.\n"
                + "- en / ru: the question in English and Russian (translate the input; keep code and "
                + "technical terms like Java, HashMap, hashCode untranslated).\n"
                + "Write ONLY the JSON to that file; print nothing to stdout.\n\n"
                + "QUESTION:\n" + question;
    }

    private void deleteDir(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
