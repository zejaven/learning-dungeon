package com.interviewlearning.remote;

import com.interviewlearning.remote.RemoteAccessFilter.Decision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The remote-access policy: who is "local", who gets in, and what stays local-only. */
class RemoteAccessFilterTest {

    private static final String TOKEN = "0123456789abcdef0123"; // >= MIN_TOKEN_LENGTH

    private Decision decide(RemoteAccessMode mode, boolean local, String path,
                            String cookie, String query) {
        return RemoteAccessFilter.decide(mode, TOKEN, false, local, path, cookie, query);
    }

    // --- who counts as local -------------------------------------------------

    @Test
    void loopbackLiteralsAreLocal() {
        assertTrue(RemoteAccessFilter.isLoopback("127.0.0.1"));
        assertTrue(RemoteAccessFilter.isLoopback("127.1.2.3")); // the whole /8
        assertTrue(RemoteAccessFilter.isLoopback("::1"));
        assertTrue(RemoteAccessFilter.isLoopback("0:0:0:0:0:0:0:1"));
        assertTrue(RemoteAccessFilter.isLoopback("[::1]"));
        assertTrue(RemoteAccessFilter.isLoopback("::ffff:127.0.0.1"));
    }

    @Test
    void otherAddressesAreNotLocal() {
        assertFalse(RemoteAccessFilter.isLoopback("192.168.88.200"));
        assertFalse(RemoteAccessFilter.isLoopback("100.101.102.103")); // tailnet
        assertFalse(RemoteAccessFilter.isLoopback("999.0.0.1"));
        assertFalse(RemoteAccessFilter.isLoopback("localhost")); // no name resolution
        assertFalse(RemoteAccessFilter.isLoopback(""));
        assertFalse(RemoteAccessFilter.isLoopback(null));
    }

    @Test
    void forwardedForIsIgnoredUnlessProxied() {
        // A directly exposed server must not let a caller claim to be local.
        assertFalse(RemoteAccessFilter.isLocal(RemoteAccessMode.DIRECT, "192.168.88.5", "127.0.0.1"));
        assertFalse(RemoteAccessFilter.isLocal(RemoteAccessMode.OFF, "192.168.88.5", "127.0.0.1"));
    }

    @Test
    void proxiedReadsTheClientFromForwardedFor() {
        // Tailscale Serve / the Vite proxy connect over loopback themselves.
        assertFalse(RemoteAccessFilter.isLocal(RemoteAccessMode.PROXIED, "127.0.0.1", "100.101.102.103"));
        assertTrue(RemoteAccessFilter.isLocal(RemoteAccessMode.PROXIED, "127.0.0.1", "127.0.0.1"));
        assertTrue(RemoteAccessFilter.isLocal(RemoteAccessMode.PROXIED, "127.0.0.1", null));
        // Only the first (original client) entry counts.
        assertFalse(RemoteAccessFilter.isLocal(RemoteAccessMode.PROXIED, "127.0.0.1",
                "100.101.102.103, 127.0.0.1"));
    }

    // --- the policy ----------------------------------------------------------

    @Test
    void localRequestsAreNeverGated() {
        assertEquals(Decision.ALLOW, decide(RemoteAccessMode.OFF, true, "/api/run", null, null));
        assertEquals(Decision.ALLOW, decide(RemoteAccessMode.DIRECT, true, "/api/run", null, null));
    }

    @Test
    void remoteIsRefusedWhileOff() {
        assertEquals(Decision.REMOTE_DISABLED, decide(RemoteAccessMode.OFF, false, "/", null, null));
        // Even with the right token: off means off.
        assertEquals(Decision.REMOTE_DISABLED, decide(RemoteAccessMode.OFF, false, "/", TOKEN, null));
    }

    @Test
    void remoteNeedsTheToken() {
        assertEquals(Decision.UNAUTHORIZED, decide(RemoteAccessMode.DIRECT, false, "/", null, null));
        assertEquals(Decision.UNAUTHORIZED, decide(RemoteAccessMode.DIRECT, false, "/", "wrong", null));
        assertEquals(Decision.UNAUTHORIZED, decide(RemoteAccessMode.DIRECT, false, "/", null, "wrong"));
        // A prefix of the token is not the token.
        assertEquals(Decision.UNAUTHORIZED,
                decide(RemoteAccessMode.DIRECT, false, "/", TOKEN.substring(0, 10), null));
    }

