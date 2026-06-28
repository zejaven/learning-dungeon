package visual;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualResourceTest {

    @BeforeEach
    void reset() {
        VisualResource.resetForTesting();
    }

    private String captureTrace(ThrowingRunnable body) throws Exception {
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
    void emitsCloseWhenTryWithResourcesExits() throws Exception {
        String out = captureTrace(() -> {
            try (VisualResource file = VisualResource.open("file")) {
                file.use("read");
            }
        });

        assertTrue(out.contains("RESOURCE_OPENED"), "expected open event, got:\n" + out);
        assertTrue(out.contains("RESOURCE_USED"), "expected use event, got:\n" + out);
        assertTrue(out.contains("RESOURCE_CLOSED"), "expected close event, got:\n" + out);
    }

    @Test
    void emitsSuppressedWhenBodyAndCloseFail() throws Exception {
        String out = captureTrace(() -> {
            try (VisualResource file = VisualResource.openFailingClose("file")) {
                file.failDuringUse("read", "read failed");
            } catch (Exception e) {
                VisualResource.reportCaught(e);
            }
        });

        assertTrue(out.contains("RESOURCE_PRIMARY_EXCEPTION"),
                "expected primary exception event, got:\n" + out);
        assertTrue(out.contains("RESOURCE_CLOSE_FAILED"),
                "expected close failure event, got:\n" + out);
        assertTrue(out.contains("RESOURCE_SUPPRESSED_EXCEPTION"),
                "expected suppressed exception event, got:\n" + out);
    }

    @Test
    void emitsReverseCloseOrderForMultipleResources() throws Exception {
        String out = captureTrace(() -> {
            try (VisualResource connection = VisualResource.open("connection");
                 VisualResource statement = VisualResource.open("statement")) {
                statement.use("execute query");
            }
        });

        assertTrue(out.contains("RESOURCE_REVERSE_CLOSE"),
                "expected reverse close event, got:\n" + out);
        assertTrue(out.contains("\"closeSequence\":[\"statement\",\"connection\"]"),
                "expected close sequence in reverse open order, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() throws Exception {
        String out = captureTrace(() -> {
            try (VisualResource file = VisualResource.open("file")) {
                file.use("read");
            }
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
