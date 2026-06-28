package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualStaticNestedClassTest {

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
    void emitsStaticNestedCreationWithoutOuterInstance() {
        String out = captureTrace(() -> {
            VisualStaticNestedClass scene = new VisualStaticNestedClass(
                    "Invoice", "Invoice.NumberFormatter", "Invoice.Item");
            scene.createStaticNested("formatter", "pattern=INV-0000");
        });

        assertTrue(out.contains("STATIC_NESTED_DECLARED"), "expected declaration event, got:\n" + out);
        assertTrue(out.contains("STATIC_NESTED_CREATED"), "expected static nested creation event, got:\n" + out);
        assertTrue(out.contains("\"outerRef\":null"), "static nested object should not carry an outer reference:\n" + out);
    }

    @Test
    void emitsInnerClassCreationWithOuterReference() {
        String out = captureTrace(() -> {
            VisualStaticNestedClass scene = new VisualStaticNestedClass(
                    "Kitchen", "Kitchen.Timer", "Kitchen.Shelf");
            String kitchen = scene.createOuter("mainKitchen", "name=Main");
            scene.createInner(kitchen, "spiceShelf", "code=spices");
        });

        assertTrue(out.contains("OUTER_INSTANCE_CREATED"), "expected outer object event, got:\n" + out);
        assertTrue(out.contains("INNER_CLASS_CREATED"), "expected inner class event, got:\n" + out);
        assertTrue(out.contains("\"outerRef\":\"outer1\""), "inner object should carry an outer reference:\n" + out);
    }

    @Test
    void emitsStaticMemberAccess() {
        String out = captureTrace(() -> {
            VisualStaticNestedClass scene = new VisualStaticNestedClass(
                    "Ticket", "Ticket.Counter", "Ticket.Line");
            scene.setStaticField("Ticket.Counter", "next", "100");
            scene.accessStaticField("Ticket.Counter", "next");
        });

        assertTrue(out.contains("STATIC_MEMBER_SET"), "expected static member set event, got:\n" + out);
        assertTrue(out.contains("STATIC_MEMBER_ACCESS"), "expected static member access event, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualStaticNestedClass scene = new VisualStaticNestedClass();
            String outer = scene.createOuter("outer");
            scene.createStaticNested("helper");
            scene.createInner(outer, "inner");
            scene.setStaticField("Outer.Helper", "mode", "shared");
            scene.accessStaticField("Outer.Helper", "mode");
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
