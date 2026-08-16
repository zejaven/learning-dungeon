package com.interviewlearning.api;

import com.interviewlearning.ai.AiCliService;
import com.interviewlearning.ai.AiTask;
import com.interviewlearning.config.RepoPaths;
import com.interviewlearning.lang.ContentLanguages;
import com.interviewlearning.theory.TheoryVersionRepository;
import com.interviewlearning.theory.TheoryVersionRepository.TheoryVersion;
import com.interviewlearning.topics.TopicDtos.Localized;
import com.interviewlearning.topics.TopicDtos.TopicDetail;
import com.interviewlearning.topics.TopicRepository;
import com.interviewlearning.topics.TopicYamlEditor;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final TopicYamlEditor yamlEditor;

    public VersionController(TopicRepository topics,
                             TheoryVersionRepository versions,
                             AiCliService ai,
                             RepoPaths repoPaths,
                             TopicYamlEditor yamlEditor) {
        this.topics = topics;
        this.versions = versions;
        this.ai = ai;
        this.repoPaths = repoPaths;
        this.yamlEditor = yamlEditor;
    }

    /** {@code texts} carries one entry per language this version was written in. */
    public record VersionDto(int versionNo, String style, Localized texts, String createdAt,
                             String aiProvider, String aiModel) {
    }

    /** {@code languages}: content languages to write; null/empty = every registered one. */
    public record RegenerateRequest(String style, String styleName, String provider,
                                    List<String> languages) {
    }

    /** Adds one missing language to an existing version. */
    public record AddLanguageRequest(String lang, String provider) {
    }

    @GetMapping
    public ResponseEntity<List<VersionDto>> list(@PathVariable String id) {
        Optional<TopicDetail> opt = topics.getTopic(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        TopicDetail topic = opt.get();
        List<VersionDto> result = new ArrayList<>();
        result.add(new VersionDto(1, topic.style(), topic.explanation(), null,
                topic.aiProvider(), topic.aiModel()));
        for (TheoryVersion v : safeList(id)) {
            result.add(new VersionDto(v.versionNo(), v.style(), v.texts(), v.createdAt(),
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
        // Not effective(...): a topic missing a language may be asked for it here,
        // which is how a translation gets created in the first place.
        List<String> languages = ContentLanguages.normalize(request.languages());
        Map<String, Path> outputs = new LinkedHashMap<>();
        for (String lang : languages) {
            outputs.put(lang, dir.resolve(lang + ".md"));
        }
        try {
            ai.runForResult(request.provider(),
                    buildPrompt(topic, request.style(), outputs, languages),
                    AiTask.REGENERATE_VERSION);
            Map<String, String> texts = new LinkedHashMap<>();
            for (String lang : languages) {
                String text = readIfPresent(outputs.get(lang));
                if (!text.isBlank()) {
                    texts.put(lang, text.trim());
                }
            }
            if (texts.isEmpty()) {
                return ResponseEntity.internalServerError()
                        .body("The AI wrote no explanation file, or every one it wrote was empty.");
            }
            String styleName = request.styleName() == null || request.styleName().isBlank()
                    ? "Default" : request.styleName().trim();
            String provider = request.provider() == null || request.provider().isBlank()
                    ? "claude" : request.provider().trim().toLowerCase();
            String model = ai.modelFor(provider, AiTask.REGENERATE_VERSION);
            int versionNo = versions.add(id, styleName, texts, provider, model);
            return ResponseEntity.ok(new VersionDto(versionNo, styleName, new Localized(texts),
                    null, provider, model));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        } finally {
            deleteDir(dir);
        }
    }

    /**
     * Translates an existing version into one more language. Versions 2+ gain a
     * row; version 1 is the on-disk explanation, so it gains an
     * {@code explanation.<lang>.md} file and the code in topic.yaml
     * {@code languages:}.
     */
    @PostMapping("/{versionNo}/languages")
    public ResponseEntity<?> addLanguage(@PathVariable String id,
                                         @PathVariable int versionNo,
                                         @RequestBody AddLanguageRequest request) {
        Optional<TopicDetail> opt = topics.getTopic(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        TopicDetail topic = opt.get();
        String lang = ContentLanguages.normalizeCode(request.lang());
        if (lang == null) {
            return ResponseEntity.badRequest().body("Unsupported language: " + request.lang());
        }

        Localized existing = versionNo <= 1
                ? topic.explanation()
                : safeList(id).stream()
                        .filter(v -> v.versionNo() == versionNo)
                        .findFirst()
                        .map(TheoryVersion::texts)
                        .orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (existing.has(lang)) {
            return ResponseEntity.badRequest()
                    .body("Version " + versionNo + " already has " + ContentLanguages.englishName(lang));
        }
        String source = existing.languages().stream().findFirst().orElse(null);
        if (source == null) {
            return ResponseEntity.badRequest().body("Version " + versionNo + " has no text to translate.");
        }

        Path dir;
        try {
            dir = Files.createTempDirectory("translate-");
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Could not create temp dir: " + e.getMessage());
        }
        Path output = dir.resolve(lang + ".md");
        try {
            ai.runForResult(request.provider(),
                    buildTranslatePrompt(topic, existing.get(source), source, lang, output),
                    AiTask.REGENERATE_VERSION);
            String text = readIfPresent(output).trim();
            if (text.isEmpty()) {
                return ResponseEntity.internalServerError()
                        .body("The AI wrote no " + ContentLanguages.englishName(lang) + " explanation.");
            }
            if (versionNo <= 1) {
                writeExplanationFile(id, lang, text);
            } else {
                versions.addTexts(id, versionNo, Map.of(lang, text));
            }
            return ResponseEntity.ok(Map.of("versionNo", String.valueOf(versionNo), "lang", lang));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body("Could not write the explanation: " + e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        } finally {
            deleteDir(dir);
        }
    }

    /** Writes topics/&lt;id&gt;/explanation.&lt;lang&gt;.md and declares the language. */
    private void writeExplanationFile(String topicId, String lang, String text) throws IOException {
        Path topicDir = repoPaths.topicsDir().resolve(topicId);
        Path file = topicDir.resolve("explanation." + lang + ".md");
        // Match the line endings of a sibling explanation so the folder stays consistent.
        String eol = "\n";
        for (String other : ContentLanguages.ALL) {
            Path sibling = topicDir.resolve("explanation." + other + ".md");
            if (Files.exists(sibling) && Files.readString(sibling, StandardCharsets.UTF_8).contains("\r\n")) {
                eol = "\r\n";
                break;
            }
        }
        Files.writeString(file, text.replace("\r\n", "\n").replace("\n", eol), StandardCharsets.UTF_8);
        yamlEditor.addLanguage(topicDir.resolve("topic.yaml"), lang);
    }

    private String buildTranslatePrompt(TopicDetail topic, String source, String sourceLang,
                                        String targetLang, Path output) {
        return "Translate the explanation of a learning topic into "
                + ContentLanguages.englishName(targetLang) + ".\n"
                + "Keep the SAME technical content, structure, headings, Mermaid diagrams and code "
                + "blocks; translate prose and diagram labels, but leave code, identifiers and "
                + "technical tokens untranslated.\n\n"
                + "TOPIC: " + topic.title().label(sourceLang) + "\n\n"
                + "SOURCE EXPLANATION (" + ContentLanguages.displayName(sourceLang) + "):\n"
                + source + "\n\n"
                + "Write ONLY the translated markdown to this file (no JSON, no code fences, "
                + "nothing on stdout):\n" + output.toAbsolutePath() + "\n";
    }

    private List<TheoryVersion> safeList(String id) {
        try {
            return versions.list(id);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** Reads a file the AI was asked to write, tolerating it never appearing. */
    private static String readIfPresent(Path path) {
        try {
            return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }

    private String buildPrompt(TopicDetail topic, String style, Map<String, Path> outputs,
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
        sb.append("TOPIC: ").append(topic.title().label(ContentLanguages.defaultLanguage()))
                .append("\n\n");
        for (String lang : topic.explanation().languages()) {
            sb.append("CURRENT ").append(ContentLanguages.displayName(lang))
                    .append(" EXPLANATION:\n").append(topic.explanation().get(lang)).append("\n\n");
        }
        for (String lang : languages) {
            sb.append("Write the rewritten ").append(ContentLanguages.displayName(lang))
                    .append(" explanation markdown to the file:\n")
                    .append(outputs.get(lang).toAbsolutePath()).append("\n");
            if (!topic.explanation().has(lang)) {
                sb.append("There is no current ").append(ContentLanguages.displayName(lang))
                        .append(" explanation: write it from the one above, keeping the same "
                                + "structure, headings, Mermaid diagrams and code blocks, and "
                                + "translating diagram labels but not identifiers or code.\n");
            }
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
