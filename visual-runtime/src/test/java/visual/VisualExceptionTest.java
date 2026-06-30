package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualExceptionTest {

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
    void unhandledExceptionPropagatesAndIsUncaught() {
        String out = captureTrace(() -> {
            VisualException vm = new VisualException();
            vm.call("main");
            vm.call("readConfig");
            vm.throwException("NullPointerException", "config is null");
        });
        assertTrue(out.contains("THROW"), "expected a THROW event, got:\n" + out);
        assertTrue(out.contains("PROPAGATE"), "expected a PROPAGATE event, got:\n" + out);
        assertTrue(out.contains("UNCAUGHT"), "expected an UNCAUGHT event, got:\n" + out);
    }

    @Test
    void matchingCatchHandlesException() {
        String out = captureTrace(() -> {
            VisualException vm = new VisualException();
            vm.call("main", "RuntimeException", false);
            vm.call("parsePort");
            vm.throwException("NumberFormatException", "for input \"abc\"");
        });
        assertTrue(out.contains("CATCH"), "expected a CATCH event, got:\n" + out);
        assertFalse(out.contains("UNCAUGHT"), "should have been caught, got:\n" + out);
    }

    @Test
    void finallyRunsWhileUnwinding() {
        String out = captureTrace(() -> {
            VisualException vm = new VisualException();
            vm.call("main", "Exception", false);
            vm.call("withResource", null, true);
            vm.throwException("IllegalStateException", "closed");
        });
        assertTrue(out.contains("FINALLY"), "expected a FINALLY event, got:\n" + out);
        assertTrue(out.contains("CATCH"), "expected a CATCH event, got:\n" + out);
    }

    @Test
    void classifiesTypesCorrectly() {
        assertEquals("UNCHECKED", VisualException.category("NullPointerException"));
        assertEquals("UNCHECKED", VisualException.category("NumberFormatException"));
        assertEquals("CHECKED", VisualException.category("IOException"));
        assertEquals("CHECKED", VisualException.category("FileNotFoundException"));
        assertEquals("ERROR", VisualException.category("OutOfMemoryError"));
        // catch (Exception) catches RuntimeException subtypes but not Errors.
        assertTrue(VisualException.assignable("NullPointerException", "Exception"));
        assertTrue(VisualException.assignable("FileNotFoundException", "IOException"));
        assertFalse(VisualException.assignable("OutOfMemoryError", "Exception"));
    }

    @Test
    void cacheReleaseCanReduceMemoryPressure() {
        String out = captureTrace(() -> {
            VisualException vm = new VisualException();
            vm.heapBudget(32);
            vm.allocateMemory("baseline objects", 20);
            vm.allocateMemory("image cache", 8, "cache");
            vm.releaseCaches();
            vm.allocateMemory("next request", 8);
        });
        assertTrue(out.contains("MEMORY_PRESSURE"), "expected memory pressure, got:\n" + out);
        assertTrue(out.contains("CACHE_RELEASED"), "expected cache release, got:\n" + out);
        assertFalse(out.contains("OUT_OF_MEMORY"), "cache release should avoid OOM, got:\n" + out);
    }

    @Test
    void outOfMemoryErrorPropagatesOutsideCatchException() {
        String out = captureTrace(() -> {
            VisualException vm = new VisualException();
            vm.heapBudget(16);
            vm.call("main", "Exception", false);
            vm.call("loadReport");
            vm.allocateMemory("large result", 32);
        });
        assertTrue(out.contains("OUT_OF_MEMORY"), "expected OOM event, got:\n" + out);
        assertTrue(out.contains("UNCAUGHT"), "catch(Exception) should not catch Error, got:\n" + out);
    }

    @Test
    void fatalErrorCanBeCaughtAtBoundaryThenFailedFast() {
        String out = captureTrace(() -> {
            VisualException vm = new VisualException();
            vm.heapBudget(16);
            vm.call("main", "Throwable", true);
            vm.call("worker");
            vm.allocateMemory("batch", 32);
            vm.failFast("write diagnostics and restart");
        });
        assertTrue(out.contains("OUT_OF_MEMORY"), "expected OOM event, got:\n" + out);
        assertTrue(out.contains("CATCH"), "catch(Throwable) should catch the Error at the boundary, got:\n" + out);
        assertTrue(out.contains("FAIL_FAST"), "expected fail-fast event, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualException vm = new VisualException();
            vm.describe("IOException");
            vm.describe("ArithmeticException");
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
