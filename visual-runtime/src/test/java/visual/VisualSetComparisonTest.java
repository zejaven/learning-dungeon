package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualSetComparisonTest {

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
    void emitsAddAndIterationOrderEvents() {
        AtomicReference<List<String>> linkedValues = new AtomicReference<>();
        AtomicReference<List<String>> treeValues = new AtomicReference<>();
        String out = captureTrace(() -> {
            VisualSetComparison<String> sets = new VisualSetComparison<>("stations");
            sets.add("traffic");
            sets.add("kitchen");
            sets.add("post");
            sets.showIterationOrder();
            linkedValues.set(sets.linkedHashSetValues());
            treeValues.set(sets.treeSetValues());
        });

        assertTrue(out.contains("SET_ADD"), "expected add events, got:\n" + out);
        assertTrue(out.contains("SET_ITERATION_ORDER"), "expected iteration event, got:\n" + out);
        assertEquals(List.of("traffic", "kitchen", "post"), linkedValues.get());
        assertEquals(List.of("kitchen", "post", "traffic"), treeValues.get());
    }

    @Test
    void detectsComparatorDuplicateOnlyInTreeSet() {
        AtomicReference<List<String>> hashValues = new AtomicReference<>();
        AtomicReference<List<String>> treeValues = new AtomicReference<>();
        String out = captureTrace(() -> {
            VisualSetComparison<String> sets = new VisualSetComparison<>(
                    "codes",
                    Comparator.comparingInt(String::length),
                    "string length",
                    "длина строки");
            sets.add("AA");
            sets.add("BB");
            hashValues.set(sets.hashSetValues());
            treeValues.set(sets.treeSetValues());
        });

        assertTrue(out.contains("SET_TREESET_COMPARATOR_DUPLICATE"),
                "expected comparator duplicate event, got:\n" + out);
        assertEquals(new HashSet<>(List.of("AA", "BB")), new HashSet<>(hashValues.get()));
        assertEquals(List.of("AA"), treeValues.get());
    }

    @Test
    void emitsNullPolicyWhenNaturalTreeSetRejectsNull() {
        AtomicReference<List<String>> linkedValues = new AtomicReference<>();
        AtomicReference<List<String>> treeValues = new AtomicReference<>();
        String out = captureTrace(() -> {
            VisualSetComparison<String> sets = new VisualSetComparison<>("labels");
            sets.add(null);
            linkedValues.set(sets.linkedHashSetValues());
            treeValues.set(sets.treeSetValues());
        });

        assertTrue(out.contains("SET_NULL_POLICY"), "expected null policy event, got:\n" + out);
        assertEquals(Collections.singletonList(null), linkedValues.get());
        assertTrue(treeValues.get().isEmpty());
    }

    @Test
    void emitsContainsAndRemoveEvents() {
        String out = captureTrace(() -> {
            VisualSetComparison<Integer> sets = new VisualSetComparison<>("numbers");
            sets.add(2);
            sets.add(1);
            sets.contains(2);
            sets.remove(1);
        });

        assertTrue(out.contains("SET_CONTAINS"), "expected contains event, got:\n" + out);
        assertTrue(out.contains("SET_REMOVE"), "expected remove event, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualSetComparison<String> sets = new VisualSetComparison<>("names");
            sets.add("Alice");
            sets.showIterationOrder();
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
