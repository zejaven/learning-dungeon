package visual;

import org.junit.jupiter.api.Test;
import visual.VisualEndpointSecurity.Access;
import visual.VisualEndpointSecurity.Request;
import visual.VisualEndpointSecurity.Token;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualEndpointSecurityTest {

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
    void settingUpAnApiEmitsItsDefaultStance() {
        String out = captureTrace(VisualEndpointSecurity::denyByDefault);
        assertTrue(out.contains("SECURITY_SETUP"), "expected a setup event, got:\n" + out);
        assertTrue(out.contains("\"defaultStance\":\"deny\""), "the stance must be visible, got:\n" + out);
    }

    @Test
    void anUnmatchedPathIsRefusedWhenTheDefaultIsDeny() {
        String out = captureTrace(() -> {
            VisualEndpointSecurity api = VisualEndpointSecurity.denyByDefault();
            api.rule("/api/health", Access.permitAll());
            api.send(Request.get("/api/internal/metrics"));
            api.report();
        });
        assertTrue(out.contains("DENIED_BY_DEFAULT"), "the unmatched path must be denied, got:\n" + out);
        assertFalse(out.contains("HANDLER_RAN"), "nothing may reach the handler, got:\n" + out);
        assertTrue(out.contains("served that should NOT have been 0"), "no breach, got:\n" + out);
    }

    @Test
    void anUnmatchedPathIsServedWhenTheDefaultIsPermit() {
        String out = captureTrace(() -> {
            VisualEndpointSecurity api = VisualEndpointSecurity.permitByDefault();
            api.rule("/api/admin/**", Access.hasRole("ADMIN"));
            api.send(Request.get("/api/internal/metrics"));
            api.report();
        });
        assertTrue(out.contains("EXPOSED_BY_DEFAULT"), "the unmatched path must be served, got:\n" + out);
        assertTrue(out.contains("HANDLER_RAN"), "the handler must run, got:\n" + out);
        assertTrue(out.contains("served that should NOT have been 1"), "one breach, got:\n" + out);
    }

    @Test
    void aBroadRuleInFrontMakesTheStricterRuleUnreachable() {
        String out = captureTrace(() -> {
            VisualEndpointSecurity api = VisualEndpointSecurity.denyByDefault();
            api.rule("/api/**", Access.authenticated());
            api.rule("/api/admin/**", Access.hasRole("ADMIN"));
            api.send(Request.get("/api/admin/users").bearer(Token.forUser("alice").roles("USER")));
            api.report();
        });
        assertTrue(out.contains("RULE_SHADOWED"), "the second rule is dead, got:\n" + out);
        assertTrue(out.contains("HANDLER_RAN"), "the broad rule lets a USER in, got:\n" + out);
        assertFalse(out.contains("ROLE_DENIED"), "the role rule never fires, got:\n" + out);
    }

    @Test
    void reorderingTheRulesRestoresTheRoleCheck() {
        String out = captureTrace(() -> {
            VisualEndpointSecurity api = VisualEndpointSecurity.denyByDefault();
            api.rule("/api/admin/**", Access.hasRole("ADMIN"));
            api.rule("/api/**", Access.authenticated());
            api.send(Request.get("/api/admin/users").bearer(Token.forUser("alice").roles("USER")));
            api.report();
        });
        assertFalse(out.contains("RULE_SHADOWED"), "specific first is fine, got:\n" + out);
        assertTrue(out.contains("ROLE_DENIED"), "the role rule must fire, got:\n" + out);
        assertFalse(out.contains("HANDLER_RAN"), "nothing may reach the handler, got:\n" + out);
    }

    @Test
    void anAnonymousCallToAProtectedEndpointIs401() {
        String out = captureTrace(() -> {
            VisualEndpointSecurity api = VisualEndpointSecurity.denyByDefault();
            api.rule("/api/orders/**", Access.authenticated());
            api.send(Request.get("/api/orders/42"));
            api.report();
        });
        assertTrue(out.contains("AUTHENTICATION_MISSING"), "expected a 401, got:\n" + out);
        assertTrue(out.contains("\"status\":401"), "the status must be 401, got:\n" + out);
    }

    @Test
    void aKnownCallerWithoutTheRoleIs403() {
        String out = captureTrace(() -> {
            VisualEndpointSecurity api = VisualEndpointSecurity.denyByDefault();
            api.rule("/api/admin/**", Access.hasRole("ADMIN"));
            api.send(Request.get("/api/admin/users").bearer(Token.forUser("alice").roles("USER")));
            api.report();
        });
        assertTrue(out.contains("AUTHENTICATED"), "the caller is known, got:\n" + out);
        assertTrue(out.contains("ROLE_DENIED"), "expected a 403, got:\n" + out);
        assertTrue(out.contains("\"status\":403"), "the status must be 403, got:\n" + out);
    }

    @Test
    void theRightRoleReachesTheHandler() {
        String out = captureTrace(() -> {
            VisualEndpointSecurity api = VisualEndpointSecurity.denyByDefault();
            api.rule("/api/admin/**", Access.hasRole("ADMIN"));
            api.send(Request.get("/api/admin/users").bearer(Token.forUser("root").roles("ADMIN")));
            api.report();
        });
        assertTrue(out.contains("AUTHORIZED"), "the role matches, got:\n" + out);
        assertTrue(out.contains("HANDLER_RAN"), "the handler must run, got:\n" + out);
        assertTrue(out.contains("served that should NOT have been 0"), "no breach, got:\n" + out);
    }

    @Test
    void aPublicEndpointNeedsNoCredential() {
        String out = captureTrace(() -> {
            VisualEndpointSecurity api = VisualEndpointSecurity.denyByDefault();
            api.rule("/api/health", Access.permitAll());
            api.send(Request.get("/api/health"));
            api.report();
        });
        assertTrue(out.contains("HANDLER_RAN"), "a public endpoint answers anonymously, got:\n" + out);
        assertFalse(out.contains("AUTHENTICATION_MISSING"), "nothing is demanded, got:\n" + out);
    }

    @Test
    void everyKindOfBadTokenIsRejectedWithItsOwnReason() {
        String out = captureTrace(() -> {
            VisualEndpointSecurity api = VisualEndpointSecurity.denyByDefault();
            api.rule("/api/orders/**", Access.authenticated());
            api.send(Request.get("/api/orders/42").bearer(Token.forUser("alice").expired()));
            api.send(Request.get("/api/orders/42").bearer(Token.forUser("alice").forged()));
            api.send(Request.get("/api/orders/42")
                    .bearer(Token.forUser("alice").fromIssuer("https://evil.example.com")));
            api.send(Request.get("/api/orders/42")
                    .bearer(Token.forUser("alice").forAudience("billing-api")));
            api.report();
        });
        assertTrue(out.contains("\"reason\":\"expired\""), "expiry must be checked, got:\n" + out);
        assertTrue(out.contains("\"reason\":\"bad-signature\""), "the signature must be checked, got:\n" + out);
        assertTrue(out.contains("\"reason\":\"wrong-issuer\""), "the issuer must be checked, got:\n" + out);
        assertTrue(out.contains("\"reason\":\"wrong-audience\""), "the audience must be checked, got:\n" + out);
        assertFalse(out.contains("HANDLER_RAN"), "none of them may get through, got:\n" + out);
        assertTrue(out.contains("refused with 401 4"), "four 401s, got:\n" + out);
    }

    @Test
    void decodingWithoutVerifyingAcceptsAForgedToken() {
        String out = captureTrace(() -> {
            VisualEndpointSecurity api = VisualEndpointSecurity.denyByDefault();
            api.trustUnverifiedTokens();
            api.rule("/api/admin/**", Access.hasRole("ADMIN"));
            api.send(Request.get("/api/admin/users")
                    .bearer(Token.forUser("mallory").roles("ADMIN").forged()));
            api.report();
        });
        assertTrue(out.contains("TOKEN_NOT_VERIFIED"), "the forged token is accepted, got:\n" + out);
        assertTrue(out.contains("HANDLER_RAN"), "it reaches the handler, got:\n" + out);
        assertTrue(out.contains("served that should NOT have been 1"), "one breach, got:\n" + out);
    }

    @Test
    void aScopeIsCheckedSeparatelyFromTheRole() {
        String out = captureTrace(() -> {
            VisualEndpointSecurity api = VisualEndpointSecurity.denyByDefault();
            api.rule("POST", "/api/orders/**", Access.authenticated().scope("orders:write"));
            api.rule("GET", "/api/orders/**", Access.authenticated().scope("orders:read"));
            Token readOnly = Token.forUser("alice").roles("USER").scopes("orders:read");
            api.send(Request.get("/api/orders/42").bearer(readOnly));
            api.send(Request.post("/api/orders").bearer(readOnly));
            api.report();
        });
        assertTrue(out.contains("AUTHORIZED"), "the read is allowed, got:\n" + out);
        assertTrue(out.contains("SCOPE_DENIED"), "the write is not, got:\n" + out);
        assertTrue(out.contains("refused with 403/404 1"), "exactly one 403, got:\n" + out);
    }

    @Test
    void aRoleCheckAloneLetsOneUserReadAnothersRecord() {
        String out = captureTrace(() -> {
            VisualEndpointSecurity api = VisualEndpointSecurity.denyByDefault();
            api.rule("/api/orders/**", Access.authenticated());
            api.owner("/api/orders/42", "bob");
            api.send(Request.get("/api/orders/42").bearer(Token.forUser("alice").roles("USER")));
            api.report();
        });
        assertTrue(out.contains("OWNERSHIP_CHECK_MISSING"), "the object check is absent, got:\n" + out);
        assertTrue(out.contains("HANDLER_RAN"), "bob's order is served, got:\n" + out);
        assertTrue(out.contains("served that should NOT have been 1"), "one breach, got:\n" + out);
    }

    @Test
    void anOwnerOnlyRuleRefusesSomebodyElsesRecordWith404() {
        String out = captureTrace(() -> {
            VisualEndpointSecurity api = VisualEndpointSecurity.denyByDefault();
            api.rule("/api/orders/**", Access.authenticated().ownerOnly());
            api.owner("/api/orders/42", "bob");
            api.owner("/api/orders/7", "alice");
            Token alice = Token.forUser("alice").roles("USER");
            api.send(Request.get("/api/orders/42").bearer(alice));
            api.send(Request.get("/api/orders/7").bearer(alice));
            api.report();
        });
        assertTrue(out.contains("OWNERSHIP_DENIED"), "bob's order must be refused, got:\n" + out);
        assertTrue(out.contains("\"status\":404"), "404 hides existence, got:\n" + out);
        assertTrue(out.contains("HANDLER_RAN"), "her own order must be served, got:\n" + out);
        assertTrue(out.contains("served that should NOT have been 0"), "no breach, got:\n" + out);
    }

    @Test
    void anIdentityHeaderIsIgnoredUnlessTheApiTrustsIt() {
        String out = captureTrace(() -> {
            VisualEndpointSecurity api = VisualEndpointSecurity.denyByDefault();
            api.rule("/api/admin/**", Access.hasRole("ADMIN"));
            api.send(Request.get("/api/admin/users")
                    .header("X-User-Id", "mallory").header("X-User-Role", "ADMIN"));
            api.report();
        });
        assertTrue(out.contains("SPOOFED_IDENTITY_HEADER"), "the header must be noticed, got:\n" + out);
        assertTrue(out.contains("AUTHENTICATION_MISSING"), "and then ignored, got:\n" + out);
        assertFalse(out.contains("HANDLER_RAN"), "nothing may reach the handler, got:\n" + out);
    }

    @Test
    void trustingIdentityHeadersLetsAnyClientBeAnAdmin() {
        String out = captureTrace(() -> {
            VisualEndpointSecurity api = VisualEndpointSecurity.denyByDefault();
            api.trustIdentityHeaders();
            api.rule("/api/admin/**", Access.hasRole("ADMIN"));
            api.send(Request.get("/api/admin/users")
                    .header("X-User-Id", "mallory").header("X-User-Role", "ADMIN"));
            api.report();
        });
        assertTrue(out.contains("IDENTITY_HEADER_TRUSTED"), "the header is believed, got:\n" + out);
        assertTrue(out.contains("HANDLER_RAN"), "it reaches the handler, got:\n" + out);
        assertTrue(out.contains("served that should NOT have been 1"), "one breach, got:\n" + out);
    }

    @Test
    void plaintextIsRefusedAndTheCredentialCountsAsLeaked() {
        String out = captureTrace(() -> {
            VisualEndpointSecurity api = VisualEndpointSecurity.denyByDefault();
            api.rule("/api/orders/**", Access.authenticated());
            api.send(Request.get("/api/orders/42")
                    .bearer(Token.forUser("alice").roles("USER")).overPlainHttp());
            api.report();
        });
        assertTrue(out.contains("TRANSPORT_INSECURE"), "plain http must be refused, got:\n" + out);
        assertTrue(out.contains("credentials exposed on the wire 1"), "the token leaked, got:\n" + out);
        assertFalse(out.contains("AUTHENTICATED"), "the chain stops at gate zero, got:\n" + out);
    }

    @Test
    void aRateLimitStopsTheFourthAttempt() {
        String out = captureTrace(() -> {
            VisualEndpointSecurity api = VisualEndpointSecurity.denyByDefault();
            api.rule("POST", "/api/login", Access.permitAll());
            api.rateLimit(3);
            for (int i = 0; i < 4; i++) {
                api.send(Request.post("/api/login"));
            }
            api.report();
        });
        assertTrue(out.contains("RATE_LIMIT_SET"), "the limit must be announced, got:\n" + out);
        assertTrue(out.contains("RATE_LIMITED"), "the fourth attempt must be stopped, got:\n" + out);
        assertTrue(out.contains("rate limited 1"), "exactly one 429, got:\n" + out);
        assertTrue(out.contains("served 3"), "the first three go through, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualEndpointSecurity api = VisualEndpointSecurity.denyByDefault();
            api.rule("/api/health", Access.permitAll());
            api.rule("GET", "/api/orders/**", Access.authenticated().scope("orders:read").ownerOnly());
            api.rule("/api/admin/**", Access.hasRole("ADMIN"));
            api.owner("/api/orders/42", "bob");
            api.rateLimit(20);
            api.send(Request.get("/api/health"));
            api.send(Request.get("/api/orders/42"));
            api.send(Request.get("/api/orders/42").bearer(Token.forUser("alice").expired()));
            api.send(Request.get("/api/orders/42")
                    .bearer(Token.forUser("alice").roles("USER").scopes("orders:read")));
            api.send(Request.get("/api/admin/users")
                    .bearer(Token.forUser("alice").roles("USER").scopes("orders:read")));
            api.send(Request.get("/api/health").overPlainHttp());
            api.report();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
