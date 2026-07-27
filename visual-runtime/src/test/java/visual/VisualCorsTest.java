package visual;

import org.junit.jupiter.api.Test;
import visual.VisualCors.Policy;
import visual.VisualCors.Request;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualCorsTest {

    private static final String PAGE = "https://app.example.com";
    private static final String API = "https://api.example.com";

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
    void settingUpABrowserEmitsThePolicy() {
        String out = captureTrace(() ->
                VisualCors.browser(PAGE, API, Policy.allowOrigin(PAGE)));
        assertTrue(out.contains("CORS_SETUP"), "expected a setup event, got:\n" + out);
        assertTrue(out.contains("Access-Control-Allow-Origin"),
                "the policy must be visible, got:\n" + out);
    }

    @Test
    void aSameOriginCallNeverInvolvesCors() {
        String out = captureTrace(() -> {
            VisualCors cors = VisualCors.browser(PAGE, PAGE, Policy.none());
            cors.send(Request.get("/orders"));
            cors.report();
        });
        assertTrue(out.contains("SAME_ORIGIN_OK"), "expected the same-origin event, got:\n" + out);
        assertFalse(out.contains("RESPONSE_BLOCKED"),
                "a same-origin response is never blocked, got:\n" + out);
        assertTrue(out.contains("blocked by the browser: 0"), "nothing may be blocked, got:\n" + out);
    }

    @Test
    void aDifferentPortIsADifferentOrigin() {
        String out = captureTrace(() -> {
            VisualCors cors = VisualCors.browser(PAGE, PAGE + ":8443", Policy.none());
            cors.send(Request.get("/orders"));
            cors.report();
        });
        assertTrue(out.contains("RESPONSE_BLOCKED"),
                "same host, different port is still cross-origin, got:\n" + out);
        assertFalse(out.contains("SAME_ORIGIN_OK"), "this is not the same origin, got:\n" + out);
    }

    @Test
    void aSimpleRequestIsDeliveredEvenWhenTheResponseIsBlocked() {
        String out = captureTrace(() -> {
            VisualCors cors = VisualCors.browser(PAGE, API, Policy.none());
            cors.send(Request.post("/orders").formEncoded());
            cors.report();
        });
        assertTrue(out.contains("SIMPLE_REQUEST"), "a form post needs no preflight, got:\n" + out);
        assertTrue(out.contains("REQUEST_DELIVERED"), "the request must reach the server, got:\n" + out);
        assertTrue(out.contains("RESPONSE_BLOCKED"), "the response must be blocked, got:\n" + out);
        assertTrue(out.contains("SERVER_ALREADY_RAN"), "the side effect must be reported, got:\n" + out);
        assertTrue(out.contains("reached the server: 1"), "the handler ran once, got:\n" + out);
    }

    @Test
    void allowOriginMakesTheResponseReadable() {
        String out = captureTrace(() -> {
            VisualCors cors = VisualCors.browser(PAGE, API, Policy.allowOrigin(PAGE));
            cors.send(Request.get("/orders"));
            cors.report();
        });
        assertTrue(out.contains("RESPONSE_READABLE"), "expected a readable response, got:\n" + out);
        assertFalse(out.contains("RESPONSE_BLOCKED"), "the origin is allowed, got:\n" + out);
        assertFalse(out.contains("PREFLIGHT_SENT"), "a plain GET does not preflight, got:\n" + out);
    }

    @Test
    void anOriginThatDoesNotMatchIsStillBlocked() {
        String out = captureTrace(() -> {
            VisualCors cors = VisualCors.browser(PAGE, API,
                    Policy.allowOrigin("https://admin.example.com"));
            cors.send(Request.get("/orders"));
            cors.report();
        });
        assertTrue(out.contains("RESPONSE_BLOCKED"), "a mismatched origin is blocked, got:\n" + out);
        assertTrue(out.contains("https://admin.example.com"),
                "the configured origin must be named, got:\n" + out);
    }

    @Test
    void jsonContentTypeTriggersAPreflight() {
        String out = captureTrace(() -> {
            VisualCors cors = VisualCors.browser(PAGE, API,
                    Policy.allowOrigin(PAGE).allowMethods("GET", "POST").allowHeaders("Content-Type"));
            cors.send(Request.post("/orders").json());
            cors.report();
        });
        assertTrue(out.contains("PREFLIGHT_SENT"), "application/json is not safelisted, got:\n" + out);
        assertTrue(out.contains("PREFLIGHT_ALLOWED"), "the policy grants it, got:\n" + out);
        assertTrue(out.contains("RESPONSE_READABLE"), "the real request must succeed, got:\n" + out);
        assertTrue(out.contains("preflights sent: 1"), "exactly one preflight, got:\n" + out);
    }

    @Test
    void anUnlistedMethodFailsThePreflightBeforeTheServerRuns() {
        String out = captureTrace(() -> {
            VisualCors cors = VisualCors.browser(PAGE, API,
                    Policy.allowOrigin(PAGE).allowMethods("GET", "POST"));
            cors.send(Request.delete("/orders/42"));
            cors.report();
        });
        assertTrue(out.contains("PREFLIGHT_BLOCKED"), "DELETE is not allowed, got:\n" + out);
        assertFalse(out.contains("REQUEST_DELIVERED"),
                "a failed preflight must stop the real request, got:\n" + out);
        assertFalse(out.contains("SERVER_ALREADY_RAN"), "nothing reached the handler, got:\n" + out);
        assertTrue(out.contains("reached the server: 0"), "the handler never ran, got:\n" + out);
    }

    @Test
    void anUnlistedHeaderFailsThePreflight() {
        String out = captureTrace(() -> {
            VisualCors cors = VisualCors.browser(PAGE, API,
                    Policy.allowOrigin(PAGE).allowMethods("GET", "POST").allowHeaders("Content-Type"));
            cors.send(Request.post("/orders").json().header("Authorization", "Bearer t0ken"));
            cors.report();
        });
        assertTrue(out.contains("PREFLIGHT_BLOCKED"), "Authorization is not allowed, got:\n" + out);
        assertTrue(out.contains("Authorization"), "the offending header must be named, got:\n" + out);
        assertTrue(out.contains("reached the server: 0"), "the handler never ran, got:\n" + out);
    }

    @Test
    void maxAgeLetsTheBrowserSkipTheSecondPreflight() {
        String out = captureTrace(() -> {
            VisualCors cors = VisualCors.browser(PAGE, API, Policy.allowOrigin(PAGE)
                    .allowMethods("GET", "POST").allowHeaders("Content-Type").maxAge(600));
            cors.send(Request.post("/orders").json());
            cors.send(Request.post("/orders").json());
            cors.report();
        });
        assertTrue(out.contains("PREFLIGHT_CACHED"), "the second call must reuse it, got:\n" + out);
        assertTrue(out.contains("preflights sent: 1"), "only one round trip, got:\n" + out);
        assertTrue(out.contains("Max-Age: 1"), "one preflight must have been saved, got:\n" + out);
    }

    @Test
    void cookiesAndAWildcardOriginCannotBeCombined() {
        String out = captureTrace(() -> {
            VisualCors cors = VisualCors.browser(PAGE, API, Policy.allowAnyOrigin());
            cors.send(Request.get("/me").withCredentials());
            cors.report();
        });
        assertTrue(out.contains("CREDENTIALS_BLOCKED"), "the wildcard must be refused, got:\n" + out);
        assertTrue(out.contains("SERVER_ALREADY_RAN"), "the request was still sent, got:\n" + out);
    }

    @Test
    void cookiesNeedAnExplicitOriginAndAllowCredentials() {
        String out = captureTrace(() -> {
            VisualCors cors = VisualCors.browser(PAGE, API, Policy.allowOrigin(PAGE));
            cors.send(Request.get("/me").withCredentials());
            cors.reconfigure(Policy.allowOrigin(PAGE).allowCredentials());
            cors.send(Request.get("/me").withCredentials());
            cors.report();
        });
        assertTrue(out.contains("CREDENTIALS_BLOCKED"),
                "the missing Allow-Credentials must block, got:\n" + out);
        assertTrue(out.contains("RESPONSE_READABLE"),
                "the fixed policy must let it through, got:\n" + out);
        assertTrue(out.contains("blocked by the browser: 1"), "exactly one block, got:\n" + out);
    }

    @Test
    void aResponseHeaderStaysHiddenUntilItIsExposed() {
        String out = captureTrace(() -> {
            VisualCors cors = VisualCors.browser(PAGE, API, Policy.allowOrigin(PAGE));
            cors.send(Request.get("/orders").readsHeader("X-Total-Count"));
            cors.reconfigure(Policy.allowOrigin(PAGE).exposeHeaders("X-Total-Count"));
            cors.send(Request.get("/orders").readsHeader("X-Total-Count"));
            cors.report();
        });
        assertTrue(out.contains("HEADER_HIDDEN"), "the header must start hidden, got:\n" + out);
        assertTrue(out.contains("Access-Control-Expose-Headers"),
                "the fix must be named, got:\n" + out);
        assertTrue(out.contains("blocked by the browser: 0"),
                "a hidden header is not a blocked response, got:\n" + out);
    }

    @Test
    void aSafelistedResponseHeaderNeedsNoExposeHeaders() {
        String out = captureTrace(() -> {
            VisualCors cors = VisualCors.browser(PAGE, API, Policy.allowOrigin(PAGE));
            cors.send(Request.get("/orders").readsHeader("Content-Type"));
            cors.report();
        });
        assertFalse(out.contains("HEADER_HIDDEN"), "Content-Type is safelisted, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualCors cors = VisualCors.browser(PAGE, API, Policy.none());
            cors.send(Request.get("/orders"));
            cors.send(Request.put("/orders/42").json());
            cors.reconfigure(Policy.allowOrigin(PAGE)
                    .allowMethods("GET", "PUT").allowHeaders("Content-Type").maxAge(600));
            cors.send(Request.put("/orders/42").json());
            cors.send(Request.put("/orders/42").json());
            cors.send(Request.get("/me").withCredentials());
            cors.send(Request.get("/orders").readsHeader("X-Total-Count"));
            cors.report();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
