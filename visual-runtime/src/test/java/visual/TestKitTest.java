package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestKitTest {

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

    private List<String> traceLines(String out) {
        return out.lines().filter(l -> l.startsWith(Trace.PREFIX)).toList();
    }

    @Test
    void announcesEveryCaseThenReportsEachAndCloses() {
        List<String> lines = traceLines(captureTrace(() -> TestKit.run(
                TestKit.test("one", 1, () -> 1),
                TestKit.test("two", 2, () -> 3))));

        assertEquals(4, lines.size(), lines.toString());
        assertTrue(lines.get(0).contains("\"TEST_PLAN\""), lines.get(0));
        assertTrue(lines.get(0).contains("\"names\":[\"one\",\"two\"]"), lines.get(0));
        assertTrue(lines.get(1).contains("\"name\":\"one\",\"passed\":true"), lines.get(1));
        assertTrue(lines.get(2).contains("\"name\":\"two\",\"passed\":false"), lines.get(2));
        assertTrue(lines.get(3).contains("\"TEST_END\""), lines.get(3));
    }

    @Test
    void anExceptionFailsOnlyItsOwnCase() {
        List<String> lines = traceLines(captureTrace(() -> TestKit.run(
                TestKit.test("boom", 1, () -> {
                    throw new IllegalStateException("broken");
                }),
                TestKit.test("after", 1, () -> 1))));

        assertTrue(lines.get(1).contains("\"passed\":false"), lines.get(1));
        assertTrue(lines.get(1).contains("IllegalStateException: broken"), lines.get(1));
        assertTrue(lines.get(2).contains("\"name\":\"after\",\"passed\":true"), lines.get(2));
    }

    @Test
    void aHangFailsOnlyItsOwnCase() {
        List<String> lines = traceLines(captureTrace(() -> TestKit.run(50,
                TestKit.test("hang", 1, () -> {
                    while (true) {
                        Thread.onSpinWait();
                    }
                }),
                TestKit.test("after", 1, () -> 1))));

        assertTrue(lines.get(1).contains("timed out after 50 ms"), lines.get(1));
        assertTrue(lines.get(2).contains("\"name\":\"after\",\"passed\":true"), lines.get(2));
        assertTrue(lines.get(3).contains("\"TEST_END\""), lines.get(3));
    }

    @Test
    void aThrowingToStringFailsTheCaseInsteadOfTheRun() {
        Object rude = new Object() {
            @Override
            public String toString() {
                throw new UnsupportedOperationException("no toString");
            }
        };
        List<String> lines = traceLines(captureTrace(() -> TestKit.run(
                TestKit.test("rude", 1, () -> rude),
                TestKit.test("after", 1, () -> 1))));

        assertTrue(lines.get(1).contains("\"passed\":false"), lines.get(1));
        assertTrue(lines.get(3).contains("\"TEST_END\""), lines.get(3));
    }

    @Test
    void outputWithoutANewlineDoesNotSwallowTheReport() {
        String out = captureTrace(() -> TestKit.run(TestKit.test("noisy", 1, () -> {
            System.out.print("debug");
            return 1;
        })));

        assertTrue(out.contains("debug"), out);
        assertTrue(traceLines(out).stream().anyMatch(l -> l.contains("\"name\":\"noisy\"")), out);
    }

    @Test
    void expectStillReportsASingleCase() {
        String out = captureTrace(() -> TestKit.expect("plain", 1, 1));

        assertTrue(out.contains("\"TEST\""), out);
        assertTrue(out.contains("\"name\":\"plain\",\"passed\":true"), out);
    }
}
