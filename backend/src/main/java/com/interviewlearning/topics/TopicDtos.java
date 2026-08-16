package com.interviewlearning.topics;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.interviewlearning.lang.ContentLanguages;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Data transfer objects for topics served to the frontend. */
public final class TopicDtos {

    private TopicDtos() {
    }

    /**
     * A piece of text in the languages it exists in, keyed by language code. It
     * serializes as a bare {@code {"en": ..., "ru": ...}} object, which is the
     * shape topic.yaml, quiz.yaml, learning-atoms.json and the frontend already
     * use — a value simply carries fewer or more keys now.
     *
     * <p>Read it with {@link #get} for body content, so a missing language is
     * reported rather than silently replaced, and with {@link #label} for titles
     * and other short strings that must always render something.
     */
    public record Localized(Map<String, String> byLang) {

        public Localized {
            byLang = byLang == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(byLang));
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Localized fromJson(Map<String, String> map) {
            return new Localized(map);
        }

        @JsonValue
        public Map<String, String> json() {
            return byLang;
        }

        /** The same text in every registered language (a scalar YAML field). */
        public static Localized of(String value) {
            Map<String, String> map = new LinkedHashMap<>();
            for (String lang : ContentLanguages.ALL) {
                map.put(lang, value);
            }
            return new Localized(map);
        }

        public static Localized ofSingle(String lang, String value) {
            return new Localized(Map.of(lang, value));
        }

        public static Localized empty() {
            return new Localized(Map.of());
        }

        /** STRICT: the text in {@code lang}, or null when it is absent or blank. */
        public String get(String lang) {
            String text = lang == null ? null : byLang.get(lang);
            return text == null || text.isBlank() ? null : text;
        }

        public boolean has(String lang) {
            return get(lang) != null;
        }

        /** LENIENT: {@code lang} if present, else any language this value carries. */
        public String label(String lang) {
            String text = get(lang);
            if (text != null) {
                return text;
            }
            for (String code : languages()) {
                return byLang.get(code);
            }
            return "";
        }

        /** Languages actually carried, registered ones first, then any extras. */
        public List<String> languages() {
            List<String> result = new java.util.ArrayList<>();
            for (String code : ContentLanguages.ALL) {
                if (get(code) != null) {
                    result.add(code);
                }
            }
            for (String code : byLang.keySet()) {
                if (!result.contains(code) && get(code) != null) {
                    result.add(code);
                }
            }
            return result;
        }

        /** A copy with one language added or replaced. */
        public Localized with(String lang, String text) {
            Map<String, String> map = new LinkedHashMap<>(byLang);
            map.put(lang, text);
            return new Localized(map);
        }
    }

    /** Lightweight entry for the topic switcher. */
    public record TopicSummary(
            String id,
            Localized title,
            Localized category,
            String type,
            Localized summary,
            boolean completed,
            String categoryId,
            String categoryName,
            int difficulty,
            String catalogId,
            String mode,
            String aiProvider,
            String aiModel,
            boolean hasAtoms,
            String domainId,
            /** Position within the catalog (e.g. lecture number); 0 = unordered. */
            int order
    ) {
    }

    public record Example(
            String id,
            Localized title,
            String code,
            Localized explanation
    ) {
    }

    /**
     * A mission/challenge. For a trace topic ({@code type == "event"}, the
     * default) {@code event} is the trace event type whose presence in a run
     * satisfies it. For a structural topic ({@code type == "structure"})
     * {@code requires} holds predicates evaluated against the analyzed class
     * graph (checked on the frontend). Predicates are passed through as raw
     * maps so the rule schema can evolve without backend changes.
     */
    public record Mission(
            String id,
            Localized title,
            Localized goal,
            String event,
            String type,
            List<Object> requires
    ) {
    }

    /** One file of a structural topic's starter project (path relative to the topic root). */
    public record ProjectFile(
            String path,
            String content
    ) {
    }

    /**
     * One boss-fight interview question. {@code id} is stable across edits so a
     * saved answer stays attached to the right question even if questions are
     * reordered or inserted.
     */
    public record BossQuestion(
            String id,
            Localized text
    ) {
    }

    /**
     * Full payload for a single topic. {@code mode} is {@code "trace"} (the
     * default behavioural engine: examples + visualizer + trace events) or
     * {@code "structural"} (a multi-file project analyzed into a class graph;
     * {@code starterFiles} seeds the editor, {@code examples}/visualizer unused).
     */
    public record TopicDetail(
            String id,
            Localized title,
            Localized category,
            String type,
            Localized summary,
            List<String> primitives,
            Localized explanation,
            List<Example> examples,
            String defaultExampleId,
            List<Mission> missions,
            Localized assistantExample,
            List<BossQuestion> bossFight,
            String mode,
            List<ProjectFile> starterFiles,
            String style,
            String aiProvider,
            String aiModel,
            boolean hasAtoms,
            String domainId,
            /** Content languages the topic declares (subset of [en, ru]); default both. */
            List<String> languages
    ) {
    }
}
