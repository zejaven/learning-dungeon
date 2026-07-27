package visual;

import org.junit.jupiter.api.Test;
import visual.VisualJwt.Token;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualJwtTest {

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
    void creatingAnIssuerEmitsTheSetupEvent() {
        String out = captureTrace(VisualJwt::jwt);
        assertTrue(out.contains("JWT_SETUP"), "expected a setup event, got:\n" + out);
        assertTrue(out.contains("\"scheme\":\"jwt\""), "the scheme must be visible, got:\n" + out);
    }

    @Test
    void anIssuedTokenHasThreeDottedSegments() {
        Token[] held = new Token[1];
        String out = captureTrace(() -> held[0] = VisualJwt.jwt().issue("alice", "user", "orders"));
        assertTrue(out.contains("TOKEN_ISSUED"), "expected an issue event, got:\n" + out);
        assertEquals(3, held[0].value().split("\\.", -1).length,
                "a JWT is header.payload.signature, got: " + held[0].value());
    }

    @Test
    void anyoneCanReadTheClaimsWithNoKeyAtAll() {
        String out = captureTrace(() -> {
            VisualJwt auth = VisualJwt.jwt();
            auth.decode(auth.issue("alice", "user", "orders"));
        });
        assertTrue(out.contains("TOKEN_DECODED"), "the token must decode, got:\n" + out);
        assertTrue(out.contains("\\\"sub\\\":\\\"alice\\\""),
                "the subject must be readable, got:\n" + out);
        assertTrue(out.contains("\\\"role\\\":\\\"user\\\""),
                "the role must be readable, got:\n" + out);
    }

    @Test
    void anOpaqueSessionIdRevealsNothing() {
        String out = captureTrace(() -> {
            VisualJwt auth = VisualJwt.sessions();
            auth.decode(auth.issue("alice", "user", "orders"));
        });
        assertTrue(out.contains("SESSION_ISSUED"), "expected a session, got:\n" + out);
        assertTrue(out.contains("NOTHING_TO_READ"), "an opaque id says nothing, got:\n" + out);
        assertFalse(out.contains("TOKEN_DECODED"), "there is nothing to decode, got:\n" + out);
    }

    @Test
    void aValidTokenVerifiesWithoutASingleLookup() {
        String out = captureTrace(() -> {
            VisualJwt auth = VisualJwt.jwt().service("orders");
            auth.verifyAt("orders", auth.issue("alice", "user", "orders"));
            auth.report();
        });
        assertTrue(out.contains("SIGNATURE_VERIFIED"), "the signature must check out, got:\n" + out);
        assertTrue(out.contains("ACCESS_GRANTED"), "the request must be served, got:\n" + out);
        assertTrue(out.contains("store lookups 0"), "a JWT costs no lookup, got:\n" + out);
    }

    @Test
    void aSessionIdCostsOneLookupPerRequest() {
        String out = captureTrace(() -> {
            VisualJwt auth = VisualJwt.sessions().service("orders");
            Token id = auth.issue("alice", "user", "orders");
            auth.verifyAt("orders", id);
            auth.verifyAt("orders", id);
            auth.report();
        });
        assertTrue(out.contains("STORE_LOOKUP"), "the store must be consulted, got:\n" + out);
        assertTrue(out.contains("ACCESS_GRANTED"), "the request must be served, got:\n" + out);
        assertTrue(out.contains("store lookups 2"), "one lookup per request, got:\n" + out);
    }

    @Test
    void editingAClaimBreaksTheSignature() {
        String out = captureTrace(() -> {
            VisualJwt auth = VisualJwt.jwt().service("orders");
            Token forged = auth.tamper(auth.issue("alice", "user", "orders"), "role", "admin");
            auth.verifyAt("orders", forged);
            auth.report();
        });
        assertTrue(out.contains("TOKEN_TAMPERED"), "the client edits the payload, got:\n" + out);
        assertTrue(out.contains("SIGNATURE_INVALID"), "the signature must not match, got:\n" + out);
        assertFalse(out.contains("ACCESS_GRANTED"), "nothing may be served, got:\n" + out);
    }

    @Test
    void algNoneIsRejectedWhenTheServerPinsTheAlgorithm() {
        String out = captureTrace(() -> {
            VisualJwt auth = VisualJwt.jwt().service("orders");
            auth.verifyAt("orders", auth.stripSignature(auth.issue("alice", "user", "orders")));
            auth.report();
        });
        assertTrue(out.contains("ALG_NONE_REJECTED"), "an unsigned token is not a token, got:\n" + out);
        assertFalse(out.contains("ACCESS_GRANTED"), "nothing may be served, got:\n" + out);
        assertTrue(out.contains("should NOT have been 0"), "no breach here, got:\n" + out);
    }

    @Test
    void algNoneIsAcceptedWhenTheVerifierBelievesTheHeader() {
        String out = captureTrace(() -> {
            VisualJwt auth = VisualJwt.jwt().service("orders").trustAlgorithmHeader();
            auth.verifyAt("orders", auth.stripSignature(auth.issue("alice", "user", "orders")));
            auth.report();
        });
        assertTrue(out.contains("ALG_NONE_ACCEPTED"), "the forgery must land, got:\n" + out);
        assertTrue(out.contains("should NOT have been 1"), "that is a breach, got:\n" + out);
    }

    @Test
    void aTokenAddressedElsewhereIsRefusedDespiteAValidSignature() {
        String out = captureTrace(() -> {
            VisualJwt auth = VisualJwt.jwt().service("orders").service("billing").checkAudience();
            Token forOrders = auth.issue("alice", "user", "orders");
            auth.verifyAt("orders", forOrders);
            auth.verifyAt("billing", forOrders);
            auth.report();
        });
        assertTrue(out.contains("SIGNATURE_VERIFIED"), "the signature is fine, got:\n" + out);
        assertTrue(out.contains("AUDIENCE_MISMATCH"), "billing must refuse it, got:\n" + out);
    }

    @Test
    void timeAloneEndsAStatelessToken() {
        String out = captureTrace(() -> {
            VisualJwt auth = VisualJwt.jwt().service("orders");
            Token token = auth.issue("alice", "user", "orders");
            auth.advanceMinutes(VisualJwt.ACCESS_TTL_MINUTES + 1);
            auth.verifyAt("orders", token);
            auth.report();
        });
        assertTrue(out.contains("TIME_PASSED"), "the clock must move, got:\n" + out);
        assertTrue(out.contains("TOKEN_EXPIRED"), "the token must expire, got:\n" + out);
        assertFalse(out.contains("ACCESS_GRANTED"), "nothing may be served, got:\n" + out);
    }

    @Test
    void aLoggedOutJwtKeepsWorkingAndThatIsABreach() {
        String out = captureTrace(() -> {
            VisualJwt auth = VisualJwt.jwt().service("orders");
            Token token = auth.issue("alice", "user", "orders");
            auth.logout(token);
            auth.verifyAt("orders", token);
            auth.report();
        });
        assertTrue(out.contains("LOGGED_OUT"), "the user logs out, got:\n" + out);
        assertTrue(out.contains("REVOKED_TOKEN_ACCEPTED"), "and it still works, got:\n" + out);
        assertTrue(out.contains("should NOT have been 1"), "that is a breach, got:\n" + out);
    }

    @Test
    void aDenyListMakesLogoutRealAgainAtTheCostOfALookup() {
        String out = captureTrace(() -> {
            VisualJwt auth = VisualJwt.jwt().service("orders").denyList();
            Token token = auth.issue("alice", "user", "orders");
            auth.logout(token);
            auth.verifyAt("orders", token);
            auth.report();
        });
        assertTrue(out.contains("DENYLIST_HIT"), "the deny-list must catch it, got:\n" + out);
        assertFalse(out.contains("REVOKED_TOKEN_ACCEPTED"), "no breach now, got:\n" + out);
        assertTrue(out.contains("should NOT have been 0"), "no breach now, got:\n" + out);
    }

    @Test
    void aSessionLogoutTakesEffectOnTheVeryNextRequest() {
        String out = captureTrace(() -> {
            VisualJwt auth = VisualJwt.sessions().service("orders");
            Token id = auth.issue("alice", "user", "orders");
            auth.logout(id);
            auth.verifyAt("orders", id);
            auth.report();
        });
        assertTrue(out.contains("LOGGED_OUT"), "the record is deleted, got:\n" + out);
        assertTrue(out.contains("ACCESS_DENIED"), "the next request finds nothing, got:\n" + out);
        assertTrue(out.contains("should NOT have been 0"), "no breach here, got:\n" + out);
    }

    @Test
    void aJwtKeepsClaimingARoleTheDatabaseHasAlreadyChanged() {
        String out = captureTrace(() -> {
            VisualJwt auth = VisualJwt.jwt().service("orders");
            Token token = auth.issue("alice", "admin", "orders");
            auth.changeRole("alice", "reader");
            auth.verifyAt("orders", token);
            auth.report();
        });
        assertTrue(out.contains("ROLE_CHANGED"), "the database must change, got:\n" + out);
        assertTrue(out.contains("STALE_CLAIM_ACCEPTED"), "the token must win, got:\n" + out);
        assertTrue(out.contains("should NOT have been 1"), "that is a breach, got:\n" + out);
    }

    @Test
    void aSessionReadsTodaysTruthOnEveryRequest() {
        String out = captureTrace(() -> {
            VisualJwt auth = VisualJwt.sessions().service("orders");
            Token id = auth.issue("alice", "admin", "orders");
            auth.changeRole("alice", "reader");
            auth.verifyAt("orders", id);
            auth.deactivate("alice");
            auth.verifyAt("orders", id);
            auth.report();
        });
        assertTrue(out.contains("with role reader"), "the new role must be used, got:\n" + out);
        assertTrue(out.contains("ACCESS_DENIED"), "a disabled account must be cut off, got:\n" + out);
        assertTrue(out.contains("should NOT have been 0"), "no breach here, got:\n" + out);
    }

    @Test
    void oneTokenIsVerifiedByEveryServiceWithNoSharedStore() {
        String out = captureTrace(() -> {
            VisualJwt auth = VisualJwt.jwt().service("orders").service("billing").service("search");
            Token token = auth.issue("alice", "user", "orders");
            auth.verifyAt("orders", token);
            auth.verifyAt("billing", token);
            auth.verifyAt("search", token);
            auth.report();
        });
        assertTrue(out.contains("store lookups 0"), "no service asks anybody, got:\n" + out);
        assertTrue(out.contains("served 3"), "all three must be served, got:\n" + out);
    }

    @Test
    void aMissingCredentialIsAnonymous() {
        String out = captureTrace(() -> {
            VisualJwt auth = VisualJwt.jwt().service("orders");
            auth.verifyAt("orders", null);
            auth.report();
        });
        assertTrue(out.contains("ACCESS_DENIED"), "401 for an anonymous call, got:\n" + out);
        assertTrue(out.contains("refused 1"), "it must be counted, got:\n" + out);
    }

    @Test
    void aJwtIsFarLargerOnTheWireThanASessionId() {
        Token[] both = new Token[2];
        captureTrace(() -> {
            both[0] = VisualJwt.jwt().issue("alice", "user", "orders");
            both[1] = VisualJwt.sessions().issue("alice", "user", "orders");
        });
        assertTrue(both[0].size() > both[1].size() * 3,
                "a JWT should dwarf a session id: " + both[0].size() + " vs " + both[1].size());
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualJwt auth = VisualJwt.jwt().service("orders").service("billing").checkAudience();
            Token token = auth.issue("alice", "admin", "orders");
            auth.decode(token);
            auth.verifyAt("orders", token);
            auth.verifyAt("billing", token);
            auth.verifyAt("orders", auth.tamper(token, "role", "root"));
            auth.verifyAt("orders", auth.stripSignature(token));
            auth.changeRole("alice", "reader");
            auth.deactivate("alice");
            auth.verifyAt("orders", token);
            auth.logout(token);
            auth.verifyAt("orders", token);
            auth.advanceMinutes(VisualJwt.ACCESS_TTL_MINUTES + 1);
            auth.verifyAt("orders", token);
            auth.report();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
