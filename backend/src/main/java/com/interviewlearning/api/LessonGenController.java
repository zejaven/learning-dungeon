package com.interviewlearning.api;

import com.interviewlearning.ai.AiCliService;
import com.interviewlearning.ai.AiTask;
import com.interviewlearning.config.RepoPaths;
import com.interviewlearning.generation.GenerationService;
import com.interviewlearning.generation.GenerationTask;
import com.interviewlearning.theory.TheoryVersionRepository;
import com.interviewlearning.topics.TopicDtos.BossQuestion;
import com.interviewlearning.topics.TopicDtos.Localized;
import com.interviewlearning.topics.TopicDtos.TopicDetail;
import com.interviewlearning.topics.TopicRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Starts a detached AI run that turns a topic's existing theory into
 * {@code topics/<id>/learning-atoms.json} (the "Learn by micro-actions"
 * lesson). Reuses the topic-generation task machinery: the task is keyed
 * {@code atoms:<topicId>} and the client attaches to the same
 * {@code /api/topics/generate/{taskId}/events} stream. The AI writes the JSON
 * file directly (same rationale as version regeneration: files avoid Windows
 * Cyrillic stdout mangling); a broken write is caught by the lenient runtime
 * loader, leaving the Generate button available for a retry.
 */
@RestController
public class LessonGenController {

    private static final Logger log = LoggerFactory.getLogger(LessonGenController.class);

    private final GenerationService generation;
    private final RepoPaths repoPaths;
    private final TopicRepository topics;
    private final TheoryVersionRepository versions;
    private final AiCliService ai;

    public LessonGenController(GenerationService generation,
                               RepoPaths repoPaths,
                               TopicRepository topics,
                               TheoryVersionRepository versions,
                               AiCliService ai) {
        this.generation = generation;
        this.repoPaths = repoPaths;
        this.topics = topics;
        this.versions = versions;
        this.ai = ai;
    }

    /** @param versionNo theory version to generate from; null/1 = the on-disk explanation */
    public record GenerateAtomsRequest(String provider, Integer versionNo) {
    }

    @PostMapping("/api/topics/{id}/atoms/generate")
    public ResponseEntity<Map<String, String>> generate(@PathVariable String id,
                                                        @RequestBody GenerateAtomsRequest request) {
        TopicDetail topic = topics.getTopic(id).orElse(null);
        if (topic == null) {
            return ResponseEntity.notFound().build();
        }
        String provider = request.provider() == null || request.provider().isBlank()
                ? "claude" : request.provider().trim().toLowerCase();
        int versionNo = request.versionNo() == null || request.versionNo() < 1 ? 1 : request.versionNo();
        Localized explanation = explanationOf(topic, versionNo);

        GenerationTask task = generation.startOrGet("atoms:" + id, provider,
                buildPrompt(topic, provider, versionNo, explanation), AiTask.GENERATE_ATOMS);
        return ResponseEntity.ok(Map.of("taskId", task.id(), "key", task.key(), "status", task.status()));
    }

    /** Version 1 is the on-disk explanation; 2+ come from the theory_version table. */
    private Localized explanationOf(TopicDetail topic, int versionNo) {
        if (versionNo > 1) {
            return versions.list(topic.id()).stream()
                    .filter(v -> v.versionNo() == versionNo)
                    .findFirst()
                    .map(v -> new Localized(v.en(), v.ru()))
                    .orElse(topic.explanation());
        }
        return topic.explanation();
    }

    private String buildPrompt(TopicDetail topic, String provider, int versionNo, Localized explanation) {
        Path promptFile = repoPaths.promptsDir().resolve("generate-learning-atoms.md");
        String contract;
        try {
            contract = Files.readString(promptFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("generate-learning-atoms.md not found at {}: {}", promptFile, e.getMessage());
            contract = "Convert the bilingual theory explanation below into a learning-atoms.json "
                    + "micro-action lesson file: 8-15 knowledge atoms, each with 1-3 prediction-first "
                    + "discovery exercises and 3-6 standalone practice exercises, all bilingual (en/ru), "
                    + "valid JSON written directly to the output path given at the end.";
        }

        String model = ai.modelFor(provider, AiTask.GENERATE_ATOMS);
        Path output = repoPaths.topicsDir().resolve(topic.id()).resolve("learning-atoms.json");

        StringBuilder sb = new StringBuilder(contract);
        sb.append("\n\n---\n\nTOPIC:\n")
                .append("- id: ").append(topic.id()).append("\n")
                .append("- title (en): ").append(topic.title().en()).append("\n")
                .append("- title (ru): ").append(topic.title().ru()).append("\n");

        sb.append("\nVALUES TO SET IN THE JSON:\n")
                .append("- topicId: ").append(topic.id()).append("\n")
                .append("- sourceVersion: ").append(versionNo).append("\n")
                .append("- aiProvider: ").append(provider).append("\n")
                .append("- aiModel: ").append(model == null ? "" : model.trim()).append("\n");

        List<BossQuestion> boss = topic.bossFight();
        if (boss != null && !boss.isEmpty()) {
            sb.append("\nBOSS-FIGHT QUESTIONS (context only — prepare for them, do not restate or include):\n");
            for (BossQuestion q : boss) {
                sb.append("- ").append(q.text().en()).append("\n");
            }
        }

        sb.append("\n---\n\nSOURCE EXPLANATION (ENGLISH):\n\n").append(explanation.en())
                .append("\n\n---\n\nSOURCE EXPLANATION (RUSSIAN):\n\n").append(explanation.ru())
                .append("\n\n---\n\nOUTPUT FILE (write the JSON exactly here):\n")
                .append(output.toAbsolutePath());
        return sb.toString();
    }
}
