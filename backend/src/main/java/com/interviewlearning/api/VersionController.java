package com.interviewlearning.api;

import com.interviewlearning.claude.ClaudeCodeService;
import com.interviewlearning.theory.TheoryVersionRepository;
import com.interviewlearning.theory.TheoryVersionRepository.TheoryVersion;
import com.interviewlearning.topics.TopicDtos.TopicDetail;
import com.interviewlearning.topics.TopicRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Theory versions of a topic. Version 1 is the on-disk explanation (with the
 * style it was generated in); versions 2+ are restyled regenerations stored in
 * the DB. Each version records its style, and the user can switch between them.
 */
@RestController
@RequestMapping("/api/topics/{id}/versions")
public class VersionController {

    private final TopicRepository topics;
    private final TheoryVersionRepository versions;
    private final ClaudeCodeService claude;
    private final String model;

    public VersionController(TopicRepository topics,
                             TheoryVersionRepository versions,
                             ClaudeCodeService claude,
                             @Value("${app.claude.generate-model:}") String model) {
        this.topics = topics;
        this.versions = versions;
        this.claude = claude;
        this.model = model;
    }

    public record VersionDto(int versionNo, String style, String en, String ru, String createdAt) {
    }

    /** Body of a regeneration request: the style instruction + its display name. */
    public record RegenerateRequest(String style, String styleName) {
    }

    @GetMapping
    public ResponseEntity<List<VersionDto>> list(@PathVariable String id) {
        Optional<TopicDetail> opt = topics.getTopic(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        TopicDetail topic = opt.get();
        List<VersionDto> result = new ArrayList<>();
        // Version 1: the canonical on-disk explanation.
        result.add(new VersionDto(1, topic.style(), topic.explanation().en(), topic.explanation().ru(), null));
        for (TheoryVersion v : safeList(id)) {
            result.add(new VersionDto(v.versionNo(), v.style(), v.en(), v.ru(), v.createdAt()));
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<?> regenerate(@PathVariable String id, @RequestBody RegenerateRequest request) {
        Optional<TopicDetail> opt = topics.getTopic(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        TopicDetail topic = opt.get();

        String prompt = buildPrompt(topic, request.style());
        String output;
        try {
            output = claude.runForResult(prompt, modelArgs());
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }

        String[] parsed = parse(output);
        if (parsed == null) {
            return ResponseEntity.internalServerError()
                    .body("Could not parse the regenerated explanation (missing ===EN===/===RU=== markers).");
        }
        String styleName = request.styleName() == null || request.styleName().isBlank()
                ? "Default" : request.styleName().trim();
        int versionNo = versions.add(id, styleName, parsed[0], parsed[1]);
        return ResponseEntity.ok(new VersionDto(versionNo, styleName, parsed[0], parsed[1], null));
    }

    private List<TheoryVersion> safeList(String id) {
        try {
            return versions.list(id);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private List<String> modelArgs() {
        return model == null || model.isBlank() ? List.of() : List.of("--model", model);
    }

    private String buildPrompt(TopicDetail topic, String style) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are rewriting ONLY the explanation prose of an existing Java interview "
                + "learning topic. Keep the SAME technical content, facts, structure, headings, "
                + "Mermaid diagrams and code blocks — change only the prose. Stay fully bilingual "
                + "(English + Russian) and technically accurate.\n\n");
        if (style != null && !style.isBlank()) {
            sb.append("STYLE: weave a short analogy from this theme into each technical point, in "
                    + "BOTH languages, to aid memorisation: ").append(style.trim()).append("\n")
                    .append("Accuracy comes first — the analogy supplements, never replaces, "
                            + "correctness; keep it concise; do NOT restyle code, Mermaid diagrams "
                            + "or identifiers.\n\n");
        } else {
            sb.append("Write clear default prose with no special style.\n\n");
        }
        sb.append("TOPIC: ").append(topic.title().en()).append("\n\n");
        sb.append("CURRENT ENGLISH EXPLANATION:\n").append(topic.explanation().en()).append("\n\n");
        sb.append("CURRENT RUSSIAN EXPLANATION:\n").append(topic.explanation().ru()).append("\n\n");
        sb.append("Output EXACTLY this and nothing else — no preamble, no surrounding code fences:\n")
                .append("===EN===\n<rewritten English explanation markdown>\n===RU===\n"
                        + "<rewritten Russian explanation markdown>\n");
        return sb.toString();
    }

    /** Splits Claude's {@code ===EN=== ... ===RU=== ...} output; null if malformed. */
    private String[] parse(String output) {
        int en = output.indexOf("===EN===");
        int ru = output.indexOf("===RU===");
        if (en < 0 || ru < 0 || ru < en) {
            return null;
        }
        String enText = output.substring(en + "===EN===".length(), ru).trim();
        String ruText = output.substring(ru + "===RU===".length()).trim();
        if (enText.isEmpty() || ruText.isEmpty()) {
            return null;
        }
        return new String[] {enText, ruText};
    }
}
