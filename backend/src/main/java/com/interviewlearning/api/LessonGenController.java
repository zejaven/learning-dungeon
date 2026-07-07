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

    /**
     * @param versionNo theory version to generate from; null/1 = the on-disk explanation
     * @param mode      "augment" (keep the existing lesson and add to it) or "full"
     *                  (replace everything); null defaults to "full"
     * @param comment   for "augment": what to add (required); for "full": things the
     *                  lesson must cover (optional, an extra requirement — not the basis
     *                  for the whole lesson)
     */
    public record GenerateAtomsRequest(String provider, Integer versionNo, String mode, String comment) {
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
        boolean augment = "augment".equalsIgnoreCase(request.mode() == null ? "" : request.mode().trim());
        String comment = request.comment() == null ? "" : request.comment().trim();
        // Augmenting requires an existing lesson to extend; fall back to a full run otherwise.
        String existingAtoms = augment ? readExistingAtoms(topic.id()) : null;
        if (augment && existingAtoms == null) {
            augment = false;
        }

        GenerationTask task = generation.startOrGet("atoms:" + id, provider,
                buildPrompt(topic, provider, versionNo, explanation, augment, comment, existingAtoms),
                AiTask.GENERATE_ATOMS);
        return ResponseEntity.ok(Map.of("taskId", task.id(), "key", task.key(), "status", task.status()));
    }

    /** The current learning-atoms.json contents, or null if there is no lesson to augment. */
    private String readExistingAtoms(String topicId) {
        Path file = repoPaths.topicsDir().resolve(topicId).resolve("learning-atoms.json");
        if (!Files.exists(file)) {
            return null;
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8).trim();
            return content.isEmpty() ? null : content;
        } catch (IOException e) {
            log.warn("Could not read existing atoms for augment at {}: {}", file, e.getMessage());
            return null;
        }
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

    private String buildPrompt(TopicDetail topic, String provider, int versionNo, Localized explanation,
                               boolean augment, String comment, String existingAtoms) {
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

        if (augment) {
            sb.append("\n---\n\nMODE: AUGMENT AN EXISTING LESSON.\n")
                    .append("A learning-atoms.json already exists for this topic (shown below). Do NOT ")
                    .append("regenerate it from scratch. Keep the existing atoms and their exercises ")
                    .append("intact, and ADD new atoms and/or exercises to satisfy the addition ")
                    .append("requested below. Preserve every existing id unchanged, keep all new ids ")
                    .append("kebab-case and unique across the whole file, keep exactly one capstone ")
                    .append("atom and keep it LAST, and write the full merged JSON (existing + new) to ")
                    .append("the output file. The rest of the contract above still applies to any new ")
                    .append("content.\n\nADDITION TO MAKE:\n").append(comment)
                    .append("\n\n---\n\nEXISTING LEARNING-ATOMS.JSON:\n\n").append(existingAtoms);
        } else if (!comment.isEmpty()) {
            sb.append("\n---\n\nMUST INCLUDE (an extra requirement, NOT the basis for the whole ")
                    .append("lesson): the lesson must definitely cover the following, in addition to ")
                    .append("faithfully covering the full source explanation below:\n").append(comment);
        }

        sb.append("\n---\n\nSOURCE EXPLANATION (ENGLISH):\n\n").append(explanation.en())
                .append("\n\n---\n\nSOURCE EXPLANATION (RUSSIAN):\n\n").append(explanation.ru())
                .append("\n\n---\n\nOUTPUT FILE (write the JSON exactly here):\n")
                .append(output.toAbsolutePath());
        return sb.toString();
    }
}
