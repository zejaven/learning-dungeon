package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualProxyFactoryTest {

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
    void interfaceBeanGetsJdkProxy() {
        String out = captureTrace(() -> new VisualProxyFactory("PaymentService")
                .implementsInterface("PaymentApi")
                .method("pay")
                .createProxy()
                .invoke("pay"));

        assertTrue(out.contains("PROXY_JDK_SELECTED"), "expected JDK selection, got:\n" + out);
        assertTrue(out.contains("PROXY_INVOKE"), "expected an invoke event, got:\n" + out);
        assertTrue(out.contains("PROXY_DELEGATE"), "expected delegation to target, got:\n" + out);
        assertFalse(out.contains("PROXY_CGLIB_SELECTED"), "should not pick CGLIB:\n" + out);
    }

    @Test
    void noInterfaceBeanGetsCglibProxy() {
        String out = captureTrace(() -> new VisualProxyFactory("ReportService")
                .method("generate")
                .createProxy()
                .invoke("generate"));

        assertTrue(out.contains("PROXY_CGLIB_SELECTED"), "expected CGLIB selection, got:\n" + out);
        assertTrue(out.contains("PROXY_ADVICE"), "expected advice to run, got:\n" + out);
    }

    @Test
    void proxyTargetClassForcesCglibEvenWithInterface() {
        String out = captureTrace(() -> new VisualProxyFactory("PaymentService")
                .implementsInterface("PaymentApi")
                .method("pay")
                .proxyTargetClass(true)
                .createProxy());

        assertTrue(out.contains("PROXY_CGLIB_SELECTED"), "expected forced CGLIB, got:\n" + out);
        assertFalse(out.contains("PROXY_JDK_SELECTED"), "should not pick JDK when forced:\n" + out);
    }

    @Test
    void finalMethodIsNotInterceptedUnderCglib() {
        String out = captureTrace(() -> new VisualProxyFactory("ReportService")
                .finalMethod("generate")
                .createProxy()
                .invoke("generate"));

        assertTrue(out.contains("PROXY_FINAL_METHOD_SKIPPED"), "expected final method skip, got:\n" + out);
        assertFalse(out.contains("PROXY_ADVICE"), "final method must run unadvised:\n" + out);
    }

    @Test
    void cglibCannotSubclassFinalClass() {
        String out = captureTrace(() -> new VisualProxyFactory("ReportService")
                .finalClass()
                .method("generate")
                .createProxy());

        assertTrue(out.contains("PROXY_FINAL_CLASS_BLOCKED"), "expected blocked creation, got:\n" + out);
        assertFalse(out.contains("PROXY_CREATED"), "proxy must not be created for a final class:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> new VisualProxyFactory("PaymentService")
                .implementsInterface("PaymentApi")
                .method("pay")
                .createProxy()
                .invoke("pay"));

        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
