package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualAnonymousClassTest {

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
    void emitsCreatedEventForAnonymousRuntimeClass() {
        String out = captureTrace(() -> {
            VisualAnonymousClass visual = new VisualAnonymousClass("Runnable");
            visual.target("interface", "run()");
            Runnable task = new Runnable() {
                @Override
                public void run() {
                    // No-op for the trace.
                }
            };
            visual.created("task", task);
        });

        assertTrue(out.contains("ANON_CLASS_CREATED"), "expected creation event, got:\n" + out);
        assertTrue(out.contains("\"anonymousClass\":true"), "expected anonymousClass=true, got:\n" + out);
        assertTrue(out.contains("Runnable"), "expected target type in state, got:\n" + out);
    }

    @Test
    void emitsCapturedLocalEvent() {
        String out = captureTrace(() -> {
            VisualAnonymousClass visual = new VisualAnonymousClass("Supplier");
            visual.target("interface", "get()");
            String prefix = "order";
            visual.captured("prefix", prefix);
        });

        assertTrue(out.contains("ANON_LOCAL_CAPTURED"), "expected capture event, got:\n" + out);
        assertTrue(out.contains("\"name\":\"prefix\""), "expected captured variable name, got:\n" + out);
        assertTrue(out.contains("\"value\":\"order\""), "expected captured variable value, got:\n" + out);
    }

    @Test
    void emitsMethodCallAndHandoffEvents() {
        String out = captureTrace(() -> {
            VisualAnonymousClass visual = new VisualAnonymousClass("ClickListener");
            visual.target("interface", "onClick(String)");
            visual.passed("register(button, listener)", "listener");
            visual.called("onClick(String)", "saved");
        });

        assertTrue(out.contains("ANON_OBJECT_PASSED"), "expected handoff event, got:\n" + out);
        assertTrue(out.contains("ANON_METHOD_CALLED"), "expected method call event, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualAnonymousClass visual = new VisualAnonymousClass("Runnable");
            visual.target("interface", "run()");
            visual.called("run()", "done");
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
