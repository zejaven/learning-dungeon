package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualSelfInvocationTest {

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
    void externalCallOpensAndCommitsTransaction() {
        String out = captureTrace(() -> new VisualSelfInvocation("OrderService")
                .transactional("saveOrder", "REQUIRED")
                .externalCall("saveOrder")
                .work("repository.save()")
                .ret());

        assertTrue(out.contains("SI_TX_BEGIN"), "expected transaction begin, got:\n" + out);
        assertTrue(out.contains("SI_TX_COMMIT"), "expected transaction commit, got:\n" + out);
    }

    @Test
    void selfInvocationBypassesProxyAndDropsTransaction() {
        String out = captureTrace(() -> new VisualSelfInvocation("OrderService")
                .method("placeOrder")
                .transactional("saveOrder", "REQUIRED")
                .externalCall("placeOrder")
                .selfInvoke("saveOrder")
                .work("repository.save()")
                .ret()
                .ret());

        assertTrue(out.contains("SI_SELF_INVOKE"), "expected self-invoke, got:\n" + out);
        assertTrue(out.contains("SI_TX_BYPASSED"), "expected bypass, got:\n" + out);
        assertFalse(out.contains("SI_TX_BEGIN"), "self-invocation should not begin a transaction:\n" + out);
    }

    @Test
    void requiresNewIsIgnoredOnSelfInvocation() {
        String out = captureTrace(() -> new VisualSelfInvocation("OrderService")
                .transactional("outer", "REQUIRED")
                .transactional("audit", "REQUIRES_NEW")
                .externalCall("outer")
                .selfInvoke("audit")
                .work("auditRepo.save()")
                .ret()
                .ret());

        // Only the outer transaction is opened; the inner REQUIRES_NEW is bypassed.
        assertTrue(out.contains("SI_TX_BEGIN"), "expected the outer transaction, got:\n" + out);
        assertTrue(out.contains("SI_TX_BYPASSED"), "expected REQUIRES_NEW to be bypassed, got:\n" + out);
        long begins = out.lines().filter(l -> l.contains("SI_TX_BEGIN")).count();
        assertTrue(begins == 1, "expected exactly one SI_TX_BEGIN, got " + begins + ":\n" + out);
    }

    @Test
    void proxyReentryRestoresTransaction() {
        String out = captureTrace(() -> new VisualSelfInvocation("OrderService")
                .method("placeOrder")
                .transactional("saveOrder", "REQUIRES_NEW")
                .externalCall("placeOrder")
                .proxyInvoke("saveOrder")
                .work("repository.save()")
                .ret()
                .ret());

        assertTrue(out.contains("SI_PROXY_REENTER"), "expected proxy re-entry, got:\n" + out);
        assertTrue(out.contains("SI_TX_BEGIN"), "expected the fix to open a transaction, got:\n" + out);
    }

    @Test
    void requiredJoinsAnExistingTransaction() {
        String out = captureTrace(() -> new VisualSelfInvocation("OrderService")
                .transactional("outer", "REQUIRED")
                .transactional("inner", "REQUIRED")
                .externalCall("outer")
                .proxyInvoke("inner")
                .work("repo.save()")
                .ret()
                .ret());

        assertTrue(out.contains("SI_TX_JOIN"), "expected REQUIRED to join, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> new VisualSelfInvocation("OrderService")
                .transactional("saveOrder", "REQUIRED")
                .externalCall("saveOrder")
                .work("repository.save()")
                .ret());

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
