package com.interviewlearning.api;

import com.interviewlearning.ai.AiTask;
import com.interviewlearning.generation.AtomsPromptBuilder;
import com.interviewlearning.generation.GenerationService;
import com.interviewlearning.generation.GenerationTask;
import com.interviewlearning.topics.TopicDtos.Localized;
import com.interviewlearning.topics.TopicDtos.TopicDetail;
import com.interviewlearning.topics.TopicRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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

    private final GenerationService generation;
    private final TopicRepository topics;
    private final AtomsPromptBuilder prompts;

    public LessonGenController(GenerationService generation,
                               TopicRepository topics,
                               AtomsPromptBuilder prompts) {
        this.generation = generation;
        this.topics = topics;
        this.prompts = prompts;
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
        Localized explanation = prompts.explanationOf(topic, versionNo);
        boolean augment = "augment".equalsIgnoreCase(request.mode() == null ? "" : request.mode().trim());
        String comment = request.comment() == null ? "" : request.comment().trim();
        // Augmenting requires an existing lesson to extend; fall back to a full run otherwise.
        String existingAtoms = augment ? prompts.readExistingAtoms(topic.id()) : null;
        if (augment && existingAtoms == null) {
            augment = false;
        }

        GenerationTask task = generation.startOrGet("atoms:" + id, provider,
                prompts.build(topic, provider, versionNo, explanation, augment, comment, existingAtoms),
                AiTask.GENERATE_ATOMS);
        return ResponseEntity.ok(Map.of("taskId", task.id(), "key", task.key(), "status", task.status()));
    }
}
