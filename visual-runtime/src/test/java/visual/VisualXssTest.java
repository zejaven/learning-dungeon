package visual;

import org.junit.jupiter.api.Test;
import visual.VisualXss.Sink;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualXssTest {

    private static final String SCRIPT_PAYLOAD = "<script>steal(document.cookie)</script>";
    private static final String IMG_PAYLOAD = "<img src=x onerror=steal(document.cookie)>";

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
    void creatingASiteEmitsTheSetupEvent() {
        String out = captureTrace(VisualXss::site);
        assertTrue(out.contains("XSS_SETUP"), "expected a setup event, got:\n" + out);
        assertTrue(out.contains(VisualXss.COOKIE_NAME), "the session cookie must be visible, got:\n" + out);
    }

    @Test
    void anUnescapedReflectedPayloadExecutesAndStealsTheCookie() {
        String out = captureTrace(() -> {
            VisualXss site = VisualXss.site();
            site.reflect(Sink.HTML_TEXT, SCRIPT_PAYLOAD);
            site.report();
        });
        assertTrue(out.contains("SCRIPT_INJECTED"), "the payload must become markup, got:\n" + out);
        assertTrue(out.contains("SCRIPT_EXECUTED"), "the script must run, got:\n" + out);
        assertTrue(out.contains("COOKIE_STOLEN"), "the cookie must be readable, got:\n" + out);
        assertTrue(out.contains("cookie was read 1"), "exactly one theft, got:\n" + out);
    }

    @Test
    void harmlessInputIsNotReportedAsAnInjection() {
        String out = captureTrace(() -> {
            VisualXss site = VisualXss.site();
            site.reflect(Sink.HTML_TEXT, "hiking boots");
            site.report();
        });
        assertTrue(out.contains("RENDERED_AS_TEXT"), "expected a plain render, got:\n" + out);
        assertFalse(out.contains("SCRIPT_INJECTED"), "nothing was injected, got:\n" + out);
        assertTrue(out.contains("a script actually ran 0"), "nothing ran, got:\n" + out);
    }

    @Test
    void contextAwareEncodingTurnsThePayloadIntoText() {
        String out = captureTrace(() -> {
            VisualXss site = VisualXss.site().encodeForContext();
            site.reflect(Sink.HTML_TEXT, SCRIPT_PAYLOAD);
            site.report();
        });
        assertTrue(out.contains("INPUT_ENCODED"), "the encoder must run, got:\n" + out);
        assertTrue(out.contains("RENDERED_AS_TEXT"), "the payload must be inert, got:\n" + out);
        assertFalse(out.contains("SCRIPT_EXECUTED"), "nothing may execute, got:\n" + out);
        assertTrue(out.contains("&lt;script&gt;"), "the tag must be entity-encoded, got:\n" + out);
    }

    @Test
    void anEventHandlerAttributeIsJustAsExecutableAsAScriptTag() {
        String out = captureTrace(() -> {
            VisualXss site = VisualXss.site();
            site.reflect(Sink.HTML_TEXT, IMG_PAYLOAD);
            site.report();
        });
        assertTrue(out.contains("SCRIPT_EXECUTED"),
                "an onerror handler needs no script tag, got:\n" + out);
    }

    @Test
    void storedInputIsServedToEveryVisitor() {
        String out = captureTrace(() -> {
            VisualXss site = VisualXss.site();
            site.save("comment", SCRIPT_PAYLOAD);
            site.showSaved(Sink.HTML_TEXT, "comment");
            site.showSaved(Sink.HTML_TEXT, "comment");
            site.report();
        });
        assertTrue(out.contains("INPUT_STORED"), "the value must be saved, got:\n" + out);
        assertTrue(out.contains("STORED_PAYLOAD_SERVED"), "it must be served again, got:\n" + out);
        assertTrue(out.contains("a script actually ran 2"), "both visitors are hit, got:\n" + out);
    }

    @Test
    void aDomSinkIsReachedWithoutTheServerSeeingAnything() {
        String out = captureTrace(() -> {
            VisualXss site = VisualXss.site().encodeForContext();
            site.domRender("<img src=x onerror=steal(document.cookie)>");
            site.report();
        });
        assertTrue(out.contains("ENCODING_SKIPPED"),
                "server-side encoding cannot run here, got:\n" + out);
        assertTrue(out.contains("DOM_XSS"), "expected a DOM-based finding, got:\n" + out);
        assertTrue(out.contains("SCRIPT_EXECUTED"), "the payload must still run, got:\n" + out);
    }

    @Test
    void theNaiveEscaperProtectsTheBodyAndNotTheScriptBlock() {
        String out = captureTrace(() -> {
            VisualXss site = VisualXss.site().escapeAngleBrackets();
            site.reflect(Sink.HTML_TEXT, SCRIPT_PAYLOAD);
            site.reflect(Sink.SCRIPT, "';steal(document.cookie);//");
            site.report();
        });
        assertTrue(out.contains("RENDERED_AS_TEXT"), "the body case must be safe, got:\n" + out);
        assertTrue(out.contains("WRONG_CONTEXT"), "the script case must be reported, got:\n" + out);
        assertTrue(out.contains("SCRIPT_EXECUTED"), "the script case must execute, got:\n" + out);
    }

    @Test
    void aJavascriptUrlSurvivesHtmlEscapingAndIsStoppedByASchemeAllowlist() {
        String out = captureTrace(() -> {
            VisualXss site = VisualXss.site().escapeAngleBrackets();
            site.reflect(Sink.URL, "javascript:steal(document.cookie)");
            site.report();
        });
        assertTrue(out.contains("WRONG_CONTEXT"), "escaping does nothing here, got:\n" + out);
        assertTrue(out.contains("SCRIPT_EXECUTED"), "the link must be executable, got:\n" + out);

        String fixed = captureTrace(() -> {
            VisualXss site = VisualXss.site().encodeForContext();
            site.reflect(Sink.URL, "javascript:steal(document.cookie)");
            site.report();
        });
        assertTrue(fixed.contains("about:blank"), "the scheme must be rejected, got:\n" + fixed);
        assertFalse(fixed.contains("SCRIPT_EXECUTED"), "nothing may execute, got:\n" + fixed);
    }

    @Test
    void encodingInAScriptBlockUsesJavaScriptEscapesNotEntities() {
        String out = captureTrace(() -> {
            VisualXss site = VisualXss.site().encodeForContext();
            site.reflect(Sink.SCRIPT, "';steal(document.cookie);//");
            site.report();
        });
        assertTrue(out.contains("\\\\u0027"), "the quote must become a unicode escape, got:\n" + out);
        assertTrue(out.contains("RENDERED_AS_TEXT"), "the value must stay data, got:\n" + out);
        assertFalse(out.contains("SCRIPT_EXECUTED"), "nothing may execute, got:\n" + out);
    }

    @Test
    void theSanitizerKeepsFormattingAndDropsTheScript() {
        String out = captureTrace(() -> {
            VisualXss site = VisualXss.site().sanitizeRichText();
            site.save("comment", "<b>Great product</b>" + SCRIPT_PAYLOAD);
            site.showSaved(Sink.HTML_TEXT, "comment");
            site.report();
        });
        assertTrue(out.contains("INPUT_SANITIZED"), "the sanitizer must run, got:\n" + out);
        assertTrue(out.contains("<b>Great product</b>"), "the allowed markup must survive, got:\n" + out);
        assertFalse(out.contains("SCRIPT_EXECUTED"), "nothing may execute, got:\n" + out);
    }

    @Test
    void aQuotedAttributeIsSafeOnceTheQuoteIsEscaped() {
        String out = captureTrace(() -> {
            VisualXss site = VisualXss.site();
            site.reflect(Sink.ATTRIBUTE, "\" onmouseover=steal(document.cookie) x=\"");
            site.reflect(Sink.ATTRIBUTE, "\" onmouseover=steal(document.cookie) x=\"");
            site.report();
        });
        assertTrue(out.contains("SCRIPT_EXECUTED"), "an unescaped attribute breaks out, got:\n" + out);

        String fixed = captureTrace(() -> {
            VisualXss site = VisualXss.site().escapeAngleBrackets();
            site.reflect(Sink.ATTRIBUTE, "\" onmouseover=steal(document.cookie) x=\"");
            site.report();
        });
        assertTrue(fixed.contains("RENDERED_AS_TEXT"), "escaping the quote is enough, got:\n" + fixed);
    }

    @Test
    void cspStopsTheInjectedScriptWithoutStoppingTheInjection() {
        String out = captureTrace(() -> {
            VisualXss site = VisualXss.site().contentSecurityPolicy("script-src 'self'");
            site.reflect(Sink.HTML_TEXT, SCRIPT_PAYLOAD);
            site.report();
        });
        assertTrue(out.contains("SCRIPT_INJECTED"), "the injection still happens, got:\n" + out);
        assertTrue(out.contains("CSP_BLOCKED"), "the policy must refuse it, got:\n" + out);
        assertFalse(out.contains("SCRIPT_EXECUTED"), "nothing may execute, got:\n" + out);
        assertTrue(out.contains("the untrusted value became markup 1"),
                "the injection must still be counted, got:\n" + out);
    }

    @Test
    void aPolicyThatAllowsUnsafeInlineStopsNothing() {
        String out = captureTrace(() -> {
            VisualXss site = VisualXss.site().contentSecurityPolicy("script-src 'self' 'unsafe-inline'");
            site.reflect(Sink.HTML_TEXT, SCRIPT_PAYLOAD);
            site.report();
        });
        assertFalse(out.contains("CSP_BLOCKED"), "unsafe-inline permits it, got:\n" + out);
        assertTrue(out.contains("SCRIPT_EXECUTED"), "the script still runs, got:\n" + out);
    }

    @Test
    void httpOnlyHidesTheCookieButNotTheVulnerability() {
        String out = captureTrace(() -> {
            VisualXss site = VisualXss.site().httpOnlySession();
            site.reflect(Sink.HTML_TEXT, SCRIPT_PAYLOAD);
            site.report();
        });
        assertTrue(out.contains("SCRIPT_EXECUTED"), "the script still runs, got:\n" + out);
        assertTrue(out.contains("COOKIE_PROTECTED"), "the cookie must be unreadable, got:\n" + out);
        assertFalse(out.contains("COOKIE_STOLEN"), "nothing may be read, got:\n" + out);
        assertTrue(out.contains("cookie was read 0"), "no theft, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualXss site = VisualXss.site();
            site.reflect(Sink.HTML_TEXT, SCRIPT_PAYLOAD);
            site.save("comment", IMG_PAYLOAD);
            site.showSaved(Sink.HTML_TEXT, "comment");
            site.domRender(IMG_PAYLOAD);
            site.encodeForContext();
            site.reflect(Sink.SCRIPT, "';steal(1);//");
            site.reflect(Sink.URL, "javascript:steal(1)");
            site.contentSecurityPolicy("script-src 'self'");
            site.httpOnlySession();
            site.sanitizeRichText();
            site.report();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
