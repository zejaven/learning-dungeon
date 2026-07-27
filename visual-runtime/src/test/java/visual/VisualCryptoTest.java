package visual;

import org.junit.jupiter.api.Test;
import visual.VisualCrypto.Envelope;
import visual.VisualCrypto.KeyPair;
import visual.VisualCrypto.SecretKey;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualCryptoTest {

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
    void aSecretKeyIsOneKeyHeldByEverybodyWhoReads() {
        String out = captureTrace(() -> {
            VisualCrypto lab = VisualCrypto.lab();
            SecretKey key = lab.generateSecretKey("orders-key", "Alice", "Bob");
            assertEquals("orders-key", key.id());
        });
        assertTrue(out.contains("CRYPTO_LAB"), "expected the lab event, got:\n" + out);
        assertTrue(out.contains("SECRET_KEY_GENERATED"), "expected a secret key, got:\n" + out);
        assertTrue(out.contains("\"kind\":\"secret\""), "the key must be a secret, got:\n" + out);
        assertTrue(out.contains("\"holders\":[\"Alice\",\"Bob\"]"),
                "both parties hold the same key, got:\n" + out);
    }

    @Test
    void aKeyPairIsTwoKeysAndOnlyOneOfThemIsPublishable() {
        String out = captureTrace(() -> {
            VisualCrypto lab = VisualCrypto.lab();
            KeyPair bob = lab.generateKeyPair("Bob");
            lab.publish(bob);
            assertEquals("Bob/public", bob.publicKeyId());
            assertEquals("Bob/private", bob.privateKeyId());
        });
        assertTrue(out.contains("KEY_PAIR_GENERATED"), "expected a key pair, got:\n" + out);
        assertTrue(out.contains("PUBLIC_KEY_PUBLISHED"), "the public half must publish, got:\n" + out);
        assertTrue(out.contains("\"kind\":\"public\""), "expected a public key, got:\n" + out);
        assertTrue(out.contains("\"kind\":\"private\""), "expected a private key, got:\n" + out);
        assertTrue(out.contains("\"holders\":[\"everyone\"]"),
                "the published half belongs to everyone, got:\n" + out);
    }

    @Test
    void theSameSecretEncryptsAndDecrypts() {
        String out = captureTrace(() -> {
            VisualCrypto lab = VisualCrypto.lab();
            SecretKey key = lab.generateSecretKey("orders-key", "Alice", "Bob");
            Envelope envelope = lab.encrypt(key, "transfer 100 to 42");
            assertEquals("symmetric", envelope.family());
            assertEquals("transfer 100 to 42", lab.decrypt(key, envelope));
        });
        assertTrue(out.contains("SYMMETRIC_ENCRYPT"), "expected encryption, got:\n" + out);
        assertTrue(out.contains("SYMMETRIC_DECRYPT"), "expected decryption, got:\n" + out);
        assertTrue(out.contains(VisualCrypto.SYMMETRIC_ALGORITHM),
                "the cipher must be visible, got:\n" + out);
    }

    @Test
    void anotherPerfectlyGoodSecretDecryptsNothing() {
        String out = captureTrace(() -> {
            VisualCrypto lab = VisualCrypto.lab();
            SecretKey ours = lab.generateSecretKey("orders-key", "Alice", "Bob");
            SecretKey theirs = lab.generateSecretKey("billing-key", "Carol");
            Envelope envelope = lab.encrypt(ours, "transfer 100 to 42");
            assertNull(lab.decrypt(theirs, envelope));
        });
        assertTrue(out.contains("WRONG_KEY"), "the wrong key must be refused, got:\n" + out);
        assertTrue(out.contains("\"reason\":\"wrong-key\""), "expected the reason, got:\n" + out);
    }

    @Test
    void sendingTheSharedKeyOverTheSameChannelHandsItToTheAttacker() {
        String out = captureTrace(() -> {
            VisualCrypto lab = VisualCrypto.lab();
            SecretKey key = lab.generateSecretKey("orders-key", "Alice");
            Envelope envelope = lab.encrypt(key, "transfer 100 to 42");
            lab.shareKeyInTheOpen(key, "Bob");
            lab.attackerTries(envelope);
        });
        assertTrue(out.contains("KEY_SHARED_IN_THE_OPEN"), "the key must travel, got:\n" + out);
        assertTrue(out.contains("ATTACKER_READS"), "the attacker must win, got:\n" + out);
        assertTrue(out.contains("\"reason\":\"attacker-has-the-key\""),
                "the key was the failure, got:\n" + out);
    }

    @Test
    void theKeyCountGrowsQuadraticallyForSecretsAndLinearlyForPairs() {
        String out = captureTrace(() -> VisualCrypto.lab().keyDistributionCost(50));
        assertTrue(out.contains("KEY_DISTRIBUTION_COST"), "expected the cost event, got:\n" + out);
        assertTrue(out.contains("\"secretKeys\":1225"), "50 parties need 1225 secrets, got:\n" + out);
        assertTrue(out.contains("\"keyPairs\":50"), "50 parties need 50 pairs, got:\n" + out);
    }

    @Test
    void thePublicHalfSealsAndOnlyThePrivateHalfOpens() {
        String out = captureTrace(() -> {
            VisualCrypto lab = VisualCrypto.lab();
            KeyPair bob = lab.generateKeyPair("Bob");
            lab.publish(bob);
            Envelope envelope = lab.encryptFor(bob, "my password is hunter2");
            lab.attackerTries(envelope);
            assertEquals("my password is hunter2", lab.decryptWith(bob, envelope));
        });
        assertTrue(out.contains("ASYMMETRIC_ENCRYPT"), "expected encryption, got:\n" + out);
        assertTrue(out.contains("ATTACKER_STUCK"), "the public key must not open it, got:\n" + out);
        assertTrue(out.contains("\"reason\":\"public-key-cannot-decrypt\""),
                "expected the reason, got:\n" + out);
        assertTrue(out.contains("ASYMMETRIC_DECRYPT"), "the private half must open it, got:\n" + out);
    }

    @Test
    void signingProvesAuthorshipAndHidesNothing() {
        String out = captureTrace(() -> {
            VisualCrypto lab = VisualCrypto.lab();
            KeyPair alice = lab.generateKeyPair("Alice");
            lab.publish(alice);
            Envelope invoice = lab.sign(alice, "invoice 42: 100 EUR");
            assertTrue(lab.verify(alice, invoice));
            lab.attackerTries(invoice);
        });
        assertTrue(out.contains("MESSAGE_SIGNED"), "expected a signature, got:\n" + out);
        assertTrue(out.contains("SIGNATURE_VERIFIED"), "the signature must verify, got:\n" + out);
        assertTrue(out.contains("ATTACKER_READS"), "a signed message is readable, got:\n" + out);
        assertTrue(out.contains("\"reason\":\"not-encrypted\""),
                "signing is not encryption, got:\n" + out);
    }

    @Test
    void aChangedMessageFailsVerificationAndCannotBeResigned() {
        String out = captureTrace(() -> {
            VisualCrypto lab = VisualCrypto.lab();
            KeyPair alice = lab.generateKeyPair("Alice");
            lab.publish(alice);
            Envelope invoice = lab.sign(alice, "invoice 42: 100 EUR");
            lab.tamper(invoice);
            assertFalse(lab.verify(alice, invoice));
        });
        assertTrue(out.contains("MESSAGE_TAMPERED"), "the message must change, got:\n" + out);
        assertTrue(out.contains("SIGNATURE_INVALID"), "verification must fail, got:\n" + out);
        assertTrue(out.contains("\"intact\":false"), "the record must be marked, got:\n" + out);
    }

    @Test
    void anAsymmetricOperationCannotSwallowBulkData() {
        String out = captureTrace(() -> {
            VisualCrypto lab = VisualCrypto.lab();
            KeyPair bob = lab.generateKeyPair("Bob");
            lab.publish(bob);
            assertNull(lab.encryptBlobFor(bob, "report.pdf", 1_048_576));
            lab.compareCost("report.pdf", 1_048_576);
        });
        assertTrue(out.contains("PAYLOAD_TOO_LARGE"), "the ceiling must bite, got:\n" + out);
        assertTrue(out.contains("\"reason\":\"payload-too-large\""), "expected the reason, got:\n" + out);
        assertTrue(out.contains("PERFORMANCE_COMPARED"), "expected the comparison, got:\n" + out);
        assertTrue(out.contains("\"symmetricMicros\":699"),
                "one pass over 1 MiB is cheap, got:\n" + out);
        assertTrue(out.contains("\"asymmetricBlocks\":5519"),
                "RSA would need thousands of blocks, got:\n" + out);
    }

    @Test
    void theHybridPatternSpendsTheExpensiveAlgorithmOnceOnTheKey() {
        String out = captureTrace(() -> {
            VisualCrypto lab = VisualCrypto.lab();
            KeyPair bob = lab.generateKeyPair("Bob");
            lab.publish(bob);
            Envelope envelope = lab.sendHybridBlob(bob, "report.pdf", 1_048_576);
            assertEquals("hybrid", envelope.family());
            assertEquals("report.pdf", lab.openHybrid(bob, envelope));
            lab.report();
        });
        assertTrue(out.contains("SESSION_KEY_WRAPPED"), "the session key must be wrapped, got:\n" + out);
        assertTrue(out.contains("HYBRID_SENT"), "one envelope must leave, got:\n" + out);
        assertTrue(out.contains("SESSION_KEY_UNWRAPPED"), "the key must be unwrapped, got:\n" + out);
        assertTrue(out.contains("SYMMETRIC_DECRYPT"), "the bulk stays symmetric, got:\n" + out);
        assertTrue(out.contains("\"asymmetricOps\":2"),
                "exactly two asymmetric operations, got:\n" + out);
        assertTrue(out.contains("CRYPTO_AUDIT"), "expected the audit, got:\n" + out);
    }

    @Test
    void aLeakedPrivateKeyOpensEverythingAddressedToIt() {
        String out = captureTrace(() -> {
            VisualCrypto lab = VisualCrypto.lab();
            KeyPair bob = lab.generateKeyPair("Bob");
            lab.publish(bob);
            Envelope envelope = lab.sendHybrid(bob, "quarterly numbers");
            lab.attackerTries(envelope);
            lab.leakPrivateKey(bob);
            lab.attackerTries(envelope);
        });
        assertTrue(out.contains("ATTACKER_STUCK"), "it must hold at first, got:\n" + out);
        assertTrue(out.contains("PRIVATE_KEY_LEAKED"), "the key must leak, got:\n" + out);
        assertTrue(out.contains("ATTACKER_READS"), "the recording must open, got:\n" + out);
        assertTrue(out.contains("\"compromised\":true"),
                "the key must be marked compromised, got:\n" + out);
    }
}
