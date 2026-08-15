package com.interviewlearning.api;

import com.interviewlearning.ai.AiCliService;
import com.interviewlearning.ai.AiTask;
import com.interviewlearning.config.RepoPaths;
import com.interviewlearning.generation.GenLanguages;
import com.interviewlearning.theory.TheoryVersionRepository;
import com.interviewlearning.theory.TheoryVersionRepository.TheoryVersion;
import com.interviewlearning.topics.TopicDtos.TopicDetail;
import com.interviewlearning.topics.TopicRepository;
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
 * <p>The selected AI writes rewritten explanations to files (not stdout);
 * files are always correct UTF-8, whereas Windows can mangle Cyrillic on stdout.
 */
@RestController
@RequestMapping("/api/topics/{id}/versions")
public class VersionController {

    private final TopicRepository topics;
    private final TheoryVersionRepository versions;
    private final AiCliService ai;
    private final RepoPaths repoPaths;

    public VersionController(TopicRepository topics,
                             TheoryVersionRepository versions,
                             AiCliService ai,
                             RepoPaths repoPaths) {
        this.topics = topics;
        this.versions = versions;
        this.ai = ai;
        this.repoPaths = repoPaths;
    }

    public record VersionDto(int versionNo, String style, String en, String ru, String createdAt,
                             String aiProvider, String aiModel) {
    }

    /** {@code languages}: content languages to regenerate ("en"/"ru"); null/empty = both;
     * always narrowed to the languages the topic itself declares. */
    public record RegenerateRequest(String style, String styleName, String provider,
                                    List<String> languages) {
    }

    @GetMapping
    public ResponseEntity<List<VersionDto>> list(@PathVariable String id) {
        Optional<TopicDetail> opt = topics.getTopic(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        TopicDetail topic = opt.get();
        List<VersionDto> result = new ArrayList<>();
        result.add(new VersionDto(1, topic.style(), topic.explanation().en(), topic.explanation().ru(), null,
                topic.aiProvider(), topic.aiModel()));
        for (TheoryVersion v : safeList(id)) {
            result.add(new VersionDto(v.versionNo(), v.style(), v.en(), v.ru(), v.createdAt(),
                    v.aiProvider(), v.aiModel()));
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
            dir = Files.createTempDirectory("regen-");
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Could not create temp dir: " + e.getMessage());
        }
        List<String> languages = GenLanguages.effective(topic.languages(), request.languages());
        Path enPath = dir.resolve("en.md");
        Path ruPath = dir.resolve("ru.md");
        try {
            ai.runForResult(request.provider(),
                    buildPrompt(topic, request.style(), enPath, ruPath, languages),
                    AiTask.REGENERATE_VERSION);
            String en = languages.contains("en")
                    ? Files.readString(enPath, StandardCharsets.UTF_8).trim() : "";
            String ru = languages.contains("ru")
                    ? Files.readString(ruPath, StandardCharsets.UTF_8).trim() : "";
            if ((languages.contains("en") && en.isEmpty()) || (languages.contains("ru") && ru.isEmpty())) {
                return ResponseEntity.internalServerError().body("The regenerated explanation was empty.");
            }
            // Mirror a single-language version into both DB columns so consumers
            // (VersionDto, atoms generation from a version) always see text.
            if (en.isEmpty()) en = ru;
            if (ru.isEmpty()) ru = en;
            String styleName = request.styleName() == null || request.styleName().isBlank()
                    ? "Default" : request.styleName().trim();
            String provider = request.provider() == null || request.provider().isBlank()
                    ? "claude" : request.provider().trim().toLowerCase();
            String model = ai.modelFor(provider, AiTask.REGENERATE_VERSION);
            int versionNo = versions.add(id, styleName, en, ru, provider, model);
            return ResponseEntity.ok(new VersionDto(versionNo, styleName, en, ru, null, provider, model));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body("AI provider did not write the explanation files: " + e.getMessage());
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

    private String buildPrompt(TopicDetail topic, String style, Path enPath, Path ruPath,
                               List<String> languages) {
        boolean both = languages.size() > 1;
        StringBuilder sb = new StringBuilder();
        sb.append("Rewrite ONLY the explanation prose of an existing Java interview learning topic. "
                + "Keep the SAME technical content, facts, structure, headings, Mermaid diagrams and "
                + "code blocks — change only the prose. Stay technically accurate")
                .append(both ? " and fully bilingual" : "").append(".\n\n");
        if (style != null && !style.isBlank()) {
            sb.append("STYLE: weave a short analogy from this theme into each technical point")
                    .append(both ? ", in BOTH languages," : "")
                    .append(" to aid memorisation: ").append(style.trim()).append("\n")
                    .append("Accuracy comes first — analogies supplement, never replace, correctness; "
                            + "keep concise; do NOT restyle code, diagrams or identifiers.\n\n");
        } else {
            sb.append("Write clear default prose with no special style.\n\n");
        }
        sb.append("TOPIC: ").append(topic.title().en()).append("\n\n");
        for (String lang : languages) {
            String name = GenLanguages.displayName(lang);
            String text = "ru".equals(lang) ? topic.explanation().ru() : topic.explanation().en();
            sb.append("CURRENT ").append(name).append(" EXPLANATION:\n").append(text).append("\n\n");
        }
        for (String lang : languages) {
            Path path = "ru".equals(lang) ? ruPath : enPath;
            sb.append("Write the rewritten ").append(GenLanguages.displayName(lang))
                    .append(" explanation markdown to the file:\n")
                    .append(path.toAbsolutePath()).append("\n");
        }
        sb.append("Write ONLY the markdown content into each file (no JSON, no surrounding code "
                + "fences). Do not print the explanations to stdout.");
        if (!both) {
            sb.append(" Write ONLY that one language; do not create the other language's file.");
        }
        sb.append("\n");
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