    @Test
    void tokenInTheQueryBootstrapsTheCookie() {
        assertEquals(Decision.BOOTSTRAP, decide(RemoteAccessMode.DIRECT, false, "/", null, TOKEN));
        // With the cookie already set the request just goes through.
        assertEquals(Decision.ALLOW, decide(RemoteAccessMode.DIRECT, false, "/api/topics", TOKEN, null));
    }

    @Test
    void codeExecutionStaysLocalOnlyForAuthenticatedRemotes() {
        for (String path : new String[] { "/api/run", "/api/sql", "/api/challenge", "/api/analyze" }) {
            assertEquals(Decision.CODE_EXECUTION_BLOCKED,
                    decide(RemoteAccessMode.PROXIED, false, path, TOKEN, null),
                    path + " must not run for a remote client");
        }
        // Reading and the lesson flow are what the phone actually uses.
        assertEquals(Decision.ALLOW, decide(RemoteAccessMode.PROXIED, false, "/api/topics", TOKEN, null));
        assertEquals(Decision.ALLOW,
                decide(RemoteAccessMode.PROXIED, false, "/api/lesson/hashmap/answer", TOKEN, null));
    }

    @Test
    void codeExecutionCanBeAllowedExplicitly() {
        assertEquals(Decision.ALLOW, RemoteAccessFilter.decide(
                RemoteAccessMode.DIRECT, TOKEN, true, false, "/api/run", TOKEN, null));
    }

    @Test
    void codeExecutionMatchIsPathBased() {
        assertTrue(RemoteAccessFilter.isCodeExecution("/api/run"));
        assertTrue(RemoteAccessFilter.isCodeExecution("/api/sql/anything"));
        assertFalse(RemoteAccessFilter.isCodeExecution("/api/runtime-info"));
        assertFalse(RemoteAccessFilter.isCodeExecution("/api/topics"));
        assertFalse(RemoteAccessFilter.isCodeExecution(null));
    }

    // --- query handling ------------------------------------------------------

    @Test
    void readsTheTokenOutOfTheQueryString() {
        assertEquals("abc", RemoteAccessFilter.queryParam("token=abc", "token"));
        assertEquals("abc", RemoteAccessFilter.queryParam("x=1&token=abc&y=2", "token"));
        assertEquals("a b", RemoteAccessFilter.queryParam("token=a%20b", "token"));
        assertNull(RemoteAccessFilter.queryParam("tokenish=abc", "token"));
        assertNull(RemoteAccessFilter.queryParam(null, "token"));
    }

    @Test
    void redirectDropsOnlyTheToken() {
        assertEquals("/", RemoteAccessFilter.withoutToken("/", "token=abc"));
        assertEquals("/?x=1", RemoteAccessFilter.withoutToken("/", "x=1&token=abc"));
        assertEquals("/api/topics", RemoteAccessFilter.withoutToken("/api/topics", null));
    }

    // --- configuration -------------------------------------------------------

    @Test
    void modeParsingAcceptsTheDocumentedValues() {
        assertEquals(RemoteAccessMode.OFF, RemoteAccessMode.parse(""));
        assertEquals(RemoteAccessMode.OFF, RemoteAccessMode.parse("off"));
        assertEquals(RemoteAccessMode.DIRECT, RemoteAccessMode.parse("Direct"));
        assertEquals(RemoteAccessMode.PROXIED, RemoteAccessMode.parse("PROXIED"));
        assertThrows(IllegalStateException.class, () -> RemoteAccessMode.parse("yes"));
    }

    @Test
    void remoteModeWithoutAUsableTokenFailsToStart() {
        assertThrows(IllegalStateException.class, () -> new RemoteAccessFilter("direct", "", false));
        assertThrows(IllegalStateException.class, () -> new RemoteAccessFilter("direct", "short", false));
        // Off needs no token.
        new RemoteAccessFilter("off", "", false);
        new RemoteAccessFilter("direct", TOKEN, false);
    }
}
