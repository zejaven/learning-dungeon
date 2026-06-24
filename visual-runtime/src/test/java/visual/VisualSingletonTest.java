package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualSingletonTest {

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
    void emitsDuplicateEventForUnsafeLazyRace() {
        String out = captureTrace(() -> {
            VisualSingleton singleton = VisualSingleton.unsafeLazy("Config");
            singleton.unsafeRace("T1", "T2");
            assertEquals(2, singleton.constructorCalls());
        });

        assertTrue(out.contains("SINGLETON_DUPLICATE_CREATED"),
                "expected duplicate creation event, got:\n" + out);
    }

    @Test
    void synchronizedAccessCreatesOnceAndReuses() {
        VisualSingleton singleton = VisualSingleton.synchronizedLazy("Config");
        singleton.synchronizedGet("T1");
        singleton.synchronizedGet("T2");

        assertEquals(1, singleton.constructorCalls());
    }

    @Test
    void doubleCheckedLockingPublishesThroughVolatile() {
        String out = captureTrace(() -> {
            VisualSingleton singleton = VisualSingleton.doubleCheckedLocking("Registry");
            singleton.doubleCheckedGet("T1");
            singleton.doubleCheckedGet("T2");
        });

        assertTrue(out.contains("SINGLETON_DCL_SECOND_CHECK"),
                "expected second check event, got:\n" + out);
        assertTrue(out.contains("SINGLETON_VOLATILE_PUBLISH"),
                "expected volatile publish event, got:\n" + out);
        assertTrue(out.contains("SINGLETON_INSTANCE_REUSED"),
                "expected reuse event, got:\n" + out);
    }

    @Test
    void enumSingletonIsReadyBeforeAccess() {
        String out = captureTrace(() -> {
            VisualSingleton singleton = VisualSingleton.enumSingleton("ConfigEnum");
            singleton.enumAccess("T1");
            singleton.enumAccess("T2");
        });

        assertTrue(out.contains("SINGLETON_ENUM_READY"),
                "expected enum ready event, got:\n" + out);
        assertTrue(out.contains("SINGLETON_ENUM_ACCESS"),
                "expected enum access event, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualSingleton singleton = VisualSingleton.doubleCheckedLocking("Registry");
            singleton.doubleCheckedGet("T1");
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
