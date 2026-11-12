package com.interviewlearning.lesson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.interviewlearning.config.RepoPaths;
import com.interviewlearning.generation.GenerationService;
import com.interviewlearning.lesson.verify.SnippetCheckTest;
import com.interviewlearning.runner.JavaCodeRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The check after a lesson generation, with the AI replaced by steps that write
 * the lesson file, and the code run for real.
 */
class LessonGeneratorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TOPIC = "t";
    private static final String CODE = """
            List<Integer> list = new ArrayList<>(List.of(1, 2, 3));
            list.remove(Integer.valueOf(1));
            System.out.println(list);""";

    @TempDir
    Path root;

    private LessonGenerator generator;
    private Path file;

    @BeforeEach
    void setUp() throws IOException {
        JavaCodeRunner runner = new JavaCodeRunner(new RepoPaths(""), MAPPER, 10, "128m");
        generator = new LessonGenerator(null, runner, new RepoPaths(root.toString()), MAPPER);
        file = root.resolve("topics").resolve(TOPIC).resolve("learning-atoms.json");
        Files.createDirectories(file.getParent());
    }

    /** The prints-[2, 3] exercise with option {@code marked} as its key. */
    private static JsonNode prediction(String id, String marked) {
        return SnippetCheckTest.exercise(id, CODE, marked, "a", "[1, 2]", "b", "[2, 3]", "c", "[1, 2, 3]");
    }

    private static String lesson(JsonNode... exercises) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode atom = root.putArray("atoms").addObject();
        atom.put("id", "atom-1");
        for (JsonNode ex : exercises) {
            atom.withArray("discovery").add(ex);
        }
        atom.putArray("practice");
        return root.toString();
    }

    /** AI steps: each call writes the next content (null = the run fails). */
    private final class FakeSteps implements GenerationService.Steps {
        final Deque<String> writes = new ArrayDeque<>();
        final List<String> prompts = new ArrayList<>();

        FakeSteps(String... contents) {
            writes.addAll(List.of(contents));
        }

        @Override
        public boolean ai(String prompt) {
            prompts.add(prompt);
            String next = writes.poll();
            if (next == null || next.equals("FAIL")) {
                return false;
            }
            try {
                Files.writeString(file, next, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return true;
        }

        @Override
        public void say(String message) {
        }
    }

    private String markedOf(String exerciseId) throws IOException {
        JsonNode root = MAPPER.readTree(file.toFile());
        for (JsonNode ex : root.path("atoms").get(0).path("discovery")) {
            if (ex.path("id").asText().equals(exerciseId)) {
                for (JsonNode o : ex.path("options")) {
                    if (o.path("correct").asBoolean()) {
                        return o.path("id").asText();
                    }
                }
            }
        }
        return null;
    }

    @Test
    void aRightKeyNeedsNoRepair() throws IOException {
        FakeSteps steps = new FakeSteps(lesson(prediction("x", "b")));
        String message = generator.run(TOPIC, "generate", steps);
        assertEquals(1, steps.prompts.size(), "no repair round");
        assertTrue(message.contains("1 of 1 new code exercise(s) confirmed"), message);
    }

    @Test
    void aWrongKeyGetsOneRepairRound() throws IOException {
        FakeSteps steps = new FakeSteps(lesson(prediction("x", "a")), lesson(prediction("x", "b")));
        String message = generator.run(TOPIC, "generate", steps);
        assertEquals(2, steps.prompts.size());
        String fix = steps.prompts.get(1);
        assertTrue(fix.contains("EXERCISE x") && fix.contains("[2, 3]") && fix.contains("MARKED CORRECT"), fix);
        assertTrue(message.contains("fixed after the run contradicted them: x"), message);
        assertFalse(message.contains("STILL CONTRADICTED"), message);
        assertEquals("b", markedOf("x"));
    }

    @Test
    void aRepairThatBreaksTheJsonIsRolledBack() throws IOException {
        FakeSteps steps = new FakeSteps(lesson(prediction("x", "a")), "{ not json");
        String message = generator.run(TOPIC, "generate", steps);
        assertTrue(message.contains("rolled back"), message);
        assertTrue(message.contains("STILL CONTRADICTED by the run: x (marked a, prints \"[2, 3]\")"), message);
        assertEquals("a", markedOf("x"), "the generated lesson is back");
    }

    @Test
    void aRepairThatLosesAnIdIsRolledBack() throws IOException {
        FakeSteps steps = new FakeSteps(lesson(prediction("x", "a"), prediction("y", "b")),
                lesson(prediction("x", "b")));
        String message = generator.run(TOPIC, "generate", steps);
        assertTrue(message.contains("rolled back"), message);
        assertEquals("b", markedOf("y"), "y survived the repair round");
    }

    @Test
    void anUnchangedOldExerciseIsNotCheckedAgain() throws IOException {
        // An augment run keeps the old exercise as it was, even a contradicted one.
        Files.writeString(file, lesson(prediction("old", "a")), StandardCharsets.UTF_8);
        FakeSteps steps = new FakeSteps(lesson(prediction("old", "a"), prediction("new", "b")));
        String message = generator.run(TOPIC, "augment", steps);
        assertEquals(1, steps.prompts.size(), "the old false alarm does not trigger a repair");
        assertTrue(message.contains("1 of 1 new code exercise(s) confirmed"), message);
    }

    @Test
    void aLessonWithoutJavaPredictionsIsNotChecked() throws IOException {
        ObjectNode tf = MAPPER.createObjectNode();
        tf.put("id", "tf");
        tf.put("type", "true_false");
        tf.put("answer", true);
        FakeSteps steps = new FakeSteps(lesson(tf));
        assertEquals("Lesson written. No new code exercises to check by running.",
                generator.run(TOPIC, "generate", steps));
    }

    @Test
    void aFailedGenerationIsAnError() {
        assertThrows(IllegalStateException.class, () -> generator.run(TOPIC, "generate", new FakeSteps("FAIL")));
    }
}
