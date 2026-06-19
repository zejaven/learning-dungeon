package com.interviewlearning.api;

import com.interviewlearning.claude.ClaudeCodeService;
import com.interviewlearning.config.RepoPaths;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Theory versions of a topic. Version 1 is the on-disk explanation (with the
 * style it was generated in); versions 2+ are restyled regenerations stored in
 * the DB, each recording its style.
 *
 * <p>Claude writes the rewritten explanation to files (not stdout) — files are
 * always correct UTF-8, whereas Windows can mangle Cyrillic on a stdout pipe.
 */
@RestController
@RequestMapping("/api/topics/{id}/versions")
public class VersionController {

    private final TopicRepository topics;
    private final TheoryVersionRepository versions;
    private final ClaudeCodeService claude;
    private final RepoPaths repoPaths;
    private final String model;

    public VersionController(TopicRepository topics,
                             TheoryVersionRepository versions,
                             ClaudeCodeService claude,
                             RepoPaths repoPaths,
                             @Value("${app.claude.generate-model:}") String model) {
        this.topics = topics;
        this.versions = versions;
        this.claude = claude;
        this.repoPaths = repoPaths;
        this.model = model;
    }

    public record VersionDto(int versionNo, String style, String en, String ru, String createdAt) {
    }

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

        Path dir;
        try {
            dir = Files.createTempDirectory(repoPaths.repoRoot(), "regen-");
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Could not create temp dir: " + e.getMessage());
        }
        Path enPath = dir.resolve("en.md");
        Path ruPath = dir.resolve("ru.md");
        try {
            claude.runForResult(buildPrompt(topic, request.style(), enPath, ruPath), fileArgs());
            String en = Files.readString(enPath, StandardCharsets.UTF_8).trim();
            String ru = Files.readString(ruPath, StandardCharsets.UTF_8).trim();
            if (en.isEmpty() || ru.isEmpty()) {
                return ResponseEntity.internalServerError().body("The regenerated explanation was empty.");
            }
            String styleName = request.styleName() == null || request.styleName().isBlank()
                    ? "Default" : request.styleName().trim();
            int versionNo = versions.add(id, styleName, en, ru);
            return ResponseEntity.ok(new VersionDto(versionNo, styleName, en, ru, null));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body("Claude did not write the explanation files: " + e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        } finally {
            deleteDir(dir);
        }
    }

    private List<TheoryVersion> safeList(String id) {
        try {
            return versions.list(id);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** File-writing run: bypass permissions so Claude can write the output files. */
    private List<String> fileArgs() {
        List<String> args = new ArrayList<>(List.of("--permission-mode", "bypassPermissions"));
        if (model != null && !model.isBlank()) {
            args.add("--model");
            args.add(model);
        }
        return args;
    }

    private String buildPrompt(TopicDetail topic, String style, Path enPath, Path ruPath) {
        StringBuilder sb = new StringBuilder();
        sb.append("Rewrite ONLY the explanation prose of an existing Java interview learning topic. "
                + "Keep the SAME technical content, facts, structure, headings, Mermaid diagrams and "
                + "code blocks — change only the prose. Stay fully bilingual and technically accurate.\n\n");
        if (style != null && !style.isBlank()) {
            sb.append("STYLE: weave a short analogy from this theme into each technical point, in BOTH "
                    + "languages, to aid memorisation: ").append(style.trim()).append("\n")
                    .append("Accuracy comes first — analogies supplement, never replace, correctness; "
                            + "keep concise; do NOT restyle code, diagrams or identifiers.\n\n");
        } else {
            sb.append("Write clear default prose with no special style.\n\n");
        }
        sb.append("TOPIC: ").append(topic.title().en()).append("\n\n");
        sb.append("CURRENT ENGLISH EXPLANATION:\n").append(topic.explanation().en()).append("\n\n");
        sb.append("CURRENT RUSSIAN EXPLANATION:\n").append(topic.explanation().ru()).append("\n\n");
        sb.append("Write the rewritten ENGLISH explanation markdown to the file:\n")
                .append(enPath.toAbsolutePath()).append("\n")
                .append("Write the rewritten RUSSIAN explanation markdown to the file:\n")
                .append(ruPath.toAbsolutePath()).append("\n")
                .append("Write ONLY the markdown content into each file (no JSON, no surrounding code "
                        + "fences). Do not print the explanations to stdout.\n");
        return sb.toString();
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
