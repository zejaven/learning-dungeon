package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualDispatchTest {

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
    void overrideResolvesByRuntimeType() {
        String out = captureTrace(() -> {
            VisualDispatch scene = new VisualDispatch();
            scene.declareClass("Animal", null);
            scene.declareClass("Dog", "Animal");
            scene.method("Animal", "speak()");
            scene.method("Dog", "speak()");
            scene.variable("a", "Animal", "Dog");
            scene.overrideCall("a", "speak");
        });
        assertTrue(out.contains("DISPATCH_OVERRIDE"), out);
        assertTrue(out.contains("\"binding\":\"RUNTIME\""), out);
        // The runtime type Dog wins over the static type Animal.
        assertTrue(out.contains("\"result\":\"Dog.speak()\""), out);
    }

    @Test
    void overrideFallsBackToInheritedBodyWhenNotRedefined() {
        String out = captureTrace(() -> {
            VisualDispatch scene = new VisualDispatch();
            scene.declareClass("Animal", null);
            scene.declareClass("Fish", "Animal");
            scene.method("Animal", "speak()"); // Fish does NOT override it
            scene.variable("a", "Animal", "Fish");
            scene.overrideCall("a", "speak");
        });
        // No Fish.speak(), so dispatch walks up to the inherited Animal.speak().
        assertTrue(out.contains("\"result\":\"Animal.speak()\""), out);
    }

    @Test
    void overloadResolvesByStaticTypeNotRuntimeType() {
        String out = captureTrace(() -> {
            VisualDispatch scene = new VisualDispatch();
            scene.declareClass("Printer", null);
            scene.method("Printer", "print(Object)");
            scene.method("Printer", "print(String)");
            // Object o = "hi";  -> static type Object, runtime type String
            scene.variable("o", "Object", "String");
            scene.overloadCall("Printer", "print", "o");
        });
        assertTrue(out.contains("DISPATCH_OVERLOAD"), out);
        assertTrue(out.contains("\"binding\":\"COMPILE\""), out);
        // The classic trap: the static type Object picks print(Object), NOT print(String).
        assertTrue(out.contains("\"result\":\"Printer.print(Object)\""), out);
    }

    @Test
    void overloadPicksMostSpecificApplicable() {
        String out = captureTrace(() -> {
            VisualDispatch scene = new VisualDispatch();
            scene.declareClass("Printer", null);
            scene.method("Printer", "print(Object)");
            scene.method("Printer", "print(String)");
            scene.variable("s", "String", "String");
            scene.overloadCall("Printer", "print", "s");
        });
        // With a String static type, the more specific print(String) wins.
        assertTrue(out.contains("\"result\":\"Printer.print(String)\""), out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualDispatch scene = new VisualDispatch();
            scene.declareClass("Animal", null);
            scene.method("Animal", "speak()");
            scene.variable("a", "Animal", "Animal");
            scene.overrideCall("a", "speak");
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
        assertFalse(out.isBlank());
    }
}
