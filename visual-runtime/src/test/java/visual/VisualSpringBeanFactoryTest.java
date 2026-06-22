package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualSpringBeanFactoryTest {

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
    void emitsCycleWhenConstructorGraphLoopsBack() {
        String out = captureTrace(() -> {
            VisualSpringBeanFactory context = new VisualSpringBeanFactory("app");
            context.bean("A").dependsOn("B");
            context.bean("B").dependsOn("C");
            context.bean("C").dependsOn("A");

            assertFalse(context.refresh());
        });

        assertTrue(out.contains("SPRING_CYCLE_DETECTED"),
                "expected a constructor cycle event, got:\n" + out);
        assertTrue(out.contains("SPRING_CONTEXT_FAILED"),
                "expected a context failure event, got:\n" + out);
    }

    @Test
    void lazyDependencyDefersTheBackReferenceUntilAfterStartup() {
        String out = captureTrace(() -> {
            VisualSpringBeanFactory context = new VisualSpringBeanFactory("app");
            context.bean("A").dependsOn("B");
            context.bean("B").dependsOn("C");
            context.bean("C").lazyDependsOn("A");

            assertTrue(context.refresh());
            assertTrue(context.useLazy("C", "A"));
        });

        assertTrue(out.contains("SPRING_LAZY_PROXY_INJECTED"),
                "expected @Lazy proxy injection, got:\n" + out);
        assertTrue(out.contains("SPRING_CONTEXT_READY"),
                "expected the context to start, got:\n" + out);
        assertTrue(out.contains("SPRING_LAZY_TARGET_RESOLVED"),
                "expected lazy target resolution, got:\n" + out);
        assertFalse(out.contains("SPRING_CYCLE_DETECTED"),
                "lazy graph should not need eager cycle resolution:\n" + out);
    }

    @Test
    void providerDependencyDefersLookupUntilExplicitRequest() {
        String out = captureTrace(() -> {
            VisualSpringBeanFactory context = new VisualSpringBeanFactory("app");
            context.bean("A").dependsOn("B");
            context.bean("B").dependsOn("C");
            context.bean("C").providerDependsOn("A");

            assertTrue(context.refresh());
            assertTrue(context.requestProvider("C", "A"));
        });

        assertTrue(out.contains("SPRING_PROVIDER_INJECTED"),
                "expected provider injection, got:\n" + out);
        assertTrue(out.contains("SPRING_PROVIDER_REQUESTED"),
                "expected provider lookup request, got:\n" + out);
        assertTrue(out.contains("SPRING_PROVIDER_TARGET_RETURNED"),
                "expected provider target return, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualSpringBeanFactory context = new VisualSpringBeanFactory("app");
            context.bean("OrderService").dependsOn("OrderRepository");
            context.bean("OrderRepository");
            context.refresh();
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
