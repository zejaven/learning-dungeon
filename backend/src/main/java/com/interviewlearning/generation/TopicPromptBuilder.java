package com.interviewlearning.generation;

import com.interviewlearning.ai.AiCliService;
import com.interviewlearning.ai.AiTask;
import com.interviewlearning.config.RepoPaths;
import com.interviewlearning.lang.ContentLanguages;
import com.interviewlearning.topics.TopicDtos.TopicSummary;
import com.interviewlearning.topics.TopicRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Builds the GENERATE_TOPIC prompt (prompts/add-topic.md + question + metadata).
 * Shared by the single "Generate theory" flow and the bulk-generation loop.
 */
@Service
public class TopicPromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(TopicPromptBuilder.class);

    private final RepoPaths repoPaths;
    private final TopicRepository topics;
    private final AiCliService ai;

    public TopicPromptBuilder(RepoPaths repoPaths, TopicRepository topics, AiCliService ai) {
        this.repoPaths = repoPaths;
        this.topics = topics;
        this.ai = ai;
    }

    /** Everything the prompt needs about one topic-to-be. */
    public record TopicGenSpec(String question, String catalogId, String categoryId,
                               Integer difficulty, String style, String styleName, String provider,
                               List<String> languages, String domainId) {
    }

    public String build(TopicGenSpec spec) {
        Path promptFile = repoPaths.promptsDir().resolve("add-topic.md");
        String contract;
        try {
            contract = Files.readString(promptFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("add-topic.md not found at {}: {}", promptFile, e.getMessage());
            contract = "Add a new Java interview learning topic following the existing "
                    + "topics/ schema (topic.yaml, explanation.md, examples/, visualizer.tsx, "
                    + "trace-schema.json, quiz.yaml). Reuse existing visual primitives and the "
                    + "existing engine; do not modify the shell or runner.";
        }

        StringBuilder sb = new StringBuilder(contract);
        sb.append("\n\n---\n\nINTERVIEW QUESTION TO TURN INTO A TOPIC:\n\n")
                .append(spec.question() == null ? "" : spec.question().trim())
                .append("\n\n---\n\nTOPIC METADATA TO SET IN topic.yaml:\n");

        String provider = spec.provider() == null || spec.provider().isBlank()
                ? "claude" : spec.provider().trim().toLowerCase();
        sb.append("- aiProvider: ").append(provider).append("\n");
        String model = ai.modelFor(provider, AiTask.GENERATE_TOPIC);
        if (model != null && !model.isBlank()) {
            sb.append("- aiModel: ").append(model.trim()).append("\n");
        }

        String domainId = spec.domainId() == null || spec.domainId().isBlank()
                ? "java" : spec.domainId().trim();
        boolean isJava = "java".equals(domainId);

        String categoryId = spec.categoryId();
        if (categoryId != null && !categoryId.isBlank()) {
            sb.append("- categoryId: ").append(categoryId.trim()).append("\n");
        } else if (isJava) {
            sb.append("- categoryId: choose the single best-fitting id from the allowed "
                    + "list in the contract above (use `other` only if nothing fits).\n");
        }
        // Other domains get their category instruction from the DOMAIN block below,
        // which lists the ids that domain actually uses.

        if (isJava && "design-patterns".equals(categoryId)) {
            sb.append("- mode: if this question is a single GoF pattern defined by class "
                    + "relationships (Strategy, Observer, Factory, Decorator, Adapter, …), set "
                    + "`mode: structural` and follow the \"Structural topics\" contract "
                    + "(starter/ files + structure missions, no model/examples/visualizer). "
                    + "For an overview question (\"what patterns exist?\") set `mode: theory` "
                    + "(explanation + Boss Fight only, see \"Theory topics\").\n");
        }
        if (isJava && "databases".equals(categoryId)) {
            sb.append("- mode: if this question is about writing a SQL query (joins, grouping, "
                    + "NULL semantics, subqueries, …), set `mode: sql` and follow the \"SQL "
                    + "topics\" contract (starter/schema.sql + starter/query.sql + sql missions "
                    + "with expectedSql). For a conceptual DB question (indexes, ACID, EXPLAIN "
                    + "plans) set `mode: theory`.\n");
        }
        if (isJava && "algorithms".equals(categoryId)) {
            sb.append("- mode: if this is a coding task where the learner implements a method "
                    + "(find/compute/return something), set `mode: challenge` and follow the "
                    + "\"Challenge topics\" contract (starter/Solution.java + harness/Main.java "
                    + "with visual.TestKit + a challenge mission). For a purely conceptual "
                    + "question (Big-O, when to use X) set `mode: theory`.\n");
        }

        Integer difficulty = spec.difficulty();
        if (difficulty != null && difficulty >= 1 && difficulty <= 3) {
            sb.append("- difficulty: ").append(difficulty).append("\n");
        } else {
            sb.append("- difficulty: decide yourself — 1 (Junior), 2 (Middle) or 3 (Senior).\n");
        }

        String catalogId = spec.catalogId();
        if (catalogId != null && !catalogId.isBlank()) {
            sb.append("- catalogId: ").append(catalogId.trim())
                    .append("   (this links the topic back to the source question — set it exactly)\n");
        }

        String styleName = spec.styleName();
        if (styleName != null && !styleName.isBlank() && !"Default".equalsIgnoreCase(styleName.trim())) {
            sb.append("- style: ").append(styleName.trim())
                    .append("   (record this in topic.yaml `style:` — the style this was generated in)\n");
        }

        appendDomain(sb, domainId);
        appendLanguages(sb, ContentLanguages.normalize(spec.languages()));
        appendStyle(sb, spec.style());
        appendCrossLinkContext(sb, domainId);
        return sb.toString();
    }

    /**
     * For a domain other than Java, overrides the Java-shaped contract: the topic
     * belongs to a different subject area, is theory-only, and picks its category
     * from the ones that domain already uses.
     */
    private void appendDomain(StringBuilder sb, String domainId) {
        if ("java".equals(domainId)) {
            return; // the contract above is written for the Java domain
        }
        sb.append("\n\n---\n\nDOMAIN: ").append(domainId)
                .append(" — this topic belongs to the \"").append(domainId)
                .append("\" subject area, NOT to Java interview prep. This overrides the "
                        + "Java-specific instructions in the contract above.\n")
                .append("- In topic.yaml set `domainId: ").append(domainId).append("`.\n")
                .append("- The topic id MUST start with `").append(domainId)
                .append("-` and MUST equal the folder name.\n")
                .append("- Set `mode: theory` and follow the \"Theory topics\" contract: ONLY "
                        + "topic.yaml, the explanation file(s), and quiz.yaml with a non-empty "
                        + "`bossFight`. Do NOT create examples/, visualizer.tsx, "
                        + "trace-schema.json, starter/, harness/, missions, or any Java code.\n")
                .append("- Ignore the Java category list, the Java mode rules and the "
                        + "visual-runtime/trace instructions entirely; use this domain's own "
                        + "technical terms instead of Java ones.\n");
        List<TopicSummary> siblings = safeList().stream()
                .filter(t -> domainId.equals(t.domainId()))
                .toList();
        if (siblings.isEmpty()) {
            sb.append("- categoryId: invent a kebab-case id prefixed `").append(domainId)
                    .append("-` and set `categoryName` to its human-readable name.\n");
            return;
        }
        sb.append("- categoryId: pick the best fit from this domain's existing categories "
                + "below, or invent a kebab-case id prefixed `").append(domainId)
                .append("-` and set `categoryName` to its human-readable name.\n");
        siblings.stream()
                .filter(t -> t.categoryId() != null && !t.categoryId().isBlank())
                .collect(java.util.stream.Collectors.toMap(
                        TopicSummary::categoryId,
                        t -> t.categoryName() == null || t.categoryName().isBlank()
                                ? t.categoryId() : t.categoryName(),
                        (a, b) -> a,
                        java.util.LinkedHashMap::new))
                .forEach((id, name) -> sb.append("  - ").append(id).append(" — ").append(name).append("\n"));
    }

    /**
     * States exactly which languages to write. Always emitted: with more than two
     * possible languages, the contract's "both languages" wording is meaningless,
     * so this block is the authority on coverage.
     */
    private void appendLanguages(StringBuilder sb, List<String> languages) {
        String names = languages.stream().map(ContentLanguages::displayName)
                .collect(java.util.stream.Collectors.joining(", "));
        sb.append("\n\n---\n\nLANGUAGES: ").append(names)
                .append(" — exactly these, no others. This overrides every bilingual or "
                        + "two-language statement in the contract above.\n")
                .append("- In topic.yaml set `languages: [")
                .append(String.join(", ", languages)).append("]`.\n");
        if (languages.size() == 1) {
            String lang = languages.get(0);
            sb.append("- Write every translatable field (title, category, summary, "
                            + "assistantExample, example titles/explanations, mission title/goal) "
                            + "as a plain ").append(ContentLanguages.displayName(lang))
                    .append(" string, NOT a language map.\n");
        } else {
            sb.append("- Every translatable field is a map with exactly these keys: { ")
                    .append(String.join(", ", languages)).append(" }.\n");
        }
        sb.append("- Write exactly these explanation files and no others: ")
                .append(languages.stream().map(l -> "explanation." + l + ".md")
                        .collect(java.util.stream.Collectors.joining(", ")))
                .append("\n")
                .append("- In quiz.yaml every bossFight entry keeps its stable `id` and carries "
                        + "exactly these language keys: ")
                .append(String.join(", ", languages)).append(".\n")
                .append("- Code, identifiers, and technical tokens stay in English as usual.\n");
    }

    /**
     * Adds an optional "explanation style" — weave analogies from a chosen theme
     * into the explanation prose to aid memorisation, without touching the
     * technical content, code, diagrams or missions.
     */
    private void appendStyle(StringBuilder sb, String style) {
        if (style == null || style.isBlank()) {
            return;
        }
        sb.append("\n\n---\n\nEXPLANATION STYLE:\n")
                .append("Apply this style ONLY to the prose in explanation.en.md / explanation.ru.md: ")
                .append(style.trim())
                .append("\nFor each technical point, process or interaction, add a short analogy in "
                        + "that theme — in BOTH languages — to help the reader remember it. Accuracy "
                        + "comes first: the analogy supplements, never replaces, precise technical "
                        + "content, and keep it concise. Do NOT style code, Mermaid diagrams, "
                        + "identifiers, the 60-second answer's correctness, missions or boss-fight "
                        + "questions.\n");
    }

    /**
     * Lists the topics that already exist so the selected AI can cross-link to them
     * from the new explanation via `[label](topic:&lt;id&gt;)` (see topic-contract.md). Only
     * real, existing ids are offered, so links never dangle.
     */
    private void appendCrossLinkContext(StringBuilder sb, String domainId) {
        // Same domain only: a QA topic linking to topic:hashmap would be noise,
        // and it keeps the prompt short for domains with few topics.
        List<TopicSummary> existing = safeList().stream()
                .filter(t -> domainId.equals(t.domainId()))
                .toList();
        if (existing.isEmpty()) {
            return;
        }
        sb.append("\n\n---\n\nEXISTING TOPICS YOU MAY CROSS-LINK TO. When the explanation "
                + "mentions one of these concepts, link to it with `[label](topic:<id>)` "
                + "so the reader can jump to that topic. Use only these exact ids; never "
                + "invent one. Do NOT link the new topic to itself.\n");
        for (TopicSummary t : existing) {
            String title = t.title() == null
                    ? t.id()
                    : t.title().label(ContentLanguages.defaultLanguage());
            sb.append("- topic:").append(t.id()).append(" — ").append(title).append("\n");
        }
    }

    /** All topics, or none when the folder cannot be read. */
    private List<TopicSummary> safeList() {
        try {
            return topics.listTopics();
        } catch (RuntimeException e) {
            log.warn("Could not list topics: {}", e.getMessage());
            return List.of();
        }
    }
}
