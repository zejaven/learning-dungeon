package com.interviewlearning.lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The content languages the app knows about. This is the single place to change
 * when a language is added; loaders, prompt builders, contract tests and the
 * theory-version store all derive their behaviour from this registry.
 *
 * <p>Lives in its own package because both {@code topics} and {@code generation}
 * depend on it, and {@code generation} already depends on {@code topics}.
 */
public final class ContentLanguages {

    /**
     * @param code        the key used in YAML/JSON, file names and DB rows
     * @param nativeName  how speakers of the language call it (UI display)
     * @param englishName how the language is named inside an English AI prompt
     */
    public record ContentLanguage(String code, String nativeName, String englishName) {
    }

    // Adding a language is this one line (plus its UI strings, if it should also
    // become an interface language on the frontend).
    private static final List<ContentLanguage> REGISTRY = List.of(
            new ContentLanguage("en", "English", "English"),
            new ContentLanguage("ru", "Русский", "Russian"));

    /** Every registered code, in canonical order. */
    public static final List<String> ALL = REGISTRY.stream().map(ContentLanguage::code).toList();

    /**
     * What a topic without an explicit {@code languages:} carries. Deliberately
     * NOT {@link #ALL}: every legacy topic is bilingual, and registering a new
     * language must not make 272 topics suddenly claim content they lack.
     */
    public static final List<String> LEGACY_DEFAULT = List.of("en", "ru");

    private ContentLanguages() {
    }

    public static List<ContentLanguage> all() {
        return REGISTRY;
    }

    public static boolean isSupported(String code) {
        return code != null && ALL.contains(code.trim().toLowerCase(Locale.ROOT));
    }

    /** Trims and lowercases a code, returning null when it is not registered. */
    public static String normalizeCode(String raw) {
        if (raw == null) {
            return null;
        }
        String code = raw.trim().toLowerCase(Locale.ROOT);
        return ALL.contains(code) ? code : null;
    }

    /** The anchor language for label fallback: the first registered one. */
    public static String defaultLanguage() {
        return ALL.get(0);
    }

    public static String nativeName(String code) {
        return find(code).map(ContentLanguage::nativeName).orElse(code);
    }

    /** Language name for prompt prose, e.g. {@code Russian}. */
    public static String englishName(String code) {
        return find(code).map(ContentLanguage::englishName).orElse(code);
    }

    /** Upper-case language name used as a prompt section heading. */
    public static String displayName(String code) {
        return englishName(code).toUpperCase(Locale.ROOT);
    }

    /** Sanitizes a requested list to registered codes in canonical order; empty → all. */
    public static List<String> normalize(List<String> requested) {
        if (requested == null) {
            return ALL;
        }
        List<String> result = new ArrayList<>();
        for (String code : ALL) {
            if (requested.stream().anyMatch(r -> code.equals(normalizeCode(r)))) {
                result.add(code);
            }
        }
        return result.isEmpty() ? ALL : result;
    }

    /**
     * Languages a generation for an existing topic should produce: the requested
     * languages restricted to what the topic actually has, since content in a
     * language the topic lacks cannot be derived. Falls back to the topic's own
     * languages when the intersection is empty.
     */
    public static List<String> effective(List<String> topicLanguages, List<String> requested) {
        List<String> topic = normalize(topicLanguages);
        List<String> want = normalize(requested);
        List<String> result = topic.stream().filter(want::contains).toList();
        return result.isEmpty() ? topic : result;
    }

    private static java.util.Optional<ContentLanguage> find(String code) {
        String normalized = normalizeCode(code);
        return REGISTRY.stream().filter(l -> l.code().equals(normalized)).findFirst();
    }
}
