package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualFinalTest {

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
    void finalLocalLocksTheBinding() {
        String out = captureTrace(() -> {
            VisualFinal f = new VisualFinal();
            f.localVar("x", "int", "10");
        });
        assertTrue(out.contains("FINAL_LOCAL"), "expected a local event, got:\n" + out);
        assertTrue(out.contains("\"locked\":true"), "a final local should be locked, got:\n" + out);
    }

    @Test
    void reassigningAFinalIsBlocked() {
        String out = captureTrace(() -> {
            VisualFinal f = new VisualFinal();
            f.localVar("x", "int", "10");
            f.reassignBlocked("x", "20");
        });
        assertTrue(out.contains("FINAL_BLOCK"), "expected a block event, got:\n" + out);
        assertTrue(out.contains("\"status\":\"blocked\""), "the attempt should be blocked, got:\n" + out);
        // The binding value is unchanged after the rejected reassignment.
        assertTrue(out.contains("\"value\":\"10\""), "the binding should still be 10, got:\n" + out);
    }

    @Test
    void blankFinalIsUnlockedUntilAssigned() {
        String out = captureTrace(() -> {
            VisualFinal f = new VisualFinal();
            f.blankField("id", "int");
            f.assignOnce("id", "42");
        });
        assertTrue(out.contains("FINAL_BLANK"), "expected a blank event, got:\n" + out);
        assertTrue(out.contains("\"value\":\"(unassigned)\""), "blank final starts unassigned, got:\n" + out);
        assertTrue(out.contains("FINAL_ASSIGN"), "expected an assign event, got:\n" + out);
        assertTrue(out.contains("\"value\":\"42\""), "after assignment the value is 42, got:\n" + out);
    }

    @Test
    void finalReferenceLocksBindingNotObject() {
        String out = captureTrace(() -> {
            VisualFinal f = new VisualFinal();
            f.reference("list", "List", "[a]");
            f.mutateObject("list", "add(\"b\")", "[a, b]");
        });
        assertTrue(out.contains("FINAL_MUTATE"), "expected a mutate event, got:\n" + out);
        assertTrue(out.contains("\"mutable\":true"), "the reference should be mutable, got:\n" + out);
        assertTrue(out.contains("[a, b]"), "the object contents should change, got:\n" + out);
    }

    @Test
    void staticConstantIsLocked() {
        String out = captureTrace(() -> {
            VisualFinal f = new VisualFinal();
            f.constant("PI", "double", "3.14159");
        });
        assertTrue(out.contains("FINAL_STATIC"), "expected a static event, got:\n" + out);
        assertTrue(out.contains("\"context\":\"static\""), "PI should be a static constant, got:\n" + out);
    }

    @Test
    void parameterMethodAndClassContexts() {
        String out = captureTrace(() -> {
            VisualFinal f = new VisualFinal();
            f.parameter("n", "int", "7");
            f.method("compute()");
            f.clazz("Money");
        });
        assertTrue(out.contains("FINAL_PARAM"), "expected a parameter event, got:\n" + out);
        assertTrue(out.contains("FINAL_METHOD"), "expected a method event, got:\n" + out);
        assertTrue(out.contains("FINAL_CLASS"), "expected a class event, got:\n" + out);
        assertTrue(out.contains("\"context\":\"parameter\""), "n should be a parameter, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualFinal f = new VisualFinal();
            f.localVar("x", "int", "10");
            f.constant("PI", "double", "3.14");
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
