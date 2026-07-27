package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualWebSocketTest {

    private static final String HOST = "chat.example.com";
    private static final String PATH = "/ws/chat";

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
    void theEndpointModelIsAnnouncedBeforeAnythingHappens() {
        String out = captureTrace(() -> VisualWebSocket.perConnection(HOST, PATH, "ChatEndpoint"));
        assertTrue(out.contains("SERVER_READY"), "expected the endpoint announcement, got:\n" + out);
        assertTrue(out.contains("\"model\":\"PER_CONNECTION\""),
                "the instancing model must be in the state, got:\n" + out);
    }

    @Test
    void theHandshakeIsAnOrdinaryGetAnsweredWith101() {
        String out = captureTrace(() -> {
            VisualWebSocket server = VisualWebSocket.perConnection(HOST, PATH, "ChatEndpoint");
            server.connect("tab-1");
        });
        assertTrue(out.contains("HANDSHAKE_REQUEST"), "the GET must be shown, got:\n" + out);
        assertTrue(out.contains("HANDSHAKE_ACCEPTED"), "it must be upgraded, got:\n" + out);
        assertTrue(out.contains("Upgrade: websocket"), "the upgrade header is the request, got:\n" + out);
        assertTrue(out.contains("HTTP/1.1 101 Switching Protocols"), "101 or nothing, got:\n" + out);
    }

    @Test
    void theAcceptHeaderIsComputedFromTheKeyAsRfc6455Says() {
        String out = captureTrace(() -> {
            VisualWebSocket server = VisualWebSocket.perConnection(HOST, PATH, "ChatEndpoint");
            server.connect("tab-1");
        });
        // The RFC 6455 example: base64("the sample nonce") hashed with the GUID.
        assertTrue(out.contains("Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ=="),
                "the first connection uses the RFC nonce, got:\n" + out);
        assertTrue(out.contains("Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo="),
                "the accept value must match the RFC, got:\n" + out);
    }

    @Test
    void anUncheckedOriginIsTheServersOwnProblem() {
        String out = captureTrace(() -> {
            VisualWebSocket server = VisualWebSocket.perConnection(HOST, PATH, "ChatEndpoint")
                    .withOriginCheck("https://app.example.com");
            server.connect("tab-1");
            server.connect("evil-tab", "https://evil.example.com");
            server.report();
        });
        assertTrue(out.contains("ORIGIN_CHECK_ON"), "the check must be announced, got:\n" + out);
        assertTrue(out.contains("HANDSHAKE_REJECTED"), "the foreign origin must be refused, got:\n" + out);
        assertTrue(out.contains("HTTP/1.1 403 Forbidden"), "it is still HTTP at that point, got:\n" + out);
        assertTrue(out.contains("handshakes accepted 1, rejected 1"), "one of each, got:\n" + out);
    }

    @Test
    void bothSidesMayWriteWheneverTheyLike() {
        String out = captureTrace(() -> {
            VisualWebSocket server = VisualWebSocket.perConnection(HOST, PATH, "ChatEndpoint");
            server.connect("tab-1");
            server.send("tab-1", "hi");
            server.push("tab-1", "welcome");
            server.push("tab-1", "someone joined");
            server.report();
        });
        assertTrue(out.contains("FRAME_FROM_CLIENT"), "the client writes, got:\n" + out);
        assertTrue(out.contains("FRAME_FROM_SERVER"), "the server writes too, got:\n" + out);
        assertTrue(out.contains("frames in 1, frames out 2"),
                "the server may write more than it was asked, got:\n" + out);
    }

    @Test
    void aTextFrameCostsAHeaderAndAMaskAndNothingElse() {
        String out = captureTrace(() -> {
            VisualWebSocket server = VisualWebSocket.perConnection(HOST, PATH, "ChatEndpoint");
            server.connect("tab-1");
            server.send("tab-1", "hi");
            server.push("tab-1", "hi");
        });
        // 2 header + 4 mask + 2 payload up, 2 header + 2 payload down.
        assertTrue(out.contains("8 bytes on the wire"), "a masked frame costs 8, got:\n" + out);
        assertTrue(out.contains("4 bytes, unmasked"), "an unmasked one costs 4, got:\n" + out);
    }

    @Test
    void aPerConnectionEndpointGetsOneObjectPerSocket() {
        String out = captureTrace(() -> {
            VisualWebSocket server = VisualWebSocket.perConnection(HOST, PATH, "ChatEndpoint");
            server.connect("tab-1");
            server.rememberInField("tab-1", "user", "ada");
            server.connect("tab-2");
            server.rememberInField("tab-2", "user", "omar");
            server.readField("tab-1", "user");
            server.report();
        });
        assertTrue(out.contains("ENDPOINT_INSTANTIATED"), "an object per connection, got:\n" + out);
        assertTrue(out.contains("ChatEndpoint#2"), "the second connection gets its own, got:\n" + out);
        assertTrue(out.contains("FIELD_READ"), "the field reads back correctly, got:\n" + out);
        assertFalse(out.contains("STATE_LEAKED"), "nothing may leak here, got:\n" + out);
        assertTrue(out.contains("endpoint objects created 2"), "two objects for two tabs, got:\n" + out);
        assertTrue(out.contains("state collisions 0"), "the fields cannot collide, got:\n" + out);
    }

    @Test
    void aSharedHandlerServesEveryConnectionWithOneObject() {
        String out = captureTrace(() -> {
            VisualWebSocket server = VisualWebSocket.shared(HOST, PATH, "ChatHandler");
            server.connect("tab-1");
            server.connect("tab-2");
            server.report();
        });
        assertTrue(out.contains("ENDPOINT_SHARED"), "no object is created, got:\n" + out);
        assertTrue(out.contains("ChatHandler@singleton"), "one singleton serves both, got:\n" + out);
        assertFalse(out.contains("ENDPOINT_INSTANTIATED"), "nothing is instantiated, got:\n" + out);
        assertTrue(out.contains("endpoint objects created 1"), "exactly one object, got:\n" + out);
    }

    @Test
    void aFieldOnASharedHandlerIsOneUserSeeingAnother() {
        String out = captureTrace(() -> {
            VisualWebSocket server = VisualWebSocket.shared(HOST, PATH, "ChatHandler");
            server.connect("tab-1");
            server.rememberInField("tab-1", "user", "ada");
            server.connect("tab-2");
            server.rememberInField("tab-2", "user", "omar");
            String seen = server.readField("tab-1", "user");
            assertEquals("omar", seen, "tab-1 must read tab-2's value from the shared field");
            server.report();
        });
        assertTrue(out.contains("STATE_CLASHED"), "the second write must collide, got:\n" + out);
        assertTrue(out.contains("STATE_LEAKED"), "the read must return the wrong user, got:\n" + out);
        assertTrue(out.contains("state collisions 1, wrong-client reads 1"),
                "one collision and one wrong read, got:\n" + out);
    }

    @Test
    void sessionScopedStateIsCorrectInBothModels() {
        String shared = captureTrace(() -> {
            VisualWebSocket server = VisualWebSocket.shared(HOST, PATH, "ChatHandler");
            server.connect("tab-1");
            server.rememberInSession("tab-1", "user", "ada");
            server.connect("tab-2");
            server.rememberInSession("tab-2", "user", "omar");
            server.report();
        });
        assertTrue(shared.contains("SESSION_STATE_STORED"), "the session must hold it, got:\n" + shared);
        assertTrue(shared.contains("state collisions 0"), "sessions cannot collide, got:\n" + shared);
        assertFalse(shared.contains("STATE_CLASHED"), "nothing may clash, got:\n" + shared);
    }

    @Test
    void broadcastIsALoopOverSocketsYouKeepYourself() {
        String out = captureTrace(() -> {
            VisualWebSocket server = VisualWebSocket.shared(HOST, PATH, "ChatHandler");
            server.connect("tab-1");
            server.connect("tab-2");
            server.connect("tab-3");
            server.close("tab-3", 1000, "normal closure");
            server.broadcast("ada: hello");
            server.report();
        });
        assertTrue(out.contains("BROADCAST"), "expected the fan-out, got:\n" + out);
        assertTrue(out.contains("written to 2 socket(s)"), "the closed one is skipped, got:\n" + out);
        assertTrue(out.contains("frames out 3"), "two broadcast frames plus one close, got:\n" + out);
    }

    @Test
    void aCleanCloseIsAHandshakeAndADropIsNot() {
        String out = captureTrace(() -> {
            VisualWebSocket server = VisualWebSocket.perConnection(HOST, PATH, "ChatEndpoint");
            server.connect("tab-1");
            server.connect("tab-2");
            server.close("tab-1", 1000, "normal closure");
            server.drop("tab-2");
            server.report();
        });
        assertTrue(out.contains("CLOSE_HANDSHAKE"), "a close frame each way, got:\n" + out);
        assertTrue(out.contains("CONNECTION_DROPPED"), "1006 has no frame, got:\n" + out);
        assertTrue(out.contains("\"closeCode\":1006"), "the drop is reported as 1006, got:\n" + out);
        assertTrue(out.contains("clean closes 1, abnormal drops 1"), "one of each, got:\n" + out);
        assertTrue(out.contains("sockets still open 0"), "both are gone, got:\n" + out);
    }

    @Test
    void aFrameWrittenIntoADeadSocketIsSimplyGone() {
        String out = captureTrace(() -> {
            VisualWebSocket server = VisualWebSocket.perConnection(HOST, PATH, "ChatEndpoint");
            server.connect("tab-1");
            server.drop("tab-1");
            server.push("tab-1", "your order shipped");
            server.report();
        });
        assertTrue(out.contains("FRAME_LOST"), "the frame must be lost, got:\n" + out);
        assertTrue(out.contains("frames written to a dead socket 1"), "counted once, got:\n" + out);
    }

    @Test
    void keepaliveAndTransportAreSpelledOut() {
        String out = captureTrace(() -> {
            VisualWebSocket server = VisualWebSocket.perConnection(HOST, PATH, "ChatEndpoint").secure();
            server.connect("tab-1");
            server.ping("tab-1");
            server.transportFacts();
        });
        assertTrue(out.contains("TLS_ENABLED"), "wss must be announced, got:\n" + out);
        assertTrue(out.contains("PING_PONG"), "the heartbeat must be visible, got:\n" + out);
        assertTrue(out.contains("TRANSPORT_EXPLAINED"), "the transport must be stated, got:\n" + out);
        assertTrue(out.contains("\"protocol\":\"TCP\",\"port\":443,\"tls\":true"),
                "wss is TLS over TCP on 443, got:\n" + out);
    }

    @Test
    void theComparisonPricesTheThreeInstancingChoices() {
        String out = captureTrace(VisualWebSocket::compareInstancing);
        assertTrue(out.contains("INSTANCING_COMPARED"), "expected the comparison, got:\n" + out);
        assertTrue(out.contains("\"model\":\"JSR356_ENDPOINT\",\"instances\":3,\"fieldPerConnection\":true"),
                "a plain @ServerEndpoint gives three objects, got:\n" + out);
        assertTrue(out.contains("\"model\":\"SPRING_HANDLER\",\"instances\":1,\"fieldPerConnection\":false"),
                "a Spring handler is a singleton, got:\n" + out);
        assertTrue(out.contains("\"needsSync\":true,\"injection\":true,\"stateLives\":\"SESSION\""),
                "a shared object needs synchronisation and session-scoped state, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualWebSocket perConnection = VisualWebSocket.perConnection(HOST, PATH, "ChatEndpoint")
                    .secure()
                    .withOriginCheck("https://app.example.com");
            perConnection.connect("tab-1");
            perConnection.connect("evil-tab", "https://evil.example.com");
            perConnection.send("tab-1", "hi");
            perConnection.sendBinary("tab-1", "avatar.png", 4096);
            perConnection.push("tab-1", "welcome");
            perConnection.ping("tab-1");
            perConnection.rememberInField("tab-1", "user", "ada");
            perConnection.readField("tab-1", "user");
            perConnection.rememberInSession("tab-1", "user", "ada");
            perConnection.transportFacts();
            perConnection.close("tab-1", 1000, "normal closure");
            perConnection.push("tab-1", "too late");
            perConnection.report();

            VisualWebSocket shared = VisualWebSocket.shared(HOST, PATH, "ChatHandler");
            shared.connect("tab-1");
            shared.connect("tab-2");
            shared.rememberInField("tab-1", "user", "ada");
            shared.rememberInField("tab-2", "user", "omar");
            shared.readField("tab-1", "user");
            shared.broadcast("ada: hello");
            shared.drop("tab-2");
            shared.report();

            VisualWebSocket.compareInstancing();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
