package visual;

import org.junit.jupiter.api.Test;
import visual.VisualAuthentication.Credential;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualAuthenticationTest {

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
    void settingUpASystemAnnouncesItsCredentialStyle() {
        String out = captureTrace(VisualAuthentication::withSessions);
        assertTrue(out.contains("AUTH_SETUP"), "expected a setup event, got:\n" + out);
        assertTrue(out.contains("\"credentialStyle\":\"session\""), "style must be visible, got:\n" + out);
    }

    @Test
    void registrationStoresAHashAndNotThePassword() {
        String out = captureTrace(() -> {
            VisualAuthentication auth = VisualAuthentication.withSessions();
            auth.register("alice", "correct-horse");
        });
        assertTrue(out.contains("USER_REGISTERED"), "expected a registration event, got:\n" + out);
        assertFalse(out.contains("correct-horse"), "the password must never be stored, got:\n" + out);
    }

    @Test
    void plaintextStorageKeepsTheSecretItself() {
        String out = captureTrace(() -> {
            VisualAuthentication auth = VisualAuthentication.withSessions().storePasswordsInPlaintext();
            auth.register("alice", "correct-horse");
            auth.leakTheUserStore();
        });
        assertTrue(out.contains("PLAINTEXT_PASSWORD_STORED"), "expected the anti-pattern, got:\n" + out);
        assertTrue(out.contains("USER_STORE_LEAKED"), "expected the leak, got:\n" + out);
        assertTrue(out.contains("correct-horse"), "the leak hands out the password, got:\n" + out);
    }

    @Test
    void theHappyPathProvesIssuesAndThenIdentifies() {
        String out = captureTrace(() -> {
            VisualAuthentication auth = VisualAuthentication.withSessions();
            auth.register("alice", "correct-horse");
            Credential session = auth.login("alice", "correct-horse");
            assertNotNull(session, "a correct password must produce a credential");
            auth.request("/api/profile", session);
            auth.report();
        });
        assertTrue(out.contains("LOGIN_ATTEMPT"), "expected a login attempt, got:\n" + out);
        assertTrue(out.contains("PASSWORD_VERIFIED"), "the password must be checked, got:\n" + out);
        assertTrue(out.contains("CREDENTIAL_ISSUED"), "a credential must be issued, got:\n" + out);
        assertTrue(out.contains("IDENTITY_ESTABLISHED"), "the caller must be identified, got:\n" + out);
        assertTrue(out.contains("served that should NOT have been 0"), "no breach, got:\n" + out);
    }

    @Test
    void aWrongPasswordIssuesNothing() {
        String out = captureTrace(() -> {
            VisualAuthentication auth = VisualAuthentication.withSessions();
            auth.register("alice", "correct-horse");
            assertNull(auth.login("alice", "guess"), "a wrong password must not produce a credential");
            auth.report();
        });
        assertTrue(out.contains("LOGIN_FAILED"), "the attempt must fail, got:\n" + out);
        assertFalse(out.contains("CREDENTIAL_ISSUED"), "nothing may be issued, got:\n" + out);
    }

    @Test
    void repeatedGuessesRunIntoTheThrottle() {
        String out = captureTrace(() -> {
            VisualAuthentication auth = VisualAuthentication.withSessions().rateLimitLogins(3);
            auth.register("alice", "correct-horse");
            for (int i = 0; i < 4; i++) {
                auth.login("alice", "guess-" + i);
            }
            auth.report();
        });
        assertTrue(out.contains("LOGIN_RATE_LIMITED"), "the fourth attempt must be throttled, got:\n" + out);
        assertTrue(out.contains("\"status\":429"), "throttling answers 429, got:\n" + out);
    }

    @Test
    void aRequestWithNoCredentialIsAnonymous() {
        String out = captureTrace(() -> {
            VisualAuthentication auth = VisualAuthentication.withSessions();
            auth.request("/api/profile");
            auth.report();
        });
        assertTrue(out.contains("ANONYMOUS_REQUEST"), "expected an anonymous request, got:\n" + out);
        assertTrue(out.contains("\"status\":401"), "the status must be 401, got:\n" + out);
    }

    @Test
    void aSecondFactorMustBeCompletedBeforeTheCredentialWorks() {
        String out = captureTrace(() -> {
            VisualAuthentication auth = VisualAuthentication.withSessions();
            auth.register("alice", "correct-horse");
            auth.requireSecondFactor("alice", "314159");
            Credential half = auth.login("alice", "correct-horse");
            auth.request("/api/profile", half);
            Credential full = auth.submitSecondFactor(half, "314159");
            auth.request("/api/profile", full);
            auth.report();
        });
        assertTrue(out.contains("MFA_REQUIRED"), "the second factor must be demanded, got:\n" + out);
        assertTrue(out.contains("\"reason\":\"mfa-incomplete\""), "half a login is not a login, got:\n" + out);
        assertTrue(out.contains("MFA_PASSED"), "the code must complete the login, got:\n" + out);
        assertTrue(out.contains("IDENTITY_ESTABLISHED"), "and then the request works, got:\n" + out);
    }

    @Test
    void aWrongCodeLeavesTheLoginHalfFinished() {
        String out = captureTrace(() -> {
            VisualAuthentication auth = VisualAuthentication.withSessions();
            auth.register("alice", "correct-horse");
            auth.requireSecondFactor("alice", "314159");
            Credential half = auth.login("alice", "correct-horse");
            auth.submitSecondFactor(half, "000000");
            auth.report();
        });
        assertTrue(out.contains("MFA_FAILED"), "the wrong code must fail, got:\n" + out);
        assertFalse(out.contains("CREDENTIAL_ISSUED"), "nothing may be issued, got:\n" + out);
    }

    @Test
    void timePassingIsEnoughToInvalidateACredential() {
        String out = captureTrace(() -> {
            VisualAuthentication auth = VisualAuthentication.withTokens();
            auth.register("alice", "correct-horse");
            Credential token = auth.login("alice", "correct-horse");
            auth.advanceMinutes(VisualAuthentication.ACCESS_LIFETIME_MINUTES + 1);
            auth.request("/api/profile", token);
            Credential fresh = auth.refresh(token);
            auth.request("/api/profile", fresh);
            auth.report();
        });
        assertTrue(out.contains("TIME_PASSED"), "the clock must move, got:\n" + out);
        assertTrue(out.contains("CREDENTIAL_EXPIRED"), "the token must expire, got:\n" + out);
        assertTrue(out.contains("CREDENTIAL_REFRESHED"), "renewal must work, got:\n" + out);
        assertTrue(out.contains("IDENTITY_ESTABLISHED"), "the fresh token must work, got:\n" + out);
    }

    @Test
    void aStatelessSystemStoresNothingOnTheServer() {
        String out = captureTrace(() -> {
            VisualAuthentication auth = VisualAuthentication.withTokens();
            auth.register("alice", "correct-horse");
            auth.login("alice", "correct-horse");
        });
        assertFalse(out.contains("\"serverStore\":[{"), "the server keeps nothing, got:\n" + out);
        assertTrue(out.contains("\"clientStore\":[{\"id\":\"t1\""), "the client keeps the token, got:\n" + out);
    }

    @Test
    void loggingOutOfASessionEndsItImmediately() {
        String out = captureTrace(() -> {
            VisualAuthentication auth = VisualAuthentication.withSessions();
            auth.register("alice", "correct-horse");
            Credential session = auth.login("alice", "correct-horse");
            auth.logout(session);
            auth.request("/api/profile", session);
            auth.report();
        });
        assertTrue(out.contains("LOGGED_OUT"), "the logout must be traced, got:\n" + out);
        assertTrue(out.contains("\"reason\":\"logged-out\""), "the session must be gone, got:\n" + out);
        assertTrue(out.contains("served that should NOT have been 0"), "no breach, got:\n" + out);
    }

    @Test
    void loggingOutOfAStatelessTokenDoesNotStopIt() {
        String out = captureTrace(() -> {
            VisualAuthentication auth = VisualAuthentication.withTokens();
            auth.register("alice", "correct-horse");
            Credential token = auth.login("alice", "correct-horse");
            auth.logout(token);
            auth.requestAs("mallory", "/api/profile", token);
            auth.report();
        });
        assertTrue(out.contains("REVOKED_TOKEN_STILL_VALID"), "the token must survive logout, got:\n" + out);
        assertTrue(out.contains("served that should NOT have been 1"), "one breach, got:\n" + out);
    }

    @Test
    void whoeverHoldsTheCredentialIsTheUser() {
        String out = captureTrace(() -> {
            VisualAuthentication auth = VisualAuthentication.withSessions();
            auth.register("alice", "correct-horse");
            Credential session = auth.login("alice", "correct-horse");
            auth.requestAs("mallory", "/api/profile", session);
            auth.report();
        });
        assertTrue(out.contains("STOLEN_CREDENTIAL_ACCEPTED"), "the copy must work, got:\n" + out);
        assertTrue(out.contains("\"user\":\"alice\""), "the server sees alice, got:\n" + out);
        assertTrue(out.contains("served that should NOT have been 1"), "one breach, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualAuthentication auth = VisualAuthentication.withSessions().rateLimitLogins(2);
            auth.register("alice", "correct-horse");
            auth.requireSecondFactor("alice", "314159");
            auth.request("/api/profile");
            Credential half = auth.login("alice", "correct-horse");
            Credential session = auth.submitSecondFactor(half, "314159");
            auth.request("/api/profile", session);
            auth.advanceMinutes(20);
            auth.request("/api/profile", session);
            Credential fresh = auth.refresh(session);
            auth.logout(fresh);
            auth.leakTheUserStore();
            auth.report();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
