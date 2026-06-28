package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualDataTypesTest {

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
    void primitivesCarryTheirSizeAndRange() {
        String out = captureTrace(() -> {
            VisualDataTypes t = new VisualDataTypes();
            t.primitive("b", "byte", "127");
            t.primitive("i", "int", "5");
            t.primitive("flag", "boolean", "true");
        });
        assertTrue(out.contains("TYPE_PRIMITIVE"), "expected a primitive event, got:\n" + out);
        assertTrue(out.contains("\"bits\":8"), "byte should be 8 bits, got:\n" + out);
        assertTrue(out.contains("\"bits\":32"), "int should be 32 bits, got:\n" + out);
        assertTrue(out.contains("-128..127"), "byte range should be shown, got:\n" + out);
    }

    @Test
    void intOverflowWrapsAround() {
        String out = captureTrace(() -> {
            VisualDataTypes t = new VisualDataTypes();
            t.primitive("max", "int", "2147483647");
            t.overflowInt("max");
        });
        assertTrue(out.contains("TYPE_OVERFLOW"), "expected an overflow event, got:\n" + out);
        // 2147483647 + 1 wraps to Integer.MIN_VALUE.
        assertTrue(out.contains("-2147483648"), "overflow should wrap to MIN_VALUE, got:\n" + out);
    }

    @Test
    void charIsAnUnsignedNumber() {
        String out = captureTrace(() -> {
            VisualDataTypes t = new VisualDataTypes();
            t.primitive("c", "char", "A");
            t.charIsANumber("c");
        });
        assertTrue(out.contains("TYPE_CHAR"), "expected a char event, got:\n" + out);
        assertTrue(out.contains("65"), "'A' should be code 65, got:\n" + out);
    }

    @Test
    void floatingPointIsInexact() {
        String out = captureTrace(() -> {
            VisualDataTypes t = new VisualDataTypes();
            t.floatingImprecision("sum");
        });
        assertTrue(out.contains("TYPE_FLOAT"), "expected a float event, got:\n" + out);
        assertTrue(out.contains("0.30000000000000004"),
                "0.1 + 0.2 should be the inexact result, got:\n" + out);
    }

    @Test
    void narrowingCastCanLoseData() {
        String out = captureTrace(() -> {
            VisualDataTypes t = new VisualDataTypes();
            t.primitive("big", "long", "300");
            t.narrow("small", "byte", "big");
        });
        assertTrue(out.contains("TYPE_CONVERT"), "expected a convert event, got:\n" + out);
        // (byte) 300 == 44.
        assertTrue(out.contains("44"), "(byte) 300 should be 44, got:\n" + out);
    }

    @Test
    void integerCacheMakesIdentitySurprising() {
        String out = captureTrace(() -> {
            VisualDataTypes t = new VisualDataTypes();
            t.integerCacheCompare(127);
            t.integerCacheCompare(200);
        });
        assertTrue(out.contains("TYPE_CACHE"), "expected a cache event, got:\n" + out);
        assertTrue(out.contains("\"result\":\"true"), "valueOf(127) == valueOf(127) is true, got:\n" + out);
        assertTrue(out.contains("\"result\":\"false"), "valueOf(200) == valueOf(200) is false, got:\n" + out);
    }

    @Test
    void autoboxingProducesAReferenceType() {
        String out = captureTrace(() -> {
            VisualDataTypes t = new VisualDataTypes();
            t.primitive("i", "int", "42");
            t.box("boxed", "Integer", "i");
        });
        assertTrue(out.contains("TYPE_BOX"), "expected a box event, got:\n" + out);
        assertTrue(out.contains("\"category\":\"wrapper\""), "boxed value should be a wrapper, got:\n" + out);
    }

    @Test
    void defaultsAreZeroAndNull() {
        String out = captureTrace(() -> {
            VisualDataTypes t = new VisualDataTypes();
            t.defaultValue("count", "int");
            t.defaultValue("name", "String");
        });
        assertTrue(out.contains("TYPE_DEFAULT"), "expected a default event, got:\n" + out);
        assertTrue(out.contains("\"value\":\"0\""), "int field defaults to 0, got:\n" + out);
        assertTrue(out.contains("\"value\":\"null\""), "reference field defaults to null, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualDataTypes t = new VisualDataTypes();
            t.primitive("i", "int", "5");
            t.reference("s", "String", "\"hi\"");
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
