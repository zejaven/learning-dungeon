package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualHashMapTest {

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
    void emitsCollisionWhenTwoKeysShareABucket() {
        // "Aa" and "BB" famously share hashCode 2112 in Java.
        String out = captureTrace(() -> {
            VisualHashMap<String, Integer> map = new VisualHashMap<>("map");
            map.put("Aa", 1);
            map.put("BB", 2);
        });
        assertTrue(out.contains("HASHMAP_COLLISION"),
                "expected a collision event, got:\n" + out);
        // Both keys spread to the same value, hence the same bucket.
        assertEquals(VisualHashMap.spread("Aa"), VisualHashMap.spread("BB"));
    }

    @Test
    void emitsResizeWhenThresholdExceeded() {
        String out = captureTrace(() -> {
            VisualHashMap<String, Integer> map = new VisualHashMap<>("map");
            for (int i = 0; i <= 12; i++) {
                map.put("k" + i, i); // 13 entries -> size 13 > threshold 12
            }
        });
        assertTrue(out.contains("HASHMAP_RESIZE"),
                "expected a resize event, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualHashMap<String, Integer> map = new VisualHashMap<>("map");
            map.put("Alice", 10);
            map.get("Alice");
            map.get("Bob");
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
