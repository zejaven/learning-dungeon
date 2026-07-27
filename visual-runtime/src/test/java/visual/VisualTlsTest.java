package visual;

import org.junit.jupiter.api.Test;
import visual.VisualTls.Certificate;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualTlsTest {

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
    void aFreshClientTrustsOnlyItsPreinstalledRoots() {
        String out = captureTrace(VisualTls::browser);
        assertTrue(out.contains("TLS_CLIENT"), "expected a client event, got:\n" + out);
        assertTrue(out.contains(VisualTls.ROOT_CA), "the trust store must be visible, got:\n" + out);
        assertTrue(out.contains("\"source\":\"preinstalled\""),
                "roots start out preinstalled, got:\n" + out);
    }

    @Test
    void aCertificateIsAPublicDocumentAboutANameAndAPublicKey() {
        Certificate cert = VisualTls.certificateFor("shop.example", "www.shop.example");
        String out = captureTrace(() -> VisualTls.browser().inspect(cert));
        assertTrue(out.contains("CERT_INSPECTED"), "expected an inspect event, got:\n" + out);
        assertEquals("shop.example", cert.subject());
        assertEquals(VisualTls.INTERMEDIATE_CA, cert.issuer());
        assertEquals(2, cert.names().size(), "both SAN names belong to the certificate");
        assertTrue(cert.hasPrivateKey(), "the real server holds the matching private key");
    }

    @Test
    void aGoodChainVerifiesAndTheHandshakeCompletes() {
        String out = captureTrace(() -> {
            VisualTls browser = VisualTls.browser();
            browser.connect("shop.example", VisualTls.certificateFor("shop.example"));
        });
        assertTrue(out.contains("CLIENT_HELLO"), "expected a hello, got:\n" + out);
        assertTrue(out.contains("CERT_PRESENTED"), "expected the chain to be sent, got:\n" + out);
        assertTrue(out.contains("CHAIN_VERIFIED"), "the chain must verify, got:\n" + out);
        assertTrue(out.contains("POSSESSION_PROVEN"), "possession must be proven, got:\n" + out);
        assertTrue(out.contains("KEY_EXCHANGE"), "keys must be agreed, got:\n" + out);
        assertTrue(out.contains("SESSION_KEYS_DERIVED"), "session keys must appear, got:\n" + out);
        assertTrue(out.contains("HANDSHAKE_DONE"), "the handshake must finish, got:\n" + out);
        assertTrue(out.contains("\"decision\":\"secure\""), "the outcome must be secure, got:\n" + out);
    }

    @Test
    void aSelfSignedCertificateEncryptsJustAsWellAndProvesNothing() {
        String out = captureTrace(() -> VisualTls.browser()
                .connect("shop.example", VisualTls.selfSignedCertificateFor("shop.example")));
        assertTrue(out.contains("CHAIN_UNTRUSTED"), "the chain must be refused, got:\n" + out);
        assertFalse(out.contains("HANDSHAKE_DONE"), "no session may be established, got:\n" + out);
        assertTrue(out.contains("\"reason\":\"untrusted-issuer\""),
                "the reason must be the issuer, got:\n" + out);
    }

    @Test
    void aMissingIntermediateBreaksThePathEvenThoughEverySignatureIsFine() {
        String out = captureTrace(() -> VisualTls.browser()
                .connect("shop.example", VisualTls.chainWithoutIntermediate("shop.example")));
        assertTrue(out.contains("CHAIN_INCOMPLETE"), "expected an incomplete chain, got:\n" + out);
        assertFalse(out.contains("CHAIN_VERIFIED"), "the path cannot be built, got:\n" + out);
    }

    @Test
    void aCertificateForAnotherNameIsRefused() {
        String out = captureTrace(() -> VisualTls.browser()
                .connect("shop.example", VisualTls.certificateFor("other.example")));
        assertTrue(out.contains("HOSTNAME_MISMATCH"), "the name must be checked, got:\n" + out);
        assertTrue(out.contains("\"reason\":\"hostname-mismatch\""),
                "the reason must be the name, got:\n" + out);
    }

    @Test
    void aWildcardCoversOneLabelAndNotTwo() {
        String out = captureTrace(() -> {
            VisualTls browser = VisualTls.browser();
            Certificate wildcard = VisualTls.certificateFor("*.shop.example");
            browser.connect("api.shop.example", wildcard);
            browser.connect("eu.api.shop.example", wildcard);
        });
        assertTrue(out.contains("HANDSHAKE_DONE"), "one label must match, got:\n" + out);
        assertTrue(out.contains("HOSTNAME_MISMATCH"), "two labels must not match, got:\n" + out);
    }

    @Test
    void anExpiredCertificateStopsAWorkingSite() {
        String out = captureTrace(() -> VisualTls.browser()
                .connect("shop.example", VisualTls.expiredCertificateFor("shop.example")));
        assertTrue(out.contains("CERT_EXPIRED"), "expiry must be checked, got:\n" + out);
        assertFalse(out.contains("HANDSHAKE_DONE"), "an expired certificate is fatal, got:\n" + out);
    }

    @Test
    void timePassingIsEnoughToTakeASiteOffTheAir() {
        String out = captureTrace(() -> {
            VisualTls browser = VisualTls.browser();
            Certificate cert = VisualTls.certificateFor("shop.example");
            browser.connect("shop.example", cert);
            browser.advanceDays(60);
            browser.connect("shop.example", cert);
        });
        assertTrue(out.contains("TIME_PASSED"), "the calendar must move, got:\n" + out);
        assertTrue(out.contains("CERT_EXPIRED"), "the same certificate must expire, got:\n" + out);
    }

    @Test
    void aRevokedCertificateIsRefusedInsideItsDates() {
        String out = captureTrace(() -> VisualTls.browser()
                .connect("shop.example", VisualTls.revokedCertificateFor("shop.example")));
        assertTrue(out.contains("CERT_REVOKED"), "revocation must be checked, got:\n" + out);
    }

    @Test
    void copyingACertificateGetsYouNothingWithoutThePrivateKey() {
        String out = captureTrace(() -> {
            Certificate genuine = VisualTls.certificateFor("shop.example");
            VisualTls.browser().connect("shop.example", VisualTls.copyOf(genuine));
        });
        assertTrue(out.contains("CHAIN_VERIFIED"), "the copied content is genuine, got:\n" + out);
        assertTrue(out.contains("POSSESSION_FAILED"), "there is no private key, got:\n" + out);
        assertFalse(out.contains("HANDSHAKE_DONE"), "the impostor must not get a session, got:\n" + out);
    }

    @Test
    void anInstalledRootTurnsAManInTheMiddleIntoAValidPadlock() {
        String out = captureTrace(() -> {
            VisualTls browser = VisualTls.browser();
            Certificate rogue = VisualTls.certificateFromRoot("shop.example", "Cafe Wi-Fi CA");
            browser.connect("shop.example", rogue);
            browser.installRoot("Cafe Wi-Fi CA");
            browser.connect("shop.example", rogue);
            browser.send("POST /login password=hunter2");
        });
        assertTrue(out.contains("CHAIN_UNTRUSTED"), "an unknown root must be refused, got:\n" + out);
        assertTrue(out.contains("ROOT_INSTALLED"), "the root must be installable, got:\n" + out);
        assertTrue(out.contains("MITM_ACCEPTED"), "the same chain must now pass, got:\n" + out);
        assertTrue(out.contains("\"decision\":\"breach\""), "that is a breach, got:\n" + out);
        assertTrue(out.contains("\"exposed\":true"),
                "the interceptor reads what it forwards, got:\n" + out);
    }

    @Test
    void applicationDataBecomesAeadRecords() {
        String out = captureTrace(() -> {
            VisualTls browser = VisualTls.browser();
            browser.connect("shop.example", VisualTls.certificateFor("shop.example"));
            browser.send("GET /orders/42");
        });
        assertTrue(out.contains("RECORD_ENCRYPTED"), "the request must be encrypted, got:\n" + out);
        assertTrue(out.contains("AES-256-GCM"), "the record cipher must be visible, got:\n" + out);
        assertTrue(out.contains("\"intact\":true"), "a fresh record is intact, got:\n" + out);
    }

    @Test
    void tamperingWithARecordTearsTheConnectionDown() {
        String out = captureTrace(() -> {
            VisualTls browser = VisualTls.browser();
            browser.connect("shop.example", VisualTls.certificateFor("shop.example"));
            browser.send("POST /transfer amount=10");
            browser.tamperInFlight();
        });
        assertTrue(out.contains("RECORD_TAMPERED"), "the tag must be checked, got:\n" + out);
        assertTrue(out.contains("\"intact\":false"), "the record must be marked broken, got:\n" + out);
        assertTrue(out.contains("\"reason\":\"tampered\""), "the reason must be tampering, got:\n" + out);
    }

    @Test
    void plainHttpPutsTheRequestOnTheWireAsItIs() {
        String out = captureTrace(() -> VisualTls.browser().sendWithoutTls("GET /orders/42"));
        assertTrue(out.contains("PLAINTEXT_ON_THE_WIRE"), "expected plaintext, got:\n" + out);
        assertTrue(out.contains("\"exposed\":true"), "plaintext is exposed, got:\n" + out);
    }

    @Test
    void ephemeralKeysKeepARecordingUnreadableAfterTheKeyLeaks() {
        String out = captureTrace(() -> {
            VisualTls browser = VisualTls.browser();
            browser.connect("shop.example", VisualTls.certificateFor("shop.example"));
            browser.send("GET /account");
            browser.eavesdropperRecords();
            browser.stealPrivateKeyLater();
        });
        assertTrue(out.contains("TRAFFIC_RECORDED"), "the traffic must be recorded, got:\n" + out);
        assertTrue(out.contains("PRIVATE_KEY_STOLEN"), "the key must leak, got:\n" + out);
        assertTrue(out.contains("FORWARD_SECRECY_HELD"), "the recording must stay shut, got:\n" + out);
        assertFalse(out.contains("RECORDING_DECRYPTED"), "nothing may open up, got:\n" + out);
    }

    @Test
    void rsaKeyTransportOpensEveryRecordedSessionAtOnce() {
        String out = captureTrace(() -> {
            VisualTls browser = VisualTls.browser().useRsaKeyTransport();
            browser.connect("shop.example", VisualTls.certificateFor("shop.example"));
            browser.send("GET /account");
            browser.eavesdropperRecords();
            browser.stealPrivateKeyLater();
            browser.report();
        });
        assertTrue(out.contains("CIPHER_CONFIGURED"), "the key exchange must change, got:\n" + out);
        assertTrue(out.contains("\"forwardSecrecy\":false"),
                "there is no forward secrecy here, got:\n" + out);
        assertTrue(out.contains("RECORDING_DECRYPTED"), "the recording must open, got:\n" + out);
        assertTrue(out.contains("TLS_AUDIT"), "expected the audit, got:\n" + out);
    }
}
