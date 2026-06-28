package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualEqualityTest {

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
    void emitsSymmetryBrokenWhenDirectionsDisagree() {
        String out = captureTrace(() -> {
            VisualEquality viz = new VisualEquality("symmetry");
            viz.object("p", new Object(), "p", "x=1", "y=2");
            viz.object("p3", new Object(), "p3", "x=1", "y=2", "z=7");
            viz.compare("p", "p3", true, "Point.equals");
            viz.compare("p3", "p", false, "Point3D.equals");
            viz.checkSymmetry("p", "p3");
        });

        assertTrue(out.contains("EQUALITY_SYMMETRY_BROKEN"),
                "expected a symmetry break event, got:\n" + out);
        assertTrue(out.contains("\"ru\""),
                "expected bilingual trace descriptions, got:\n" + out);
    }

    @Test
    void emitsTransitivityBrokenForClassicPointTrap() {
        String out = captureTrace(() -> {
            VisualEquality viz = new VisualEquality("transitivity");
            viz.object("a", new Object(), "a", "z=10");
            viz.object("b", new Object(), "b", "2D");
            viz.object("c", new Object(), "c", "z=20");
            viz.compare("a", "b", true, "Point3D.equals");
            viz.compare("b", "c", true, "Point.equals");
            viz.compare("a", "c", false, "Point3D.equals");
            viz.checkTransitivity("a", "b", "c");
        });

        assertTrue(out.contains("EQUALITY_TRANSITIVITY_BROKEN"),
                "expected a transitivity break event, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualEquality viz = new VisualEquality("getClass");
            viz.object("p", new Object(), "p");
            viz.object("p3", new Object(), "p3");
            viz.compareWithGetClass("p", "p3", false, "Point.equals");
            viz.collectionProbe("set.contains(p3)", false);
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
