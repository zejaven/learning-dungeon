package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualPushChannelTest {

    private static final String APP = "dashboard";

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
    void choosingATransportIsAnnouncedBeforeAnythingHappens() {
        String out = captureTrace(() -> VisualPushChannel.shortPolling(APP, 5));
        assertTrue(out.contains("CHANNEL_OPENED"), "expected the transport choice, got:\n" + out);
        assertTrue(out.contains("SHORT_POLL"), "the transport must be in the state, got:\n" + out);
    }

    @Test
    void withNothingOpenTheNewsHasToWaitForTheNextPoll() {
        String out = captureTrace(() -> {
            VisualPushChannel channel = VisualPushChannel.shortPolling(APP, 4);
            channel.tick(1);
            channel.serverEvent("order-42 paid");
            channel.tick(3);
            channel.report();
        });
        assertTrue(out.contains("SERVER_EVENT"), "the event must happen, got:\n" + out);
        assertTrue(out.contains("EVENT_WAITING"), "it must have nowhere to go, got:\n" + out);
        assertTrue(out.contains("POLL_DELIVERED"), "the poll must pick it up, got:\n" + out);
        assertTrue(out.contains("\"latency\":3"), "it waited three ticks, got:\n" + out);
    }

    @Test
    void everyPollThatFindsNothingIsStillAFullRoundTrip() {
        String out = captureTrace(() -> {
            VisualPushChannel channel = VisualPushChannel.shortPolling(APP, 2);
            channel.tick(6);
            channel.report();
        });
        assertTrue(out.contains("POLL_EMPTY"), "empty polls must be visible, got:\n" + out);
        assertTrue(out.contains("HTTP requests 3 (of which 3 came back with nothing)"),
                "three requests, all empty, got:\n" + out);
        assertTrue(out.contains("events delivered 0"), "nothing was delivered, got:\n" + out);
    }

    @Test
    void aParkedLongPollAnswersTheMomentTheEventExists() {
        String out = captureTrace(() -> {
            VisualPushChannel channel = VisualPushChannel.longPolling(APP, 8);
            channel.tick(3);
            channel.serverEvent("order-42 paid");
            channel.report();
        });
        assertTrue(out.contains("LONGPOLL_HELD"), "the request must be parked, got:\n" + out);
        assertTrue(out.contains("LONGPOLL_DELIVERED"), "it must answer on the event, got:\n" + out);
        assertTrue(out.contains("LONGPOLL_REOPENED"), "the client must ask again, got:\n" + out);
        assertTrue(out.contains("average delay 0.0 tick(s)"), "delivery is immediate, got:\n" + out);
    }

    @Test
    void anIdleLongPollExpiresOnPurposeAndIsReopened() {
        String out = captureTrace(() -> {
            VisualPushChannel channel = VisualPushChannel.longPolling(APP, 5);
            channel.tick(10);
            channel.report();
        });
        assertTrue(out.contains("LONGPOLL_TIMEOUT"), "the hold must expire, got:\n" + out);
        assertTrue(out.contains("HTTP requests 3 (of which 2 came back with nothing)"),
                "two timeouts and three opened requests, got:\n" + out);
    }

    @Test
    void anOpenStreamDeliversWithoutASingleExtraRequest() {
        String out = captureTrace(() -> {
            VisualPushChannel channel = VisualPushChannel.sse(APP);
            channel.tick(2);
            channel.serverEvent("order-42 paid");
            channel.tick(2);
            channel.serverEvent("order-42 packed");
            channel.report();
        });
        assertTrue(out.contains("SSE_STREAM_OPENED"), "the stream must open, got:\n" + out);
        assertTrue(out.contains("SSE_MESSAGE"), "messages must be written into it, got:\n" + out);
        assertTrue(out.contains("HTTP requests 1"), "one request for the whole run, got:\n" + out);
        assertTrue(out.contains("average delay 0.0 tick(s)"), "no waiting at all, got:\n" + out);
    }

    @Test
    void sseReplaysWhatWasMissedUsingLastEventId() {
        String out = captureTrace(() -> {
            VisualPushChannel channel = VisualPushChannel.sse(APP);
            channel.tick(1);
            channel.serverEvent("order-42 paid");
            channel.goOffline();
            channel.tick(2);
            channel.serverEvent("order-42 shipped");
            channel.goOnline();
            channel.report();
        });
        assertTrue(out.contains("CONNECTION_LOST"), "the stream must die, got:\n" + out);
        assertTrue(out.contains("SSE_REPLAYED"), "the gap must be replayed, got:\n" + out);
        assertTrue(out.contains("Last-Event-ID: e1"), "the resume point must be sent, got:\n" + out);
        assertTrue(out.contains("never delivered 0"), "nothing may be lost, got:\n" + out);
    }

    @Test
    void aWebSocketCarriesFramesInBothDirections() {
        String out = captureTrace(() -> {
            VisualPushChannel channel = VisualPushChannel.webSocket("chat");
            channel.tick(1);
            channel.serverEvent("ada: hello");
            channel.sendFromClient("omar: hi");
            channel.report();
        });
        assertTrue(out.contains("WS_HANDSHAKE"), "the upgrade must happen, got:\n" + out);
        assertTrue(out.contains("WS_FRAME_DOWN"), "the server must push, got:\n" + out);
        assertTrue(out.contains("WS_FRAME_UP"), "the client must send on the same wire, got:\n" + out);
        assertTrue(out.contains("HTTP requests 1"), "only the handshake was HTTP, got:\n" + out);
    }

    @Test
    void aReconnectedSocketHasNothingToReplay() {
        String out = captureTrace(() -> {
            VisualPushChannel channel = VisualPushChannel.webSocket("chat");
            channel.tick(1);
            channel.goOffline();
            channel.serverEvent("ada: are you there?");
            channel.tick(2);
            channel.goOnline();
            channel.report();
        });
        assertTrue(out.contains("MESSAGES_MISSED"), "the frame must be lost, got:\n" + out);
        assertFalse(out.contains("SSE_REPLAYED"), "a raw socket has no replay, got:\n" + out);
        assertTrue(out.contains("never delivered 1"), "one message never arrived, got:\n" + out);
    }

    @Test
    void aSecondSideChannelIsNeededToSendOverSse() {
        String out = captureTrace(() -> {
            VisualPushChannel channel = VisualPushChannel.sse(APP);
            channel.sendFromClient("mark as read");
            channel.report();
        });
        assertTrue(out.contains("CLIENT_MESSAGE_POSTED"), "the way back is a request, got:\n" + out);
        assertTrue(out.contains("HTTP requests 2"), "the stream plus one POST, got:\n" + out);
    }

    @Test
    void anOpenConnectionLivesInOneInstanceOnly() {
        String out = captureTrace(() -> {
            VisualPushChannel channel = VisualPushChannel.sse(APP);
            channel.tick(1);
            channel.eventOnAnotherInstance("order-43 paid");
            channel.useSharedBus();
            channel.eventOnAnotherInstance("order-44 paid");
            channel.report();
        });
        assertTrue(out.contains("FANOUT_MISS"), "the other instance cannot reach it, got:\n" + out);
        assertTrue(out.contains("BUS_ATTACHED"), "the bus must be added, got:\n" + out);
        assertTrue(out.contains("FANOUT_ROUTED"), "the bus must route it, got:\n" + out);
        assertTrue(out.contains("never delivered 1"), "exactly one was missed, got:\n" + out);
    }

    @Test
    void statelessPollingDoesNotCareWhichInstanceSawTheEvent() {
        String out = captureTrace(() -> {
            VisualPushChannel channel = VisualPushChannel.shortPolling(APP, 3);
            channel.eventOnAnotherInstance("order-45 paid");
            channel.tick(3);
            channel.report();
        });
        assertTrue(out.contains("FANOUT_ROUTED"), "any instance may answer, got:\n" + out);
        assertFalse(out.contains("FANOUT_MISS"), "there is no connection to miss, got:\n" + out);
        assertTrue(out.contains("POLL_DELIVERED"), "the next poll must find it, got:\n" + out);
    }

    @Test
    void theComparisonPricesTheSameTimelineFourWays() {
        String out = captureTrace(VisualPushChannel::compare);
        assertTrue(out.contains("TRANSPORT_COMPARED"), "expected the comparison, got:\n" + out);
        assertTrue(out.contains("\"transport\":\"SHORT_POLL\",\"requests\":4,\"empty\":2"),
                "short polling pays four requests, two of them empty, got:\n" + out);
        assertTrue(out.contains("\"transport\":\"LONG_POLL\",\"requests\":5,\"empty\":1"),
                "long polling pays five requests, one empty, got:\n" + out);
        assertTrue(out.contains("\"transport\":\"SSE\",\"requests\":1,\"empty\":0"),
                "SSE pays one request, got:\n" + out);
        assertTrue(out.contains("\"transport\":\"WEBSOCKET\",\"requests\":1,\"empty\":0"),
                "a WebSocket pays one handshake, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualPushChannel polling = VisualPushChannel.shortPolling(APP, 3);
            polling.tick(3);
            polling.serverEvent("order-42 paid");
            polling.tick(3);
            polling.report();

            VisualPushChannel held = VisualPushChannel.longPolling(APP, 4);
            held.tick(5);
            held.serverEvent("order-42 packed");
            held.report();

            VisualPushChannel stream = VisualPushChannel.sse(APP);
            stream.serverEvent("order-42 shipped");
            stream.goOffline();
            stream.serverEvent("order-42 delivered");
            stream.goOnline();
            stream.sendFromClient("mark as read");
            stream.eventOnAnotherInstance("order-43 paid");
            stream.useSharedBus();
            stream.eventOnAnotherInstance("order-44 paid");
            stream.report();

            VisualPushChannel socket = VisualPushChannel.webSocket("chat");
            socket.serverEvent("ada: hello");
            socket.sendFromClient("omar: hi");
            socket.goOffline();
            socket.serverEvent("ada: still there?");
            socket.goOnline();
            socket.report();

            VisualPushChannel.compare();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
