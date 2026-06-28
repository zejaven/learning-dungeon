package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualStaticTest {

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
    void classInitializesOnlyOnceForRepeatedStaticUse() {
        String out = captureTrace(() -> {
            VisualStatic scene = new VisualStatic("Settings");
            scene.staticField("mode", "dev");
            scene.staticField("mode", "prod");
        });
        assertEquals(1, countOccurrences(out, "\"event\":\"STATIC_CLASS_INIT\""),
                "class initialization should be emitted once, got:\n" + out);
        assertTrue(out.contains("\"initializationCount\":1"),
                "state should keep one initialization, got:\n" + out);
        assertTrue(out.contains("STATIC_FIELD_WRITE"),
                "expected a static field write event, got:\n" + out);
    }

    @Test
    void staticFieldIsSharedWhileInstanceFieldsStayPerObject() {
        String out = captureTrace(() -> {
            VisualStatic scene = new VisualStatic("Ticket");
            scene.staticField("nextNumber", "1");
            scene.newInstance("ticketA", "number=1");
            scene.staticField("nextNumber", "2");
            scene.newInstance("ticketB", "number=2");
            scene.instanceField("ticketA", "status", "paid");
        });
        assertTrue(out.contains("INSTANCE_CREATED"), "expected object creation events, got:\n" + out);
        assertTrue(out.contains("INSTANCE_FIELD_WRITE"), "expected instance field write, got:\n" + out);
        assertTrue(out.contains("\"id\":\"ticketA\""), "first object missing, got:\n" + out);
        assertTrue(out.contains("\"id\":\"ticketB\""), "second object missing, got:\n" + out);
    }

    @Test
    void compileTimeConstantDoesNotInitializeClassInTheModel() {
        String out = captureTrace(() -> {
            VisualStatic scene = new VisualStatic("Defaults");
            scene.constant("MAX_RETRIES", "3");
        });
        assertTrue(out.contains("STATIC_CONSTANT"), "expected constant event, got:\n" + out);
        assertEquals(0, countOccurrences(out, "\"event\":\"STATIC_CLASS_INIT\""),
                "constant-only example should not initialize the class, got:\n" + out);
        String last = out.lines().reduce("", (acc, line) -> line.isEmpty() ? acc : line);
        assertTrue(last.contains("\"initialized\":false"),
                "constant-only state should stay uninitialized, got:\n" + last);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualStatic scene = new VisualStatic("Mapper");
            scene.staticNestedClass("Mapper.Row", "no outer object required");
            scene.callStatic("parse(String)", "uses only arguments");
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
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
