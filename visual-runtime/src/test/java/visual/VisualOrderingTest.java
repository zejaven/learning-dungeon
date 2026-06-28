package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualOrderingTest {

    private String captureTrace(Runnable body) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            body.run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void naturalOrderingEmitsCompareToAndSortedEvents() {
        AtomicReference<List<Task>> sorted = new AtomicReference<>();
        String out = captureTrace(() -> {
            VisualOrdering<Task> ordering = VisualOrdering.natural(
                    "tasks",
                    "priority, then id",
                    "priority, затем id");
            ordering.add(new Task(2, "B"));
            ordering.add(new Task(1, "A"));
            sorted.set(ordering.sort());
        });

        assertTrue(out.contains("ORDERING_COMPARE_TO"), "expected compareTo event, got:\n" + out);
        assertTrue(out.contains("ORDERING_SORTED"), "expected sorted event, got:\n" + out);
        assertEquals(List.of(new Task(1, "A"), new Task(2, "B")), sorted.get());
    }

    @Test
    void comparatorOrderingEmitsComparatorEvent() {
        String out = captureTrace(() -> {
            VisualOrdering<String> ordering = VisualOrdering.usingComparator(
                    "words",
                    Comparator.comparingInt(String::length),
                    "shorter words first",
                    "короткие слова раньше");
            ordering.compare("tea", "coffee");
        });

        assertTrue(out.contains("ORDERING_COMPARATOR_COMPARE"),
                "expected Comparator.compare event, got:\n" + out);
    }

    @Test
    void zeroResultEmitsSameSortPositionEvent() {
        String out = captureTrace(() -> {
            VisualOrdering<String> ordering = VisualOrdering.usingComparator(
                    "words",
                    Comparator.comparingInt(String::length),
                    "word length",
                    "длина слова");
            ordering.sameSortPosition("tea", "jam");
        });

        assertTrue(out.contains("ORDERING_COMPARE_ZERO"),
                "expected zero-result event, got:\n" + out);
    }

    @Test
    void safeIntCompareShowsSubtractionOverflowRisk() {
        String out = captureTrace(() ->
                VisualOrdering.compareIntFields("rank", "tiny", Integer.MIN_VALUE, "one", 1));

        assertTrue(out.contains("ORDERING_SAFE_COMPARE"),
                "expected safe-compare event, got:\n" + out);
        assertTrue(out.contains("\"overflowRisk\":true"),
                "expected overflow risk in state, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualOrdering<Task> ordering = VisualOrdering.natural(
                    "tasks",
                    "priority",
                    "priority");
            ordering.add(new Task(1, "A"));
            ordering.add(new Task(2, "B"));
            ordering.compare(new Task(1, "A"), new Task(2, "B"));
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }

    private record Task(int priority, String id) implements Comparable<Task> {
        @Override
        public int compareTo(Task other) {
            int byPriority = Integer.compare(priority, other.priority);
            if (byPriority != 0) {
                return byPriority;
            }
            return id.compareTo(other.id);
        }

        @Override
        public String toString() {
            return id + "(p=" + priority + ")";
        }
    }
}
