package com.interviewlearning.lesson.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.interviewlearning.config.RepoPaths;
import com.interviewlearning.runner.JavaCodeRunner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SnippetCheckTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final JavaCodeRunner runner = new JavaCodeRunner(new RepoPaths(""), MAPPER, 10, "128m");

    /** A predict_output exercise; {@code options} are id/text pairs, {@code correct} the marked id. */
    public static JsonNode exercise(String id, String code, String correct, String... options) {
        ObjectNode ex = MAPPER.createObjectNode();
        ex.put("id", id);
        ex.put("type", "predict_output");
        ex.putObject("prompt").put("en", "What does it print?").put("ru", "Что напечатает?");
        ex.put("code", code);
        ex.put("codeLang", "java");
        ArrayNode opts = ex.putArray("options");
        for (int i = 0; i < options.length; i += 2) {
            ObjectNode o = opts.addObject();
            o.put("id", options[i]);
            o.putObject("text").put("en", options[i + 1]).put("ru", options[i + 1]);
            o.put("correct", options[i].equals(correct));
        }
        return ex;
    }

    private static final String MAP_CODE = """
            Map<String, Integer> m = new HashMap<>();
            m.put("a", 1);
            m.put("a", 2);
            System.out.println(m.size() + " " + m.get("a"));""";

    @Test
    void theMarkedOptionPrintedIsConfirmed() {
        SnippetCheck.Verdict v = SnippetCheck.check(runner,
                exercise("x", MAP_CODE, "b", "a", "2 1", "b", "1 2", "c", "2 2"));
        assertEquals(SnippetCheck.RUNNABLE, v.group());
        assertEquals(SnippetCheck.CONFIRMED, v.verdict());
        assertEquals("1 2", v.output());
    }

    /** The calibration: the same exercise with the key moved must be caught. */
    @Test
    void anotherOptionPrintedContradicts() {
        SnippetCheck.Verdict v = SnippetCheck.check(runner,
                exercise("x", MAP_CODE, "a", "a", "2 1", "b", "1 2", "c", "2 2"));
        assertTrue(v.contradicts());
        assertEquals(List.of("b"), v.literalMatches());
        assertEquals("a", v.correctId());
    }

    @Test
    void anExceptionMatchesByItsClassName() {
        String code = """
                List<String> list = new ArrayList<>(List.of("a", "b", "c"));
                for (String s : list) {
                    if (s.equals("a")) list.remove(s);
                }""";
        SnippetCheck.Verdict v = SnippetCheck.check(runner, exercise("x", code, "b",
                "a", "[b, c]", "b", "It throws ConcurrentModificationException"));
        assertEquals("EXCEPTION ConcurrentModificationException", v.output());
        assertEquals(SnippetCheck.CONFIRMED, v.verdict());
    }

    @Test
    void anOptionMayExplainAfterADash() {
        assertTrue(SnippetCheck.matchesOption("false", "false — the hash changed after insertion"));
        assertTrue(SnippetCheck.matchesOption("1\n2", "1, 2"));
        assertFalse(SnippetCheck.matchesOption("12", "1 — one"));
    }

    @Test
    void proseOptionsCannotBeSettled() {
        SnippetCheck.Verdict v = SnippetCheck.check(runner, exercise("x", MAP_CODE, "a",
                "a", "The second put replaces the value", "b", "The map keeps both values"));
        assertEquals(SnippetCheck.NO_LITERAL_MATCH, v.verdict());
    }

    @Test
    void aFragmentThatDoesNotCompileNeedsStubs() {
        SnippetCheck.Verdict v = SnippetCheck.check(runner,
                exercise("x", "repository.save(order);\nSystem.out.println(\"ok\");", "a", "a", "ok"));
        assertEquals(SnippetCheck.NEEDS_STUBS, v.group());
        assertEquals("does-not-compile", v.verdict());
    }

    @Test
    void wrapMovesTypesOutAndMakesMethodsStatic() {
        String wrapped = SnippetCheck.wrap("""
                record Point(int x, int y) {}
                int twice(int n) {
                    return n * 2;
                }
                System.out.println(twice(new Point(2, 3).x()));""");
        assertTrue(wrapped.indexOf("record Point") < wrapped.indexOf("public class Main"), wrapped);
        assertTrue(wrapped.contains("static int twice(int n)"), wrapped);
        assertTrue(wrapped.contains("import java.util.*;"), wrapped);
    }

    @Test
    void onlyJavaPredictionsAreCheckable() {
        assertTrue(SnippetCheck.checkable(exercise("x", MAP_CODE, "a", "a", "1")));
        ObjectNode spot = (ObjectNode) exercise("y", MAP_CODE, "a", "a", "1");
        spot.put("type", "spot_bug");
        assertFalse(SnippetCheck.checkable(spot));
        ObjectNode sql = (ObjectNode) exercise("z", "SELECT 1", "a", "a", "1");
        sql.put("codeLang", "sql");
        assertFalse(SnippetCheck.checkable(sql));
    }
}
