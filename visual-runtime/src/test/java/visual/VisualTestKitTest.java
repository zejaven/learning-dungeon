package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualTestKitTest {

    private String capture(Runnable body) {
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
    void passingCaseEmitsTestEventWithPassedTrue() {
        String out = capture(() -> TestKit.expect("basic", 6, 6));
        assertTrue(out.startsWith(Trace.PREFIX), out);
        assertTrue(out.contains("\"event\":\"TEST\""), out);
        assertTrue(out.contains("\"passed\":true"), out);
        assertTrue(out.contains("\"name\":\"basic\""), out);
    }

    @Test
    void failingCaseEmitsPassedFalseWithExpectedAndActual() {
        String out = capture(() -> TestKit.expect("wrong", 6, 7));
        assertTrue(out.contains("\"passed\":false"), out);
        assertTrue(out.contains("\"expected\":\"6\""), out);
        assertTrue(out.contains("\"actual\":\"7\""), out);
    }

    @Test
    void arraysAreDeepCompared() {
        String pass = capture(() -> TestKit.expect("arr", new int[] {1, 2, 3}, new int[] {1, 2, 3}));
        assertTrue(pass.contains("\"passed\":true"), pass);
        String fail = capture(() -> TestKit.expect("arr", new int[] {1, 2}, new int[] {2, 1}));
        assertTrue(fail.contains("\"passed\":false"), fail);
    }
}
