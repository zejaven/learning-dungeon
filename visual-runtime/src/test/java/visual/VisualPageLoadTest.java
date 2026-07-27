package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualPageLoadTest {

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

    private static int count(String haystack, String needle) {
        int found = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            found++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return found;
    }

    @Test
    void whatYouTypeIsNotAUrlUntilTheBrowserFillsInTheDefaults() {
        String out = captureTrace(() -> VisualPageLoad.browser().type("google.com"));
        assertTrue(out.contains("URL_TYPED"), "the address bar step must be traced, got:\n" + out);
        assertTrue(out.contains("\"scheme\":\"https\""), "a scheme is filled in, got:\n" + out);
        assertTrue(out.contains("\"port\":443"), "the default port is implied, got:\n" + out);
        assertTrue(out.contains("\"path\":\"/\""), "a URL always has a path, got:\n" + out);
    }

    @Test
    void hstsRewritesTheSchemeLocallyWithoutSendingAnything() {
        String out = captureTrace(() -> VisualPageLoad.browser()
                .hstsPreloaded("shop.example.com")
                .type("http://shop.example.com"));
        assertTrue(out.contains("HSTS_UPGRADED"), "the upgrade must be traced, got:\n" + out);
        assertTrue(out.contains("\"roundTrips\":0"), "a local rewrite costs no round trip, got:\n" + out);
        assertTrue(out.contains("\"hsts\":true"), "the URL state must show it, got:\n" + out);
    }

    @Test
    void aColdLookupWalksRootThenTldThenAuthoritative() {
        String out = captureTrace(() -> VisualPageLoad.browser().type("www.google.com").resolve());
        assertTrue(out.contains("DNS_CACHE_MISS"), "three local caches miss first, got:\n" + out);
        assertEquals(3, count(out, "\"event\":\"DNS_QUERY\""),
                "root, TLD and authoritative — three hops, got:\n" + out);
        assertTrue(out.contains("a.root-servers.net"), "the walk starts at a root, got:\n" + out);
        assertTrue(out.contains("a.gtld-servers.net"), "the root refers to the .com servers, got:\n" + out);
        assertTrue(out.contains("ns1.google.com"), "the TLD refers to the authoritative one, got:\n" + out);
        assertTrue(out.contains("DNS_RESOLVED"), "an A record must come back, got:\n" + out);
        assertTrue(out.contains("\"dnsQueries\":3"), "three network queries, got:\n" + out);
    }

    @Test
    void everyHandshakeHappensBeforeTheRequestExists() {
        String out = captureTrace(() -> VisualPageLoad.browser()
                .type("https://www.google.com")
                .resolve()
                .connect()
                .secure()
                .request());
        int tcp = out.indexOf("TCP_HANDSHAKE");
        int tls = out.indexOf("TLS_HANDSHAKE");
        int established = out.indexOf("TLS_ESTABLISHED");
        int request = out.indexOf("HTTP_REQUEST");
        assertTrue(tcp > 0 && tls > tcp && established > tls && request > established,
                "the order must be TCP, then TLS, then the request, got:\n" + out);
        assertTrue(out.contains("SYN-ACK"), "the three-way handshake must be on the wire, got:\n" + out);
        assertTrue(out.contains("SNI=www.google.com"), "SNI carries the host name, got:\n" + out);
        assertTrue(out.contains("ALPN=[h2, http/1.1]"), "ALPN negotiates the HTTP version, got:\n" + out);
        assertTrue(out.contains("\"state\":\"SECURE\""), "the connection is encrypted, got:\n" + out);
    }

    @Test
    void aColdPageLoadCostsTenRoundTrips() {
        String out = captureTrace(() -> {
            VisualPageLoad browser = VisualPageLoad.browser();
            browser.open("www.google.com");
            browser.report();
        });
        assertTrue(out.contains("HTML_PARSED"), "the document is not the page, got:\n" + out);
        assertEquals(3, count(out, "\"event\":\"RESOURCE_FETCHED\""),
                "three subresources on the open connection, got:\n" + out);
        assertTrue(out.contains("PAGE_RENDERED"), "it has to be painted, got:\n" + out);
        // 4 DNS + 1 TCP + 1 TLS + 1 document + 3 subresources.
        assertTrue(out.contains("335 ms total, 10 round trip(s)"), "the arithmetic is fixed, got:\n" + out);
    }

    @Test
    void tlsTwelveCostsOneMoreRoundTripThanThirteen() {
        String out = captureTrace(() -> {
            VisualPageLoad browser = VisualPageLoad.browser().usingTls("TLS 1.2");
            browser.open("www.google.com");
            browser.report();
        });
        assertTrue(out.contains("ClientKeyExchange"), "TLS 1.2 needs the extra flight, got:\n" + out);
        assertTrue(out.contains("365 ms total, 11 round trip(s)"),
                "one more round trip than TLS 1.3, got:\n" + out);
    }

    @Test
    void theSecondVisitReusesTheDnsAnswerTheConnectionAndTheCache() {
        String out = captureTrace(() -> {
            VisualPageLoad browser = VisualPageLoad.browser();
            browser.open("www.google.com");
            browser.open("www.google.com");
            browser.report();
        });
        assertTrue(out.contains("DNS_CACHE_HIT"), "the name is still cached, got:\n" + out);
        assertTrue(out.contains("TCP_REUSED"), "the connection is still open, got:\n" + out);
        assertEquals(3, count(out, "\"event\":\"RESOURCE_FROM_CACHE\""),
                "every subresource comes from the HTTP cache, got:\n" + out);
        assertTrue(out.contains("105 ms total, 1 round trip(s)"),
                "only the document is fetched again, got:\n" + out);
    }

    @Test
    void aRedirectThrowsAwayTheConnectionButNotTheElapsedTime() {
        String out = captureTrace(() -> {
            VisualPageLoad browser = VisualPageLoad.browser();
            browser.type("http://shop.example.com");
            browser.resolve();
            browser.connect();
            browser.request();
            browser.redirect("https://shop.example.com/");
            browser.resolve();
            browser.connect();
            browser.secure();
            browser.request();
            browser.respond();
            browser.report();
        });
        assertTrue(out.contains("HTTP_REDIRECT"), "the 301 must be traced, got:\n" + out);
        assertTrue(out.indexOf("DNS_CACHE_HIT") > out.indexOf("HTTP_REDIRECT"),
                "the DNS answer survives the redirect, got:\n" + out);
        assertEquals(2, count(out, "\"event\":\"TCP_HANDSHAKE\""),
                "the connection to :80 cannot be reused for :443, got:\n" + out);
        assertTrue(out.contains("310 ms total, 9 round trip(s)"),
                "the redirect adds to the bill, it does not reset it, got:\n" + out);
        assertTrue(out.contains("1 redirect(s)"), "the redirect must be counted, got:\n" + out);
    }

    @Test
    void theComparisonPricesEveryStartingCondition() {
        String out = captureTrace(VisualPageLoad::compareRoundTrips);
        assertTrue(out.contains("ROUND_TRIPS_COMPARED"), "the table must be emitted, got:\n" + out);
        assertTrue(out.contains("\"scenario\":\"COLD_TLS12\",\"dnsRtt\":4,\"tcpRtt\":1,\"tlsRtt\":2"),
                "a cold TLS 1.2 load is the worst case, got:\n" + out);
        assertTrue(out.contains("\"scenario\":\"FROM_CACHE\""), "and the cache is the best, got:\n" + out);
        assertTrue(out.contains("\"totalRtt\":0,\"ms\":0"), "which costs nothing at all, got:\n" + out);
    }
}
