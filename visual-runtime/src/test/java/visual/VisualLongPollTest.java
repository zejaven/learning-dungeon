package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualLongPollTest {

    private static final String ENDPOINT = "GET /api/messages";

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
    void theEndpointStyleIsAnnouncedBeforeAnythingHappens() {
        String out = captureTrace(() -> VisualLongPoll.immediate(ENDPOINT, 4));
        assertTrue(out.contains("ENDPOINT_OPENED"), "expected the endpoint style, got:\n" + out);
        assertTrue(out.contains("\"style\":\"IMMEDIATE\""), "the style must be in the state, got:\n" + out);
    }

    @Test
    void aRegularRequestIsAnsweredEvenWhenThereIsNothingToSay() {
        String out = captureTrace(() -> {
            VisualLongPoll server = VisualLongPoll.immediate(ENDPOINT, 4);
            server.poll("tab-1");
            server.tick(3);
            server.serverEvent("msg-1");
            server.tick(3);
            server.poll("tab-1");
            server.report();
        });
        assertTrue(out.contains("REQUEST_ARRIVED"), "the request must arrive, got:\n" + out);
        assertTrue(out.contains("ANSWERED_EMPTY"), "an empty answer is still an answer, got:\n" + out);
        assertTrue(out.contains("NO_ONE_LISTENING"), "the event has nowhere to go, got:\n" + out);
        assertTrue(out.contains("ANSWERED_NOW"), "the next request must pick it up, got:\n" + out);
        assertTrue(out.contains("average delay 3.0 tick(s)"),
                "the news waited for the next request, got:\n" + out);
    }

    @Test
    void aHeldRequestIsAnsweredTheInstantTheEventExists() {
        String out = captureTrace(() -> {
            VisualLongPoll server = VisualLongPoll.held(ENDPOINT, 4, 10);
            server.poll("tab-1");
            server.tick(3);
            server.serverEvent("msg-1");
            server.report();
        });
        assertTrue(out.contains("REQUEST_PARKED"), "the request must be parked, got:\n" + out);
        assertTrue(out.contains("PARKED_COMPLETED"), "it must answer on the event, got:\n" + out);
        assertFalse(out.contains("ANSWERED_EMPTY"), "nothing was answered empty, got:\n" + out);
        assertTrue(out.contains("average delay 0.0 tick(s)"), "delivery is immediate, got:\n" + out);
    }

    @Test
    void anIdleHoldExpiresOnPurposeBeforeSomebodyElseKillsIt() {
        String out = captureTrace(() -> {
            VisualLongPoll server = VisualLongPoll.held(ENDPOINT, 4, 5);
            server.poll("tab-1");
            server.tick(5);
            server.poll("tab-1");
            server.report();
        });
        assertTrue(out.contains("HOLD_EXPIRED"), "the hold must expire, got:\n" + out);
        assertTrue(out.contains("requests 2 (of which 1 came back with nothing)"),
                "one empty answer over two requests, got:\n" + out);
    }

    @Test
    void aBlockingHandlerSpendsAThreadOnEveryWaitingClient() {
        String out = captureTrace(() -> {
            VisualLongPoll server = VisualLongPoll.held(ENDPOINT, 2, 10);
            server.poll("tab-1");
            server.poll("tab-2");
            server.poll("tab-3");
            server.report();
        });
        assertTrue(out.contains("POOL_EXHAUSTED"), "the pool must run out, got:\n" + out);
        assertTrue(out.contains("requests refused by a full pool 1"),
                "the third request found no worker, got:\n" + out);
        assertTrue(out.contains("\"busy\":2"), "both workers are parked, got:\n" + out);
    }

    @Test
    void anAsyncHandlerParksTheRequestWithoutKeepingTheThread() {
        String out = captureTrace(() -> {
            VisualLongPoll server = VisualLongPoll.heldAsync(ENDPOINT, 2, 10);
            server.poll("tab-1");
            server.poll("tab-2");
            server.poll("tab-3");
            server.poll("tab-4");
            server.report();
        });
        assertTrue(out.contains("WORKER_RELEASED"), "the thread must go back, got:\n" + out);
        assertFalse(out.contains("POOL_EXHAUSTED"), "two workers can hold four clients, got:\n" + out);
        assertTrue(out.contains("peak parked requests 4"), "all four stay open, got:\n" + out);
        assertTrue(out.contains("\"busy\":0"), "no worker is occupied, got:\n" + out);
    }

    @Test
    void aCursorSurvivesTheGapBetweenTwoLongPolls() {
        String out = captureTrace(() -> {
            VisualLongPoll server = VisualLongPoll.held(ENDPOINT, 4, 10);
            server.poll("tab-1");
            server.tick(2);
            server.serverEvent("msg-1");
            server.serverEvent("msg-2");
            server.poll("tab-1");
            server.report();
        });
        assertTrue(out.contains("NO_ONE_LISTENING"), "the second event lands in the gap, got:\n" + out);
        assertTrue(out.contains("POLL_CATCHES_UP"), "the cursor must recover it, got:\n" + out);
        assertTrue(out.contains("events delivered 2"), "both events arrive, got:\n" + out);
        assertTrue(out.contains("never delivered 0"), "nothing may be lost, got:\n" + out);
    }

    @Test
    void withoutACursorWhateverLandsInTheGapIsLost() {
        String out = captureTrace(() -> {
            VisualLongPoll server = VisualLongPoll.held(ENDPOINT, 4, 10).withoutCursor();
            server.poll("tab-1");
            server.tick(2);
            server.serverEvent("msg-1");
            server.serverEvent("msg-2");
            server.poll("tab-1");
            server.report();
        });
        assertTrue(out.contains("CURSOR_DROPPED"), "the cursor must be dropped, got:\n" + out);
        assertTrue(out.contains("EVENT_MISSED"), "the gap must swallow an event, got:\n" + out);
        assertFalse(out.contains("POLL_CATCHES_UP"), "there is no cursor to catch up with, got:\n" + out);
        assertTrue(out.contains("never delivered 1"), "exactly one was lost, got:\n" + out);
    }

    @Test
    void theComparisonPricesTheSameTimelineThreeWays() {
        String out = captureTrace(VisualLongPoll::compare);
        assertTrue(out.contains("STYLES_COMPARED"), "expected the comparison, got:\n" + out);
        assertTrue(out.contains("\"style\":\"IMMEDIATE\",\"requests\":4,\"empty\":2"),
                "the regular endpoint pays four requests, two empty, got:\n" + out);
        assertTrue(out.contains("\"style\":\"HELD_BLOCKING\",\"requests\":5,\"empty\":1"),
                "long polling pays five requests, one empty, got:\n" + out);
        assertTrue(out.contains("\"avgLatency\":2.0,\"maxLatency\":3,\"workerTicks\":4"),
                "the regular endpoint is late but cheap, got:\n" + out);
        assertTrue(out.contains("\"avgLatency\":0.0,\"maxLatency\":0,\"workerTicks\":29"),
                "the blocking hold delivers instantly and burns worker time, got:\n" + out);
        assertTrue(out.contains("\"avgLatency\":0.0,\"maxLatency\":0,\"workerTicks\":5"),
                "the async hold delivers instantly for almost no worker time, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualLongPoll regular = VisualLongPoll.immediate(ENDPOINT, 4);
            regular.poll("tab-1");
            regular.tick(2);
            regular.serverEvent("msg-1");
            regular.poll("tab-1");
            regular.report();

            VisualLongPoll blocking = VisualLongPoll.held(ENDPOINT, 2, 4);
            blocking.poll("tab-1");
            blocking.poll("tab-2");
            blocking.poll("tab-3");
            blocking.tick(4);
            blocking.serverEvent("msg-2");
            blocking.report();

            VisualLongPoll async = VisualLongPoll.heldAsync(ENDPOINT, 2, 6);
            async.poll("tab-1");
            async.tick(1);
            async.serverEvent("msg-3");
            async.report();

            VisualLongPoll naive = VisualLongPoll.held(ENDPOINT, 4, 8).withoutCursor();
            naive.poll("tab-1");
            naive.serverEvent("msg-4");
            naive.serverEvent("msg-5");
            naive.poll("tab-1");
            naive.report();

            VisualLongPoll.compare();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
