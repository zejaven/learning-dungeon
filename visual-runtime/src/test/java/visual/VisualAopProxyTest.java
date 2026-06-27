package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualAopProxyTest {

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
    void reportsDuplicatedCrossCuttingConcern() {
        String out = captureTrace(() -> new VisualAopProxy("OrderService")
                .duplicatedConcern("logging", "placeOrder", "cancelOrder", "refundOrder"));

        assertTrue(out.contains("AOP_DUPLICATION_FOUND"), "expected duplication event, got:\n" + out);
    }

    @Test
    void beforeAdviceRunsForMatchedPointcut() {
        String out = captureTrace(() -> new VisualAopProxy("OrderService")
                .before("LoggingAdvice", "place*")
                .call("placeOrder")
                .targetLine("saveOrder()")
                .returnNormally());

        assertTrue(out.contains("AOP_POINTCUT_MATCH"), "expected pointcut match, got:\n" + out);
        assertTrue(out.contains("AOP_ADVICE_BEFORE"), "expected before advice, got:\n" + out);
        assertTrue(out.contains("AOP_TARGET_CALL"), "expected target call, got:\n" + out);
    }

    @Test
    void aroundAdviceWrapsTarget() {
        String out = captureTrace(() -> new VisualAopProxy("CheckoutService")
                .around("TimingAdvice", "*")
                .call("checkout")
                .targetLine("chargeCard()")
                .returnNormally());

        assertTrue(out.contains("AOP_ADVICE_AROUND_START"), "expected around start, got:\n" + out);
        assertTrue(out.contains("AOP_ADVICE_AROUND_FINISH"), "expected around finish, got:\n" + out);
    }

    @Test
    void afterThrowingAdviceRunsOnException() {
        String out = captureTrace(() -> new VisualAopProxy("ShippingService")
                .afterThrowing("AlertAdvice", "ship*")
                .call("shipOrder")
                .targetLine("reserveCourier()")
                .throwException("CourierUnavailableException"));

        assertTrue(out.contains("AOP_EXCEPTION"), "expected exception event, got:\n" + out);
        assertTrue(out.contains("AOP_ADVICE_AFTER_THROWING"), "expected after-throwing advice, got:\n" + out);
    }

    @Test
    void afterAdviceRunsOnNormalReturnAndException() {
        String out = captureTrace(() -> new VisualAopProxy("OrderService")
                .after("CleanupAdvice", "*")
                .call("placeOrder")
                .targetLine("saveOrder()")
                .returnNormally()
                .call("cancelOrder")
                .targetLine("releaseReservation()")
                .throwException("CancellationFailedException"));

        long afterEvents = out.lines().filter(line -> line.contains("AOP_ADVICE_AFTER")).count();
        assertEquals(2, afterEvents, "expected after advice for success and exception, got:\n" + out);
    }

    @Test
    void directCallSkipsAdviceChain() {
        String out = captureTrace(() -> new VisualAopProxy("OrderService")
                .before("LoggingAdvice", "*")
                .directCall("placeOrder")
                .targetLine("saveOrder()")
                .returnNormally());

        assertTrue(out.contains("AOP_DIRECT_CALL"), "expected direct call, got:\n" + out);
        assertFalse(out.contains("AOP_ADVICE_BEFORE"), "direct call should not run advice:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> new VisualAopProxy("OrderService")
                .before("LoggingAdvice", "*")
                .call("placeOrder")
                .targetLine("saveOrder()")
                .returnNormally());

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
