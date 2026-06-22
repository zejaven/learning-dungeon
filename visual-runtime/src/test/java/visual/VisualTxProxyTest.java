package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualTxProxyTest {

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
    void proxyOpensAndCommitsTransaction() {
        String out = captureTrace(() -> {
            VisualTxProxy app = new VisualTxProxy("OrderService");
            app.proxyCall("placeOrder", true)
                    .persist("order-1", "NEW")
                    .returnNormally();
        });

        assertTrue(out.contains("TX_PROXY_CREATED"), "expected proxy creation, got:\n" + out);
        assertTrue(out.contains("TX_PROXY_INTERCEPT"), "expected proxy interception, got:\n" + out);
        assertTrue(out.contains("TX_BEGIN"), "expected transaction begin, got:\n" + out);
        assertTrue(out.contains("TX_COMMIT"), "expected commit, got:\n" + out);
    }

    @Test
    void runtimeExceptionRollsBackThroughProxy() {
        String out = captureTrace(() -> {
            VisualTxProxy app = new VisualTxProxy("OrderService");
            app.proxyCall("placeOrder", true)
                    .persist("order-1", "NEW")
                    .throwRuntime("DataAccessException");
        });

        assertTrue(out.contains("TX_ROLLBACK"), "expected rollback, got:\n" + out);
    }

    @Test
    void selfInvocationBypassesProxyAndHasNoTransaction() {
        String out = captureTrace(() -> {
            VisualTxProxy app = new VisualTxProxy("UserService");
            app.proxyCall("register", false)
                    .selfInvoke("saveUser", true)
                    .persist("user-7", "Alice")
                    .returnNormally();
        });

        assertTrue(out.contains("TX_SELF_INVOCATION"), "expected self-invocation, got:\n" + out);
        assertTrue(out.contains("TX_NO_TRANSACTION"), "expected missing transaction, got:\n" + out);
    }

    @Test
    void directCallOnRawObjectHasNoProxy() {
        String out = captureTrace(() -> {
            VisualTxProxy app = new VisualTxProxy("UserService");
            app.directCall("saveUser", true)
                    .persist("user-7", "Alice");
        });

        assertTrue(out.contains("TX_DIRECT_CALL"), "expected direct call, got:\n" + out);
        assertTrue(out.contains("TX_NO_TRANSACTION"), "expected missing transaction, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualTxProxy app = new VisualTxProxy("OrderService");
            app.proxyCall("placeOrder", true)
                    .persist("order-1", "NEW")
                    .returnNormally();
        });

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
