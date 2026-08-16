package com.interviewlearning.topics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewlearning.lesson.LessonDtos.LocalizedList;
import com.interviewlearning.topics.TopicDtos.Localized;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wire and file format of a translatable value is a bare {@code {lang: text}}
 * object — every topic.yaml, quiz.yaml, learning-atoms.json and API response
 * depends on it, so the map-backed record must serialize as the map itself and
 * never as a wrapper object.
 */
class LocalizedJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesAsABareLanguageMap() throws Exception {
        Localized value = Localized.fromJson(Map.of("en", "Hello"));
        assertEquals("{\"en\":\"Hello\"}", mapper.writeValueAsString(value));
    }

    @Test
    void roundTripsEveryLanguage() throws Exception {
        String json = "{\"en\":\"Hello\",\"ru\":\"Привет\"}";
        Localized parsed = mapper.readValue(json, Localized.class);
        assertEquals("Hello", parsed.get("en"));
        assertEquals("Привет", parsed.get("ru"));
        assertEquals(json, mapper.writeValueAsString(parsed));
    }

    @Test
    void keepsAnUnregisteredLanguageInsteadOfDroppingIt() throws Exception {
        Localized parsed = mapper.readValue("{\"en\":\"Hello\",\"de\":\"Hallo\"}", Localized.class);
        assertEquals("Hallo", parsed.get("de"));
    }

    @Test
    void getIsStrictAndLabelFallsBack() {
        Localized ruOnly = Localized.fromJson(Map.of("ru", "Привет"));
        assertNull(ruOnly.get("en"), "a missing language must be reported, not substituted");
        assertFalse(ruOnly.has("en"));
        assertEquals("Привет", ruOnly.label("en"), "labels fall back to a language that exists");
        assertEquals(List.of("ru"), ruOnly.languages());
    }

    @Test
    void blankTextCountsAsMissing() {
        Localized blank = Localized.fromJson(Map.of("en", "   "));
        assertNull(blank.get("en"));
        assertFalse(blank.has("en"));
        assertTrue(blank.languages().isEmpty());
    }

    @Test
    void ofFillsEveryRegisteredLanguage() {
        Localized same = Localized.of("42");
        assertEquals("42", same.get("en"));
        assertEquals("42", same.get("ru"));
    }

    @Test
    void withAddsALanguageWithoutTouchingTheOriginal() {
        Localized ruOnly = Localized.fromJson(Map.of("ru", "Привет"));
        Localized both = ruOnly.with("en", "Hello");
        assertEquals("Hello", both.get("en"));
        assertNull(ruOnly.get("en"), "the original value must stay immutable");
    }

    @Test
    void localizedListSerializesAsABareLanguageMap() throws Exception {
        String json = "{\"en\":[\"a\",\"b\"],\"ru\":[\"а\",\"б\"]}";
        LocalizedList parsed = mapper.readValue(json, LocalizedList.class);
        assertEquals(List.of("a", "b"), parsed.get("en"));
        assertEquals(json, mapper.writeValueAsString(parsed));
        assertTrue(parsed.get("de").isEmpty(), "a missing language reads as an empty list");
    }
}
