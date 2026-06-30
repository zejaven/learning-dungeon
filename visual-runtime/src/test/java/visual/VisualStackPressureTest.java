package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualStackPressureTest {

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
    void largerFramesOverflowAfterFewerCalls() {
        String out = captureTrace(() -> {
            VisualStackPressure stack = new VisualStackPressure(320);
            stack.recurseUntilOverflow("wideRecursiveCall", 128,
                    "long subtotal", "long tax", "long discount", "long checksum");
        });

        assertTrue(out.contains("STACK_BUDGET_SCENE"), "expected a budget scene, got:\n" + out);
        assertTrue(out.contains("STACK_FRAME_PUSH"), "expected pushed frames, got:\n" + out);
        assertTrue(out.contains("STACK_FRAME_OVERFLOW"), "expected overflow, got:\n" + out);
        assertTrue(out.contains("\"bytes\":128"), "expected frame size in state, got:\n" + out);
    }

    @Test
    void returningFramesReusesStackSpace() {
        String out = captureTrace(() -> {
            VisualStackPressure stack = new VisualStackPressure(192);
            for (int i = 0; i < 3; i++) {
                if (!stack.call("processOneItem", 64, "int item")) {
                    stack.ret();
                }
            }
        });

        assertTrue(out.contains("STACK_FRAME_PUSH"), "expected pushes, got:\n" + out);
        assertTrue(out.contains("STACK_FRAME_POP"), "expected pops, got:\n" + out);
        assertFalse(out.contains("STACK_FRAME_OVERFLOW"), "iteration should not overflow:\n" + out);
    }

    @Test
    void heapObjectDoesNotBecomePartOfEachFrame() {
        String out = captureTrace(() -> {
            VisualStackPressure stack = new VisualStackPressure(256);
            stack.allocateHeapObject("byte[1_000_000]", 1_000_000);
            stack.call("usesArray", 48, "byte[] data reference", "int index");
        });

        assertTrue(out.contains("HEAP_OBJECT_ALLOCATED"), "expected heap allocation event, got:\n" + out);
        assertTrue(out.contains("byte[1_000_000]"), "expected heap object label, got:\n" + out);
        assertFalse(out.contains("STACK_FRAME_OVERFLOW"), "one reference-sized frame should fit:\n" + out);
    }
}
