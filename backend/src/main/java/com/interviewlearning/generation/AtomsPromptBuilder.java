package com.interviewlearning.generation;

import com.interviewlearning.ai.AiCliService;
import com.interviewlearning.ai.AiTask;
import com.interviewlearning.config.RepoPaths;
import com.interviewlearning.lang.ContentLanguages;
import com.interviewlearning.theory.TheoryVersionRepository;
import com.interviewlearning.topics.TopicDtos.BossQuestion;
import com.interviewlearning.topics.TopicDtos.Localized;
import com.interviewlearning.topics.TopicDtos.TopicDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Builds the GENERATE_ATOMS prompt (prompts/generate-learning-atoms.md + theory
 * source + output path). Shared by the single "Generate lesson" flow and the
 * bulk-generation loop.
 */
@Service
public class AtomsPromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(AtomsPromptBuilder.class);

    private final RepoPaths repoPaths;
    private final TheoryVersionRepository versions;
    private final AiCliService ai;

    public AtomsPromptBuilder(RepoPaths repoPaths, TheoryVersionRepository versions, AiCliService ai) {
        this.repoPaths = repoPaths;
        this.versions = versions;
        this.ai = ai;
    }

    /** A full (non-augment) lesson generation from the given theory version. */
    public String buildFull(TopicDetail topic, String provider, int versionNo, List<String> languages) {
        return build(topic, provider, versionNo, explanationOf(topic, versionNo), false, "", null, languages);
    }

    /** The current learning-atoms.json contents, or null if there is no lesson to augment. */
    public String readExistingAtoms(String topicId) {
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
    public Localized explanationOf(TopicDetail topic, int versionNo) {
        if (versionNo > 1) {
            return versions.list(topic.id()).stream()
                    .filter(v -> v.versionNo() == versionNo)
                    .findFirst()
                    .map(TheoryVersionRepository.TheoryVersion::texts)
                    .orElse(topic.explanation());
        }
        return topic.explanation();
    }

    public String build(TopicDetail topic, String provider, int versionNo, Localized explanation,
                        boolean augment, String comment, String existingAtoms,
                        List<String> requestedLanguages) {
        // A lesson can only exist in languages the topic itself has content in.
        List<String> languages = ContentLanguages.effective(topic.languages(), requestedLanguages);
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
        appendLanguages(sb, languages);
        sb.append("\n\n---\n\nTOPIC:\n")
                .append("- id: ").append(topic.id()).append("\n");
        for (String lang : languages) {
            sb.append("- title (").append(lang).append("): ")
                    .append(topic.title().label(lang)).append("\n");
        }

        sb.append("\nVALUES TO SET IN THE JSON:\n")
                .append("- topicId: ").append(topic.id()).append("\n")
                .append("- sourceVersion: ").append(versionNo).append("\n")
                .append("- aiProvider: ").append(provider).append("\n")
                .append("- aiModel: ").append(model == null ? "" : model.trim()).append("\n");

        List<BossQuestion> boss = topic.bossFight();
        if (boss != null && !boss.isEmpty()) {
            sb.append("\nBOSS-FIGHT QUESTIONS (context only — prepare for them, do not restate or include):\n");
            for (BossQuestion q : boss) {
                sb.append("- ").append(q.text().label(languages.get(0))).append("\n");
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

        for (String lang : languages) {
            String source = explanation.get(lang);
            if (source == null) {
                continue; // no text to derive this language's exercises from
            }
            sb.append("\n---\n\nSOURCE EXPLANATION (").append(ContentLanguages.displayName(lang))
                    .append("):\n\n").append(source).append("\n");
        }
        sb.append("\n---\n\nOUTPUT FILE (write the JSON exactly here):\n")
                .append(output.toAbsolutePath());
        return sb.toString();
    }

    /**
     * States exactly which languages the lesson must carry. Always emitted: the
     * contract's "both languages" wording says nothing once more than two
     * languages are possible.
     */
    private void appendLanguages(StringBuilder sb, List<String> languages) {
        String keys = String.join(", ", languages);
        sb.append("\n\n---\n\nLANGUAGES: ")
                .append(languages.stream().map(ContentLanguages::displayName)
                        .collect(java.util.stream.Collectors.joining(", ")))
                .append(" — exactly these, no others. This overrides every bilingual or "
                        + "two-language statement in the contract above.\n")
                .append("- Every localized field in the JSON (text objects and string-list "
                        + "objects alike) must contain exactly these keys: ").append(keys)
                .append(" — omit every other language key.\n")
                .append("- Code, identifiers, and technical tokens stay in English as usual.\n");
    }
}
