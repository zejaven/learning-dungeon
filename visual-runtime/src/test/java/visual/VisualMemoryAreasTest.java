package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualMemoryAreasTest {

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
    void bootsWithTheFourSharedAreasAndOneThread() {
        String out = captureTrace(() -> {
            VisualMemoryAreas jvm = new VisualMemoryAreas();
            assertEquals(4, jvm.sharedAreaCount());
            assertEquals(1, jvm.threadCount());
        });
        assertTrue(out.contains("AREAS_SCENE"), "expected the boot event, got:\n" + out);
        assertTrue(out.contains("\"heap\"") && out.contains("\"metaspace\"")
                        && out.contains("\"code-cache\"") && out.contains("\"string-pool\""),
                "expected all four shared areas in the state, got:\n" + out);
    }

    @Test
    void everyThreadAddsItsOwnStackWhileTheHeapStaysSingle() {
        String out = captureTrace(() -> {
            VisualMemoryAreas jvm = new VisualMemoryAreas();
            jvm.startThread("worker-1");
            jvm.startThread("worker-2");
            assertEquals(3, jvm.threadCount());
            jvm.endThread("worker-1");
            assertEquals(2, jvm.threadCount());
            // Shared areas never multiply with threads.
            assertEquals(4, jvm.sharedAreaCount());
        });
        assertTrue(out.contains("THREAD_STARTED"), "expected a thread-start event, got:\n" + out);
        assertTrue(out.contains("THREAD_EXITED"), "expected a thread-exit event, got:\n" + out);
    }

    @Test
    void objectsGoToTheHeapAndClassMetadataToMetaspace() {
        String out = captureTrace(() -> {
            VisualMemoryAreas jvm = new VisualMemoryAreas();
            jvm.loadClass("Order");
            jvm.allocate("main", "Order");
        });
        assertTrue(out.contains("CLASS_LOADED"), "expected a class-loaded event, got:\n" + out);
        assertTrue(out.contains("OBJECT_ALLOCATED"), "expected an allocation event, got:\n" + out);
        assertTrue(out.contains("Order.class"), "expected the class metadata item, got:\n" + out);
    }

    @Test
    void internedLiteralIsPooledOnceAndReusedAfterwards() {
        String out = captureTrace(() -> {
            VisualMemoryAreas jvm = new VisualMemoryAreas();
            jvm.internString("main", "OK");
            jvm.internString("main", "OK");
        });
        assertTrue(out.contains("STRING_INTERNED"), "expected an interning event, got:\n" + out);
        assertTrue(out.contains("already in the string pool"),
                "expected the second interning to reuse the pooled instance, got:\n" + out);
    }

    @Test
    void framesAndNativeCallsGoToSeparatePerThreadStacks() {
        String out = captureTrace(() -> {
            VisualMemoryAreas jvm = new VisualMemoryAreas();
            jvm.call("main", "process");
            jvm.callNative("main", "currentTimeMillis");
            jvm.ret("main");
        });
        assertTrue(out.contains("FRAME_PUSHED"), "expected a frame-push event, got:\n" + out);
        assertTrue(out.contains("NATIVE_CALL"), "expected a native-call event, got:\n" + out);
        assertTrue(out.contains("FRAME_POPPED"), "expected a frame-pop event, got:\n" + out);
        assertTrue(out.contains("\"nativeFrames\""), "expected a native stack in the state, got:\n" + out);
    }

    @Test
    void jitCompiledCodeGoesToTheCodeCache() {
        String out = captureTrace(() -> {
            VisualMemoryAreas jvm = new VisualMemoryAreas();
            jvm.jitCompile("total");
        });
        assertTrue(out.contains("JIT_COMPILED"), "expected a JIT event, got:\n" + out);
        assertTrue(out.contains("total()"), "expected the compiled method in the code cache, got:\n" + out);
    }

    @Test
    void countReportsOneSharedAreaEachAndOneStackPerThread() {
        String out = captureTrace(() -> {
            VisualMemoryAreas jvm = new VisualMemoryAreas();
            jvm.startThread("worker-1");
            jvm.countAreas();
        });
        assertTrue(out.contains("AREA_COUNT"), "expected a counting event, got:\n" + out);
        assertTrue(out.contains("\"threadCount\":2"),
                "expected two live threads in the state, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualMemoryAreas jvm = new VisualMemoryAreas();
            jvm.loadClass("Order");
            jvm.allocate("main", "Order");
            jvm.countAreas();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
