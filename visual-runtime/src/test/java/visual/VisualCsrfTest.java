package visual;

import org.junit.jupiter.api.Test;
import visual.VisualCsrf.Delivery;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualCsrfTest {

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
    void creatingABankEmitsTheSetupEvent() {
        String out = captureTrace(VisualCsrf::bank);
        assertTrue(out.contains("CSRF_SETUP"), "expected a setup event, got:\n" + out);
        assertTrue(out.contains(VisualCsrf.COOKIE_NAME),
                "the session cookie must be visible, got:\n" + out);
    }

    @Test
    void aCrossSiteFormPostMovesMoneyWithTheUsersOwnSession() {
        String out = captureTrace(() -> {
            VisualCsrf bank = VisualCsrf.bank();
            bank.crossSiteAttempt(Delivery.AUTO_FORM, 1000);
            bank.report();
        });
        assertTrue(out.contains("COOKIE_ATTACHED"), "the cookie must ride along, got:\n" + out);
        assertTrue(out.contains("SESSION_ACCEPTED"), "the session must be valid, got:\n" + out);
        assertTrue(out.contains("FORGED_ACTION"), "the transfer must go through, got:\n" + out);
        assertTrue(out.contains("changed state, 1"), "exactly one forged change, got:\n" + out);
    }

    @Test
    void theAttackerCannotReadTheResponseAndDoesNotNeedTo() {
        String out = captureTrace(() -> {
            VisualCsrf bank = VisualCsrf.bank();
            bank.crossSiteAttempt(Delivery.AUTO_FORM, 1000);
            bank.report();
        });
        assertTrue(out.contains("RESPONSE_UNREADABLE"),
                "the same-origin policy must hide the answer, got:\n" + out);
        assertTrue(out.contains("FORGED_ACTION"),
                "and the side effect must still have happened, got:\n" + out);
    }

    @Test
    void theUsersOwnRequestIsNotCountedAsForged() {
        String out = captureTrace(() -> {
            VisualCsrf bank = VisualCsrf.bank();
            bank.userTransfers(200);
            bank.report();
        });
        assertTrue(out.contains("ACTION_PERFORMED"), "the legit transfer must pass, got:\n" + out);
        assertFalse(out.contains("FORGED_ACTION"), "nothing was forged, got:\n" + out);
        assertFalse(out.contains("RESPONSE_UNREADABLE"),
                "a same-origin page reads its own response, got:\n" + out);
    }

    @Test
    void anImageTagIsEnoughWhenAGetChangesState() {
        String out = captureTrace(() -> {
            VisualCsrf bank = VisualCsrf.bank();
            bank.crossSiteAttempt(Delivery.IMAGE_TAG, 750);
            bank.report();
        });
        assertTrue(out.contains("UNSAFE_GET"), "the unsafe method must be called out, got:\n" + out);
        assertTrue(out.contains("FORGED_ACTION"), "no click was needed, got:\n" + out);
    }

    @Test
    void postOnlyClosesTheImageTagAndNotTheForm() {
        String out = captureTrace(() -> {
            VisualCsrf bank = VisualCsrf.bank().postOnly();
            bank.crossSiteAttempt(Delivery.IMAGE_TAG, 750);
            bank.report();
        });
        assertTrue(out.contains("REQUEST_REJECTED"), "a GET must not change state, got:\n" + out);
        assertFalse(out.contains("FORGED_ACTION"), "nothing may move, got:\n" + out);

        String form = captureTrace(() -> {
            VisualCsrf bank = VisualCsrf.bank().postOnly();
            bank.crossSiteAttempt(Delivery.AUTO_FORM, 750);
            bank.report();
        });
        assertTrue(form.contains("FORGED_ACTION"),
                "POST-only is not a CSRF defence on its own, got:\n" + form);
    }

    @Test
    void sameSiteLaxBlocksTheFormPostButNotAClickedLink() {
        String post = captureTrace(() -> {
            VisualCsrf bank = VisualCsrf.bank().sameSite("Lax");
            bank.crossSiteAttempt(Delivery.AUTO_FORM, 1000);
            bank.report();
        });
        assertTrue(post.contains("COOKIE_WITHHELD"), "Lax must withhold the cookie, got:\n" + post);
        assertTrue(post.contains("REQUEST_REJECTED"), "the bank must see a stranger, got:\n" + post);
        assertFalse(post.contains("FORGED_ACTION"), "nothing may move, got:\n" + post);

        String link = captureTrace(() -> {
            VisualCsrf bank = VisualCsrf.bank().sameSite("Lax");
            bank.crossSiteAttempt(Delivery.LINK_CLICK, 1000);
            bank.report();
        });
        assertTrue(link.contains("COOKIE_ATTACHED"),
                "a top-level GET still carries the cookie, got:\n" + link);
        assertTrue(link.contains("SAMESITE_GAP"), "the gap must be named, got:\n" + link);
        assertTrue(link.contains("FORGED_ACTION"), "the attack still works, got:\n" + link);
    }

    @Test
    void sameSiteStrictAlsoBlocksTheClickedLink() {
        String out = captureTrace(() -> {
            VisualCsrf bank = VisualCsrf.bank().sameSite("Strict");
            bank.crossSiteAttempt(Delivery.LINK_CLICK, 1000);
            bank.report();
        });
        assertTrue(out.contains("COOKIE_WITHHELD"), "Strict withholds everything, got:\n" + out);
        assertFalse(out.contains("SAMESITE_GAP"), "there is no gap under Strict, got:\n" + out);
        assertFalse(out.contains("FORGED_ACTION"), "nothing may move, got:\n" + out);
    }

    @Test
    void aSynchronizerTokenBlocksTheForgeryAndNotTheUser() {
        String out = captureTrace(() -> {
            VisualCsrf bank = VisualCsrf.bank().csrfToken();
            bank.userTransfers(200);
            bank.crossSiteAttempt(Delivery.AUTO_FORM, 1000);
            bank.report();
        });
        assertTrue(out.contains("TOKEN_VALIDATED"), "the user's own page passes, got:\n" + out);
        assertTrue(out.contains("ACTION_PERFORMED"), "the legit transfer must pass, got:\n" + out);
        assertTrue(out.contains("TOKEN_MISSING"), "the forgery has no token, got:\n" + out);
        assertFalse(out.contains("FORGED_ACTION"), "nothing may be forged, got:\n" + out);
    }

    @Test
    void anOriginCheckRefusesAnAuthenticatedRequestFromAnotherSite() {
        String out = captureTrace(() -> {
            VisualCsrf bank = VisualCsrf.bank().checkOrigin();
            bank.crossSiteAttempt(Delivery.AUTO_FORM, 1000);
            bank.report();
        });
        assertTrue(out.contains("SESSION_ACCEPTED"),
                "the session is still valid, got:\n" + out);
        assertTrue(out.contains("ORIGIN_REJECTED"), "the origin must be refused, got:\n" + out);
        assertFalse(out.contains("FORGED_ACTION"), "nothing may move, got:\n" + out);
    }

    @Test
    void aJsonContentTypeIsPreflightedAndNeverLeavesTheBrowser() {
        String out = captureTrace(() -> {
            VisualCsrf bank = VisualCsrf.bank();
            bank.crossSiteAttempt(Delivery.FETCH_JSON, 1000);
            bank.report();
        });
        assertTrue(out.contains("PREFLIGHT_BLOCKED"), "the browser must ask first, got:\n" + out);
        assertFalse(out.contains("REQUEST_SENT"), "the real request never goes, got:\n" + out);
        assertFalse(out.contains("FORGED_ACTION"), "nothing may move, got:\n" + out);
    }

    @Test
    void reflectingAnyOriginWithCredentialsUndoesThePreflight() {
        String out = captureTrace(() -> {
            VisualCsrf bank = VisualCsrf.bank().corsReflectsAnyOrigin();
            bank.crossSiteAttempt(Delivery.FETCH_JSON, 1000);
            bank.report();
        });
        assertTrue(out.contains("CORS_MISCONFIGURED"), "the misconfiguration must show, got:\n" + out);
        assertTrue(out.contains("PREFLIGHT_ALLOWED"), "the browser is waved through, got:\n" + out);
        assertTrue(out.contains("FORGED_ACTION"), "the attack now works, got:\n" + out);
    }

    @Test
    void aBearerTokenLeavesNothingToForge() {
        String out = captureTrace(() -> {
            VisualCsrf bank = VisualCsrf.bank().bearerToken();
            bank.userTransfers(200);
            bank.crossSiteAttempt(Delivery.AUTO_FORM, 1000);
            bank.report();
        });
        assertTrue(out.contains("AUTH_HEADER_ATTACHED"), "the app attaches its own, got:\n" + out);
        assertTrue(out.contains("ACTION_PERFORMED"), "the legit transfer must pass, got:\n" + out);
        assertTrue(out.contains("NO_AMBIENT_CREDENTIALS"),
                "the forgery has no identity, got:\n" + out);
        assertFalse(out.contains("FORGED_ACTION"), "nothing may be forged, got:\n" + out);
    }

    @Test
    void anInjectedScriptReadsTheTokenAndDefeatsEveryCsrfDefence() {
        String out = captureTrace(() -> {
            VisualCsrf bank = VisualCsrf.bank().csrfToken().sameSite("Strict").checkOrigin();
            bank.crossSiteAttempt(Delivery.AUTO_FORM, 1000);
            bank.injectedScriptTransfer(1000);
            bank.report();
        });
        assertTrue(out.contains("COOKIE_WITHHELD"), "the plain forgery is stopped, got:\n" + out);
        assertTrue(out.contains("TOKEN_STOLEN"), "the script reads the token, got:\n" + out);
        assertTrue(out.contains("TOKEN_VALIDATED"), "and the server accepts it, got:\n" + out);
        assertTrue(out.contains("FORGED_ACTION"), "the transfer goes through, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualCsrf bank = VisualCsrf.bank();
            bank.userTransfers(200);
            bank.crossSiteAttempt(Delivery.IMAGE_TAG, 100);
            bank.crossSiteAttempt(Delivery.LINK_CLICK, 100);
            bank.crossSiteAttempt(Delivery.AUTO_FORM, 100);
            bank.crossSiteAttempt(Delivery.FETCH_JSON, 100);
            bank.sameSite("Lax").postOnly().checkOrigin().csrfToken().corsReflectsAnyOrigin();
            bank.crossSiteAttempt(Delivery.AUTO_FORM, 100);
            bank.injectedScriptTransfer(100);
            bank.bearerToken();
            bank.crossSiteAttempt(Delivery.AUTO_FORM, 100);
            bank.report();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
