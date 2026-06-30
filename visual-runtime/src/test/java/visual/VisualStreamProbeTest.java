package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualStreamProbeTest {

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
    void emitsLoopAndStreamPipelineEvents() {
        String out = captureTrace(() -> {
            VisualStreamProbe loopProbe = new VisualStreamProbe("loop", List.of(1, 2, 3, 4));
            int loopSum = 0;
            for (int value : List.of(1, 2, 3, 4)) {
                int current = loopProbe.loopVisit(value);
                if (current % 2 == 0) {
                    loopSum += current * current;
                }
            }
            loopProbe.finishLoop(loopSum);

            VisualStreamProbe streamProbe = new VisualStreamProbe("stream", List.of(1, 2, 3, 4));
            streamProbe.pipelineDeclared("filter even -> map square -> reduce sum");
            int streamSum = List.of(1, 2, 3, 4).stream()
                    .filter(streamProbe::filterEven)
                    .map(streamProbe::mapSquare)
                    .reduce(0, streamProbe::reduceSum);
            streamProbe.finishStream(streamSum);
        });

        assertTrue(out.contains("LOOP_ITERATION"), "expected loop event, got:\n" + out);
        assertTrue(out.contains("STREAM_PIPELINE_DECLARED"), "expected pipeline declaration, got:\n" + out);
        assertTrue(out.contains("STREAM_FILTER"), "expected filter event, got:\n" + out);
        assertTrue(out.contains("STREAM_MAP"), "expected map event, got:\n" + out);
        assertTrue(out.contains("STREAM_REDUCE"), "expected reduce event, got:\n" + out);
    }

    @Test
    void emitsBoxingPrimitiveAndParallelEvents() {
        String out = captureTrace(() -> {
            VisualStreamProbe boxingProbe = new VisualStreamProbe("boxing", List.of(10, 20));
            int boxed = List.of(10, 20).stream()
                    .mapToInt(boxingProbe::unbox)
                    .sum();
            boxingProbe.finishStream(boxed);

            VisualStreamProbe primitiveProbe = new VisualStreamProbe("primitive", List.of(10, 20));
            java.util.stream.IntStream.of(10, 20)
                    .peek(primitiveProbe::primitiveVisit)
                    .sum();

            VisualStreamProbe parallelProbe = new VisualStreamProbe("parallel", List.of(1, 2, 3, 4, 5, 6));
            parallelProbe.simulateParallelSum(3);
        });

        assertTrue(out.contains("STREAM_BOXING"), "expected boxing event, got:\n" + out);
        assertTrue(out.contains("PRIMITIVE_STREAM_STEP"), "expected primitive event, got:\n" + out);
        assertTrue(out.contains("PARALLEL_SPLIT"), "expected split event, got:\n" + out);
        assertTrue(out.contains("PARALLEL_MERGE"), "expected merge event, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualStreamProbe probe = new VisualStreamProbe("short", List.of(1, 2, 3));
            probe.pipelineDeclared("filter even -> findFirst");
            int firstEven = List.of(1, 2, 3).stream()
                    .filter(probe::filterEven)
                    .findFirst()
                    .orElseThrow();
            probe.shortCircuitFound(firstEven);
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
