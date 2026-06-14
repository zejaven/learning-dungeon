package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualChainTest {

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
    void buildingTheChainEmitsHandlerAdded() {
        String out = captureTrace(() -> {
            VisualChain chain = new VisualChain("approvals");
            chain.addHandler("TeamLead", 1_000);
        });
        assertTrue(out.contains("CHAIN_CREATED"), "expected a creation event, got:\n" + out);
        assertTrue(out.contains("HANDLER_ADDED"), "expected a handler-added event, got:\n" + out);
    }

    @Test
    void firstCapableHandlerHandlesAndStops() {
        String out = captureTrace(() -> {
            VisualChain chain = new VisualChain("approvals");
            chain.addHandler("TeamLead", 1_000).addHandler("Manager", 10_000);
            Integer approver = chain.handle("laptop", 800);
            assertEquals(1_000, approver, "TeamLead (limit 1000) should approve 800");
        });
        assertTrue(out.contains("REQUEST_RECEIVED"), "expected a received event, got:\n" + out);
        assertTrue(out.contains("REQUEST_HANDLED"), "expected a handled event, got:\n" + out);
        // 800 is approved by the first handler, so 'Manager' is never even inspected.
        assertFalse(out.contains("'Manager' inspects"), "Manager should not be reached, got:\n" + out);
    }

    @Test
    void requestEscalatesThroughHandlers() {
        String out = captureTrace(() -> {
            VisualChain chain = new VisualChain("approvals");
            chain.addHandler("TeamLead", 1_000)
                    .addHandler("Manager", 10_000)
                    .addHandler("Director", 100_000);
            Integer approver = chain.handle("server", 25_000);
            assertEquals(100_000, approver, "Director (limit 100000) should approve 25000");
        });
        assertTrue(out.contains("HANDLER_PASS"), "expected at least one pass event, got:\n" + out);
        assertTrue(out.contains("REQUEST_HANDLED"), "expected a handled event, got:\n" + out);
    }

    @Test
    void unapprovableRequestFallsOffTheEnd() {
        String out = captureTrace(() -> {
            VisualChain chain = new VisualChain("approvals");
            chain.addHandler("TeamLead", 1_000).addHandler("Manager", 10_000);
            Integer approver = chain.handle("datacenter", 500_000);
            assertNull(approver, "nobody can approve 500000");
        });
        assertTrue(out.contains("REQUEST_UNHANDLED"), "expected an unhandled event, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualChain chain = new VisualChain("approvals");
            chain.addHandler("TeamLead", 1_000);
            chain.handle("pen", 5);
            chain.handle("yacht", 9_999_999);
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
