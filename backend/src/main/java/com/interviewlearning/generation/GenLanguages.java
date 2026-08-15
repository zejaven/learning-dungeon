package com.interviewlearning.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Content-language selection for AI generation. Topics default to bilingual
 * (en + ru), but the settings gear lets the user generate in one language;
 * a topic itself may also declare {@code languages:} in topic.yaml.
 */
public final class GenLanguages {

    /** All supported content languages, in canonical order. */
    public static final List<String> ALL = List.of("en", "ru");

    private GenLanguages() {
    }

    /** Sanitizes a requested list to a subset of {@link #ALL}; null/empty/invalid → both. */
    public static List<String> normalize(List<String> requested) {
        if (requested == null) {
            return ALL;
        }
        List<String> result = new ArrayList<>();
        for (String lang : ALL) {
            if (requested.stream().anyMatch(r -> r != null && lang.equals(r.trim().toLowerCase(Locale.ROOT)))) {
                result.add(lang);
            }
        }
        return result.isEmpty() ? ALL : result;
    }

    /**
     * Languages a generation for an existing topic should produce: the requested
     * languages restricted to what the topic actually has (content in a language
     * the topic lacks cannot be derived), falling back to the topic's languages
     * when the intersection is empty.
     */
    public static List<String> effective(List<String> topicLanguages, List<String> requested) {
        List<String> topic = normalize(topicLanguages);
        List<String> want = normalize(requested);
        List<String> result = topic.stream().filter(want::contains).toList();
        return result.isEmpty() ? topic : result;
    }

    /** Prompt-friendly display name: "ENGLISH" / "RUSSIAN". */
    public static String displayName(String lang) {
        return "ru".equals(lang) ? "RUSSIAN" : "ENGLISH";
    }
}
