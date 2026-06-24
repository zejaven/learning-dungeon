package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualMemoryTest {

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
    void primitiveStoresValueInItsOwnSlot() {
        String out = captureTrace(() -> {
            VisualMemory mem = new VisualMemory();
            mem.primitive("a", "int", "5");
            mem.copyPrimitive("b", "int", "a");
            mem.setPrimitive("b", "99");
        });
        assertTrue(out.contains("MEM_PRIMITIVE"), "expected a primitive event, got:\n" + out);
        assertTrue(out.contains("MEM_COPY_PRIMITIVE"), "expected a primitive copy event, got:\n" + out);
        // The copy changed; the source value 5 must still be present in the last snapshot.
        String last = out.lines().reduce("", (acc, l) -> l.isEmpty() ? acc : l);
        assertTrue(last.contains("\"value\":\"5\""), "source slot should still hold 5, got:\n" + last);
        assertTrue(last.contains("\"value\":\"99\""), "copied slot should hold 99, got:\n" + last);
    }

    @Test
    void aliasingMakesTwoNamesShareOneObject() {
        String out = captureTrace(() -> {
            VisualMemory mem = new VisualMemory();
            mem.newObject("p", "Point", "x=1", "y=2");
            mem.copyReference("q", "Point", "p");
            mem.mutateField("q", "x", "99");
        });
        assertTrue(out.contains("MEM_NEW"), "expected an allocation event, got:\n" + out);
        assertTrue(out.contains("MEM_COPY_REFERENCE"), "expected a reference copy event, got:\n" + out);
        assertTrue(out.contains("MEM_MUTATE"), "expected a mutate event, got:\n" + out);
        // Only ONE object was ever allocated (aliasing copies the handle, not the object).
        assertEquals(0, countOccurrences(out, "\"id\":\"obj2\""),
                "expected a single shared object, got:\n" + out);
        // The mutation through q is visible (the one shared object now holds x=99).
        String last = out.lines().reduce("", (acc, l) -> l.isEmpty() ? acc : l);
        assertTrue(last.contains("\"value\":\"99\""), "field changed through q should be 99, got:\n" + last);
    }

    @Test
    void reassigningALocalDoesNotAffectTheCallersObject() {
        String out = captureTrace(() -> {
            VisualMemory mem = new VisualMemory();
            mem.newObject("p", "Point", "x=1", "y=2");
            mem.call("modify", "param", "Point", "p");
            mem.mutateField("param", "x", "99"); // visible to caller (shared object)
            mem.reassignNew("param", "Point", "x=0", "y=0"); // NOT visible to caller
            mem.ret();
        });
        assertTrue(out.contains("MEM_CALL"), "expected a call event, got:\n" + out);
        assertTrue(out.contains("MEM_REASSIGN"), "expected a reassign event, got:\n" + out);
        assertTrue(out.contains("MEM_RETURN"), "expected a return event, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualMemory mem = new VisualMemory();
            mem.newObject("p", "Point", "x=1", "y=2");
            mem.setNull("p");
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
