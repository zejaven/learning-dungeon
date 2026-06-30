package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualLambdaTest {

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
    void emitsPassAndInvokeForRunnableLambda() {
        String out = captureTrace(() -> {
            VisualLambda trace = new VisualLambda("Runnable", "void run()");
            trace.created("logic", "() -> System.out.println(\"Hello\")");
            trace.passed("doSomeLogic", "logic");
            trace.invokeRunnable("logic.run()", () -> {
            });
        });

        assertTrue(out.contains("LAMBDA_TARGET_DECLARED"), "expected target event, got:\n" + out);
        assertTrue(out.contains("LAMBDA_CREATED"), "expected creation event, got:\n" + out);
        assertTrue(out.contains("LAMBDA_PASSED"), "expected pass event, got:\n" + out);
        assertTrue(out.contains("LAMBDA_INVOKED"), "expected invoke event, got:\n" + out);
    }

    @Test
    void emitsCaptureAndReturnEvents() {
        String out = captureTrace(() -> {
            VisualLambda trace = new VisualLambda("Supplier<String>", "String get()");
            trace.created("messageFactory", "() -> prefix + \" ready\"");
            trace.captured("prefix", "Order");
            trace.passed("buildMessage", "messageFactory");
            String value = trace.invokeSupplier("messageFactory.get()", () -> "Order ready");
            assertEquals("Order ready", value);
        });

        assertTrue(out.contains("LAMBDA_CAPTURED_VALUE"), "expected capture event, got:\n" + out);
        assertTrue(out.contains("LAMBDA_RETURNED_VALUE"), "expected return event, got:\n" + out);
    }

    @Test
    void emitsArgumentEventForFunctionLambda() {
        String out = captureTrace(() -> {
            VisualLambda trace = new VisualLambda("Function<Integer, String>", "String apply(Integer value)");
            trace.created("formatter", "cents -> \"$\" + cents / 100");
            trace.passed("formatPrice", "formatter");
            String value = trace.invokeFunction("formatter.apply(cents)", 250, cents -> "$" + cents / 100);
            assertEquals("$2", value);
        });

        assertTrue(out.contains("LAMBDA_ARGUMENT_SENT"), "expected argument event, got:\n" + out);
        assertTrue(out.contains("LAMBDA_RETURNED_VALUE"), "expected return event, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixedWhenBodyIsSilent() {
        String out = captureTrace(() -> {
            VisualLambda trace = new VisualLambda("Runnable", "void run()");
            trace.created("logic", "() -> doWork()");
            trace.passed("runLater", "logic");
            trace.invokeRunnable("logic.run()", () -> {
            });
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
