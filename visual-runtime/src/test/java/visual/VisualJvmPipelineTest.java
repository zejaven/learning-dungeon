package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualJvmPipelineTest {

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
    void emitsSourceAndCompilationEvents() {
        String out = captureTrace(() -> {
            VisualJvmPipeline pipeline = new VisualJvmPipeline("OrderService");
            pipeline.tryRunSource();
            pipeline.compile();
        });

        assertTrue(out.contains("JVM_SOURCE_CREATED"), out);
        assertTrue(out.contains("JVM_SOURCE_REJECTED"), out);
        assertTrue(out.contains("JAVAC_COMPILED"), out);
        assertTrue(out.contains("OrderService.class"), out);
    }

    @Test
    void emitsLoadVerifyInitializeAndInterpretEvents() {
        String out = captureTrace(() -> {
            VisualJvmPipeline pipeline = new VisualJvmPipeline("BillingJob");
            pipeline.compile();
            pipeline.load();
            pipeline.verify();
            pipeline.initialize();
            pipeline.interpret("main");
            pipeline.print("done");
        });

        assertTrue(out.contains("BYTECODE_LOADED"), out);
        assertTrue(out.contains("BYTECODE_VERIFIED"), out);
        assertTrue(out.contains("CLASS_INITIALIZED"), out);
        assertTrue(out.contains("BYTECODE_INTERPRETED"), out);
        assertTrue(out.contains("PROGRAM_OUTPUT"), out);
    }

    @Test
    void emitsJitEventWhenMethodGetsHot() {
        String out = captureTrace(() -> {
            VisualJvmPipeline pipeline = new VisualJvmPipeline("PriceCalculator");
            pipeline.compile();
            pipeline.load();
            pipeline.verify();
            pipeline.initialize();
            pipeline.callHotMethod("total", 12);
        });

        assertTrue(out.contains("METHOD_JIT_COMPILED"), out);
        assertTrue(out.contains("\"nativeCompiled\":true"), out);
        assertTrue(out.contains("Code Cache"), out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualJvmPipeline pipeline = new VisualJvmPipeline("Main");
            pipeline.compile();
            pipeline.load();
            pipeline.verify();
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX),
                        "unexpected non-trace line: " + line);
            }
        });
    }
}
