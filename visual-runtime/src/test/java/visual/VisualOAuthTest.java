package visual;

import org.junit.jupiter.api.Test;
import visual.VisualOAuth.Grant;
import visual.VisualOAuth.Redirect;
import visual.VisualOAuth.Tokens;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualOAuthTest {

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
    void settingUpAnnouncesWhichProtocolThisIs() {
        String out = captureTrace(VisualOAuth::openIdConnect);
        assertTrue(out.contains("OAUTH_SETUP"), "expected a setup event, got:\n" + out);
        assertTrue(out.contains("\"protocol\":\"oidc\""), "the protocol must be visible, got:\n" + out);
    }

    @Test
    void theAuthorizationCodeFlowWalksAllSixSteps() {
        String out = captureTrace(() -> {
            VisualOAuth flow = VisualOAuth.openIdConnect();
            Redirect redirect = flow.authorize("alice", "photos.read");
            Grant grant = flow.approve(redirect);
            assertNotNull(grant, "consent must produce a code");
            Tokens tokens = flow.exchange(grant);
            assertNotNull(tokens, "the code must be exchangeable");
            flow.verifyIdToken(tokens);
            flow.callApi("/photos", tokens, "photos.read");
            flow.report();
        });
        assertTrue(out.contains("AUTHORIZATION_REQUEST"), "expected the redirect, got:\n" + out);
        assertTrue(out.contains("USER_AUTHENTICATED_AT_PROVIDER"), "the user logs in at the provider, got:\n" + out);
        assertTrue(out.contains("CONSENT_GRANTED"), "consent must happen, got:\n" + out);
        assertTrue(out.contains("AUTHORIZATION_CODE_RETURNED"), "a code must come back, got:\n" + out);
        assertTrue(out.contains("STATE_VERIFIED"), "state must be checked, got:\n" + out);
        assertTrue(out.contains("TOKEN_REQUEST"), "the back-channel call must happen, got:\n" + out);
        assertTrue(out.contains("TOKENS_ISSUED"), "tokens must be issued, got:\n" + out);
        assertTrue(out.contains("ID_TOKEN_VERIFIED"), "the id_token must be validated, got:\n" + out);
        assertTrue(out.contains("API_CALL_SERVED"), "the API call must be served, got:\n" + out);
        assertTrue(out.contains("\"breaches\":0"), "nothing may go wrong here, got:\n" + out);
    }

    @Test
    void theCodeFlowNeverCarriesThePasswordAnywhere() {
        String out = captureTrace(() -> {
            VisualOAuth flow = VisualOAuth.openIdConnect();
            Grant grant = flow.approve(flow.authorize("alice", "photos.read"));
            flow.exchange(grant);
        });
        assertFalse(out.contains("correct-horse"), "the client must never see a password, got:\n" + out);
    }

    @Test
    void plainOAuthHandsBackNothingThatSaysWhoTheUserIs() {
        String out = captureTrace(() -> {
            VisualOAuth flow = VisualOAuth.oauth2();
            Tokens tokens = flow.exchange(flow.approve(flow.authorize("alice", "photos.read")));
            assertNotNull(tokens, "tokens must still be issued");
            assertFalse(tokens.hasIdToken(), "plain OAuth 2.0 has no id_token");
            flow.verifyIdToken(tokens);
        });
        assertTrue(out.contains("ID_TOKEN_MISSING"), "there must be nothing to validate, got:\n" + out);
    }

    @Test
    void treatingAnAccessTokenAsALoginIsABreach() {
        String out = captureTrace(() -> {
            VisualOAuth flow = VisualOAuth.oauth2();
            Tokens tokens = flow.exchange(flow.approve(flow.authorize("alice", "photos.read")));
            flow.useAccessTokenAsLogin(tokens);
            flow.report();
        });
        assertTrue(out.contains("ACCESS_TOKEN_MISUSED_AS_IDENTITY"), "expected the classic bug, got:\n" + out);
        assertTrue(out.contains("\"breaches\":1"), "it must count as a breach, got:\n" + out);
    }

    @Test
    void aScopeThatWasNotGrantedIsRefusedWithForbidden() {
        String out = captureTrace(() -> {
            VisualOAuth flow = VisualOAuth.openIdConnect();
            Redirect redirect = flow.authorize("alice", "photos.read", "photos.write");
            Grant grant = flow.approveOnly(redirect, "openid", "photos.read");
            Tokens tokens = flow.exchange(grant);
            flow.callApi("/photos", tokens, "photos.read");
            flow.callApi("/photos", tokens, "photos.write");
            flow.report();
        });
        assertTrue(out.contains("API_CALL_SERVED"), "the granted scope must work, got:\n" + out);
        assertTrue(out.contains("SCOPE_INSUFFICIENT"), "the ungranted scope must not, got:\n" + out);
        assertTrue(out.contains("\"status\":403"), "an ungranted scope is 403, got:\n" + out);
    }

    @Test
    void refusingConsentIssuesNothingAtAll() {
        String out = captureTrace(() -> {
            VisualOAuth flow = VisualOAuth.openIdConnect();
            Redirect redirect = flow.authorize("alice", "photos.read");
            flow.deny(redirect);
            flow.callApi("/photos", null, "photos.read");
            flow.report();
        });
        assertTrue(out.contains("CONSENT_DENIED"), "the refusal must be traced, got:\n" + out);
        assertTrue(out.contains("API_CALL_REFUSED"), "without a token the API refuses, got:\n" + out);
        assertFalse(out.contains("TOKENS_ISSUED"), "nothing may be issued, got:\n" + out);
    }

    @Test
    void pkceStopsAStolenAuthorizationCode() {
        String out = captureTrace(() -> {
            VisualOAuth flow = VisualOAuth.openIdConnect().publicClient();
            Grant grant = flow.approve(flow.authorize("alice", "photos.read"));
            Grant stolen = flow.stealTheCode(grant);
            assertNull(flow.redeemStolenCode(stolen), "PKCE must stop the thief");
            assertNotNull(flow.exchange(grant), "the real client must still succeed");
            flow.report();
        });
        assertTrue(out.contains("AUTHORIZATION_CODE_STOLEN"), "the theft must be traced, got:\n" + out);
        assertTrue(out.contains("STOLEN_CODE_BLOCKED"), "PKCE must block it, got:\n" + out);
        assertTrue(out.contains("\"breaches\":0"), "nothing may be breached, got:\n" + out);
    }

    @Test
    void withoutPkceAPublicClientsCodeIsEnoughForTheThief() {
        String out = captureTrace(() -> {
            VisualOAuth flow = VisualOAuth.openIdConnect().publicClient().withoutPkce();
            Grant grant = flow.approve(flow.authorize("alice", "photos.read"));
            Tokens stolenTokens = flow.redeemStolenCode(flow.stealTheCode(grant));
            assertNotNull(stolenTokens, "with no PKCE the code alone works");
            flow.report();
        });
        assertTrue(out.contains("STOLEN_CODE_REDEEMED"), "the thief must get tokens, got:\n" + out);
        assertTrue(out.contains("\"breaches\":1"), "that is a breach, got:\n" + out);
    }

    @Test
    void aReusedAuthorizationCodeIsRefused() {
        String out = captureTrace(() -> {
            VisualOAuth flow = VisualOAuth.openIdConnect();
            Grant grant = flow.approve(flow.authorize("alice", "photos.read"));
            assertNotNull(flow.exchange(grant), "the first exchange works");
            assertNull(flow.exchange(grant), "codes are single use");
        });
        assertTrue(out.contains("AUTHORIZATION_CODE_REJECTED"), "the second use must fail, got:\n" + out);
        assertTrue(out.contains("\"reason\":\"code-reused\""), "the reason must be reuse, got:\n" + out);
    }

    @Test
    void theStateParameterBlocksAnInjectedCode() {
        String out = captureTrace(() -> {
            VisualOAuth flow = VisualOAuth.openIdConnect();
            flow.authorize("alice", "photos.read");
            assertNull(flow.exchange(flow.injectedCode()), "a foreign callback must be dropped");
            flow.report();
        });
        assertTrue(out.contains("CODE_INJECTION_ATTEMPT"), "the attempt must be traced, got:\n" + out);
        assertTrue(out.contains("STATE_MISMATCH_BLOCKED"), "state must stop it, got:\n" + out);
        assertTrue(out.contains("\"breaches\":0"), "nothing may be breached, got:\n" + out);
    }

    @Test
    void withoutStateAnInjectedCodeTakesOverTheSession() {
        String out = captureTrace(() -> {
            VisualOAuth flow = VisualOAuth.openIdConnect().withoutStateCheck();
            flow.authorize("alice", "photos.read");
            Tokens tokens = flow.exchange(flow.injectedCode());
            assertNotNull(tokens, "with no state the client redeems it");
            flow.callApi("/photos", tokens, "photos.read");
            flow.report();
        });
        assertTrue(out.contains("CODE_INJECTION_ACCEPTED"), "the injection must land, got:\n" + out);
        assertTrue(out.contains("\"breaches\":1"), "that is a breach, got:\n" + out);
    }

    @Test
    void anExpiredAccessTokenIsRenewedWithoutTheUser() {
        String out = captureTrace(() -> {
            VisualOAuth flow = VisualOAuth.openIdConnect();
            Tokens tokens = flow.exchange(flow.approve(flow.authorize("alice", "photos.read")));
            flow.advanceMinutes(VisualOAuth.ACCESS_LIFETIME_MINUTES + 1);
            flow.callApi("/photos", tokens, "photos.read");
            Tokens fresh = flow.refresh(tokens);
            assertNotNull(fresh, "the refresh token must still work");
            flow.callApi("/photos", fresh, "photos.read");
            flow.report();
        });
        assertTrue(out.contains("TIME_PASSED"), "the clock must move, got:\n" + out);
        assertTrue(out.contains("ACCESS_TOKEN_EXPIRED"), "the token must expire, got:\n" + out);
        assertTrue(out.contains("TOKENS_REFRESHED"), "renewal must work, got:\n" + out);
        assertTrue(out.contains("API_CALL_SERVED"), "the fresh token must work, got:\n" + out);
    }

    @Test
    void clientCredentialsHasNoUserAndNoRefreshToken() {
        String out = captureTrace(() -> {
            VisualOAuth flow = VisualOAuth.oauth2();
            Tokens tokens = flow.clientCredentials("reports.read");
            assertTrue(tokens.subject().isEmpty(), "there is no user in this grant");
            assertFalse(tokens.hasIdToken(), "and nothing to identify");
            flow.callApi("/reports", tokens, "reports.read");
            assertNull(flow.refresh(tokens), "there is no refresh token to use");
        });
        assertTrue(out.contains("CLIENT_CREDENTIALS_ISSUED"), "expected the machine grant, got:\n" + out);
        assertTrue(out.contains("REFRESH_UNAVAILABLE"), "there is nothing to refresh, got:\n" + out);
        assertTrue(out.contains("\"no user in this flow\""), "no resource owner is involved, got:\n" + out);
    }

    @Test
    void theImplicitFlowPutsTheTokenInTheUrl() {
        String out = captureTrace(() -> {
            VisualOAuth flow = VisualOAuth.oauth2();
            Tokens tokens = flow.implicitFlow("alice", "photos.read");
            flow.callApi("/photos", tokens, "photos.read");
            flow.report();
        });
        assertTrue(out.contains("IMPLICIT_TOKEN_IN_URL"), "expected the old flow, got:\n" + out);
        assertTrue(out.contains("#access_token="), "the token rides in the fragment, got:\n" + out);
        assertTrue(out.contains("\"warnings\":1"), "it must be flagged as risky, got:\n" + out);
    }

    @Test
    void thePasswordGrantHandsTheSecretToTheApp() {
        String out = captureTrace(() -> {
            VisualOAuth flow = VisualOAuth.oauth2();
            flow.passwordGrant("alice", "correct-horse");
            flow.report();
        });
        assertTrue(out.contains("PASSWORD_GRANT_USED"), "expected the anti-pattern, got:\n" + out);
        assertTrue(out.contains("correct-horse"), "the client really does see it, got:\n" + out);
        assertTrue(out.contains("\"reason\":\"password-seen-by-client\""), "flagged, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualOAuth flow = VisualOAuth.openIdConnect().publicClient();
            Redirect redirect = flow.authorize("alice", "photos.read", "photos.write");
            Grant grant = flow.approveOnly(redirect, "openid", "photos.read");
            flow.stealTheCode(grant);
            Tokens tokens = flow.exchange(grant);
            flow.verifyIdToken(tokens);
            flow.callApi("/photos", tokens, "photos.read");
            flow.callApi("/photos", tokens, "photos.write");
            flow.advanceMinutes(20);
            flow.refresh(tokens);
            flow.report();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
