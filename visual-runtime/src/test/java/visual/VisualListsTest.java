package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualListsTest {

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
    void arrayListGrowsWhenBackingArrayIsFull() {
        // Default capacity is 4, so the 5th add must trigger a grow-and-copy.
        String out = captureTrace(() -> {
            VisualArrayList<Integer> list = new VisualArrayList<>("list");
            for (int i = 0; i < 5; i++) {
                list.add(i);
            }
        });
        assertTrue(out.contains("ARRAYLIST_GROW"),
                "expected a grow event, got:\n" + out);
    }

    @Test
    void insertAtFrontShiftsEveryFollowingElement() {
        String out = captureTrace(() -> {
            VisualArrayList<String> list = new VisualArrayList<>("list");
            list.add("a");
            list.add("b");
            list.add("c");
            list.add(0, "x"); // must shift a, b, c -> 3 shifts
        });
        assertTrue(out.contains("ARRAYLIST_INSERT"),
                "expected an insert event, got:\n" + out);
        assertTrue(out.contains("\"cost\":3"),
                "expected 3 shifts recorded in state, got:\n" + out);
    }

    @Test
    void arrayListGetIsDirectWithZeroCost() {
        String out = captureTrace(() -> {
            VisualArrayList<Integer> list = new VisualArrayList<>("list");
            list.add(10);
            list.add(20);
            assertEquals(20, list.get(1));
        });
        assertTrue(out.contains("ARRAYLIST_GET"),
                "expected a get event, got:\n" + out);
        assertTrue(out.contains("\"costKind\":\"direct\""),
                "expected direct access recorded in state, got:\n" + out);
    }

    @Test
    void linkedListGetWalksAndCountsHops() {
        String out = captureTrace(() -> {
            VisualLinkedList<Integer> list = new VisualLinkedList<>("list");
            for (int i = 0; i < 4; i++) {
                list.add(i);
            }
            assertEquals(2, list.get(2)); // walks 2 hops from head
        });
        assertTrue(out.contains("LINKEDLIST_GET"),
                "expected a get event, got:\n" + out);
        assertTrue(out.contains("\"costKind\":\"hops\""),
                "expected hop-counting recorded in state, got:\n" + out);
    }

    @Test
    void linkedListGetWalksFromNearerEnd() {
        String out = captureTrace(() -> {
            VisualLinkedList<Integer> list = new VisualLinkedList<>("list");
            for (int i = 0; i < 6; i++) {
                list.add(i);
            }
            list.get(5); // last element -> should walk from the tail, 0 hops
        });
        assertTrue(out.contains("from the tail"),
                "expected a tail-side walk, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualArrayList<Integer> a = new VisualArrayList<>("a");
            a.add(1);
            VisualLinkedList<Integer> l = new VisualLinkedList<>("l");
            l.add(1);
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
