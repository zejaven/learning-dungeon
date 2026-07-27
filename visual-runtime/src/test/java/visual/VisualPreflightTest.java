package visual;

import org.junit.jupiter.api.Test;
import visual.VisualPreflight.Api;
import visual.VisualPreflight.Call;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualPreflightTest {

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

    private static Api openApi() {
        return Api.cors().allowOrigin(PAGE)
                .allowMethods("GET", "POST", "PUT", "PATCH", "DELETE")
                .allowHeaders("Content-Type", "Authorization");
    }

    @Test
    void settingUpABrowserShowsHowTheApiAnswers() {
        String out = captureTrace(() -> VisualPreflight.browser(PAGE, API, openApi()));
        assertTrue(out.contains("PREFLIGHT_SETUP"), "expected a setup event, got:\n" + out);
        assertTrue(out.contains("Access-Control-Allow-Methods"),
                "the answer must be visible, got:\n" + out);
    }

    @Test
    void aFormStylePostIsNotPreflighted() {
        String out = captureTrace(() -> {
            VisualPreflight browser = VisualPreflight.browser(PAGE, API, openApi());
            browser.send(Call.post("/orders").formEncoded());
            browser.report();
        });
        assertTrue(out.contains("SIMPLE_CALL"), "a form post is simple, got:\n" + out);
        assertTrue(out.contains("REAL_REQUEST_SENT"), "it must be sent, got:\n" + out);
        assertFalse(out.contains("OPTIONS_SENT"), "no preflight may be sent, got:\n" + out);
        assertTrue(out.contains("preflights sent: 0"), "nothing to pay for, got:\n" + out);
    }

    @Test
    void aTextPlainPostIsNotPreflightedEither() {
        String out = captureTrace(() -> {
            VisualPreflight browser = VisualPreflight.browser(PAGE, API, openApi());
            browser.send(Call.post("/events").textPlain());
        });
        assertTrue(out.contains("SIMPLE_CALL"), "text/plain is safelisted, got:\n" + out);
    }

    @Test
    void aJsonBodyTriggersThePreflight() {
        String out = captureTrace(() -> {
            VisualPreflight browser = VisualPreflight.browser(PAGE, API, openApi());
            browser.send(Call.post("/orders").json());
            browser.report();
        });
        assertTrue(out.contains("PREFLIGHT_REQUIRED"), "application/json is not safelisted, got:\n" + out);
        assertTrue(out.contains("OPTIONS_SENT"), "the browser must ask first, got:\n" + out);
        assertTrue(out.contains("Access-Control-Request-Method"),
                "the preflight must name the method it asks about, got:\n" + out);
        assertTrue(out.contains("PREFLIGHT_APPROVED"), "the policy grants it, got:\n" + out);
        assertTrue(out.contains("REAL_REQUEST_SENT"), "the real call must follow, got:\n" + out);
        assertTrue(out.contains("round trips in total: 2"), "one call, two round trips, got:\n" + out);
    }

    @Test
    void aNonSimpleMethodTriggersThePreflightOnItsOwn() {
        String out = captureTrace(() -> {
            VisualPreflight browser = VisualPreflight.browser(PAGE, API, openApi());
            browser.send(Call.delete("/orders/42"));
        });
        assertTrue(out.contains("OPTIONS_SENT"), "DELETE is not simple, got:\n" + out);
        assertTrue(out.contains("PREFLIGHT_APPROVED"), "DELETE is allowed here, got:\n" + out);
    }

    @Test
    void aDeniedPreflightStopsTheRealRequest() {
        String out = captureTrace(() -> {
            VisualPreflight browser = VisualPreflight.browser(PAGE, API,
                    Api.cors().allowOrigin(PAGE).allowMethods("GET", "POST"));
            browser.send(Call.delete("/orders/42"));
            browser.report();
        });
        assertTrue(out.contains("PREFLIGHT_DENIED"), "DELETE is not listed, got:\n" + out);
        assertTrue(out.contains("REQUEST_NEVER_SENT"), "the real call must be stopped, got:\n" + out);
        assertFalse(out.contains("REAL_REQUEST_SENT"), "nothing may reach the API, got:\n" + out);
        assertTrue(out.contains("real requests that reached the API: 0"),
                "the handler never ran, got:\n" + out);
    }

    @Test
    void anUnlistedHeaderIsNamedInTheDenial() {
        String out = captureTrace(() -> {
            VisualPreflight browser = VisualPreflight.browser(PAGE, API,
                    Api.cors().allowOrigin(PAGE).allowMethods("POST").allowHeaders("Content-Type"));
            browser.send(Call.post("/orders").json().header("X-Request-Id", "r-1"));
        });
        assertTrue(out.contains("PREFLIGHT_DENIED"), "the header is not listed, got:\n" + out);
        assertTrue(out.contains("x-request-id"), "the offending header must be named, got:\n" + out);
    }

    @Test
    void aSafelistedMethodPassesTheMethodCheckWithoutBeingListed() {
        String out = captureTrace(() -> {
            VisualPreflight browser = VisualPreflight.browser(PAGE, API,
                    Api.cors().allowOrigin(PAGE).allowHeaders("X-Request-Id"));
            browser.send(Call.get("/orders").header("X-Request-Id", "r-1"));
        });
        assertTrue(out.contains("PREFLIGHT_APPROVED"),
                "GET is a safelisted method, so an empty Allow-Methods is still fine, got:\n" + out);
    }

    @Test
    void aSecurityFilterAnsweringOptionsBreaksEveryPreflightedCall() {
        String out = captureTrace(() -> {
            VisualPreflight browser = VisualPreflight.browser(PAGE, API,
                    openApi().authFilterBeforeCors());
            browser.send(Call.post("/orders").json());
            browser.report();
        });
        assertTrue(out.contains("OPTIONS_UNAUTHORIZED"), "the filter must answer 401, got:\n" + out);
        assertTrue(out.contains("PREFLIGHT_DENIED"), "a 401 is not permission, got:\n" + out);
        assertFalse(out.contains("REAL_REQUEST_SENT"), "the real call never leaves, got:\n" + out);
    }

    @Test
    void theWildcardHeaderListDoesNotCoverAuthorization() {
        String out = captureTrace(() -> {
            VisualPreflight browser = VisualPreflight.browser(PAGE, API,
                    Api.cors().allowOrigin(PAGE).allowMethods("GET").allowAnyHeader());
            browser.send(Call.get("/me").bearer("t0ken"));
            browser.redeploy(Api.cors().allowOrigin(PAGE).allowMethods("GET")
                    .allowHeaders("Authorization"));
            browser.send(Call.get("/me").bearer("t0ken"));
        });
        assertTrue(out.contains("PREFLIGHT_DENIED"), "* must not cover Authorization, got:\n" + out);
        assertTrue(out.contains("PREFLIGHT_APPROVED"),
                "naming the header explicitly must fix it, got:\n" + out);
    }

    @Test
    void aCredentialedCallMatchesTheWildcardLiterally() {
        String out = captureTrace(() -> {
            VisualPreflight browser = VisualPreflight.browser(PAGE, API,
                    Api.cors().allowOrigin(PAGE).allowCredentials()
                            .allowMethods("*").allowAnyHeader());
            browser.send(Call.patch("/me").json().withCredentials());
        });
        assertTrue(out.contains("PREFLIGHT_DENIED"),
                "for a credentialed call * is not a wildcard, got:\n" + out);
        assertTrue(out.contains("REQUEST_NEVER_SENT"), "nothing may be sent, got:\n" + out);
    }

    @Test
    void cookiesCannotBeCombinedWithAWildcardOrigin() {
        String out = captureTrace(() -> {
            VisualPreflight browser = VisualPreflight.browser(PAGE, API,
                    Api.cors().allowAnyOrigin().allowMethods("PATCH").allowAnyHeader());
            browser.send(Call.patch("/me").json().withCredentials());
        });
        assertTrue(out.contains("PREFLIGHT_DENIED"), "cookies plus * must be refused, got:\n" + out);
    }

    @Test
    void maxAgeLetsTheSecondIdenticalCallSkipTheHandshake() {
        String out = captureTrace(() -> {
            VisualPreflight browser = VisualPreflight.browser(PAGE, API, openApi().maxAge(600));
            browser.send(Call.post("/orders").json());
            browser.send(Call.post("/orders").json());
            browser.report();
        });
        assertTrue(out.contains("CACHE_HIT"), "the second call must reuse it, got:\n" + out);
        assertTrue(out.contains("preflights sent: 1"), "only one handshake, got:\n" + out);
        assertTrue(out.contains("round trips in total: 3"), "two calls, three round trips, got:\n" + out);
    }

    @Test
    void aDifferentMethodIsADifferentCacheEntry() {
        String out = captureTrace(() -> {
            VisualPreflight browser = VisualPreflight.browser(PAGE, API, openApi().maxAge(600));
            browser.send(Call.post("/orders").json());
            browser.send(Call.put("/orders/42").json());
            browser.report();
        });
        assertFalse(out.contains("CACHE_HIT"), "PUT is not covered by the POST answer, got:\n" + out);
        assertTrue(out.contains("preflights sent: 2"), "each combination pays once, got:\n" + out);
    }

    @Test
    void anExpiredEntryForcesAFreshHandshake() {
        String out = captureTrace(() -> {
            VisualPreflight browser = VisualPreflight.browser(PAGE, API, openApi().maxAge(60));
            browser.send(Call.post("/orders").json());
            browser.advanceSeconds(90);
            browser.send(Call.post("/orders").json());
            browser.report();
        });
        assertTrue(out.contains("CLOCK_ADVANCED"), "the clock must move, got:\n" + out);
        assertTrue(out.contains("CACHE_EXPIRED"), "the entry must expire, got:\n" + out);
        assertTrue(out.contains("preflights sent: 2"), "the browser asks again, got:\n" + out);
    }

    @Test
    void theBrowserCapsHowLongItRemembersAPreflight() {
        String out = captureTrace(() -> {
            VisualPreflight browser = VisualPreflight.browser(PAGE, API, openApi().maxAge(86400));
            browser.send(Call.post("/orders").json());
        });
        assertTrue(out.contains("7200"), "the cap must be visible, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualPreflight browser = VisualPreflight.browser(PAGE, API, Api.withoutCors());
            browser.send(Call.get("/orders"));
            browser.send(Call.post("/orders").json());
            browser.redeploy(openApi().maxAge(600));
            browser.send(Call.post("/orders").json());
            browser.send(Call.post("/orders").json());
            browser.advanceSeconds(1200);
            browser.send(Call.post("/orders").json());
            browser.report();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
