package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualRateFeedTest {

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
    void creatingTheServiceEmitsReady() {
        String out = captureTrace(VisualRateFeed::withEventCarriedState);
        assertTrue(out.contains("FEED_READY"), "expected a creation event, got:\n" + out);
    }

    @Test
    void aSynchronousCallIsAlwaysCurrentAndAlwaysPaidFor() {
        String out = captureTrace(() -> {
            VisualRateFeed feed = VisualRateFeed.callingOnEveryDecision();
            feed.publishRate("EUR/USD", 10800);
            feed.price("EUR/USD", 20000);
            feed.price("EUR/USD", 20000);
            feed.report();
        });
        assertTrue(out.contains("SYNC_CALL"), "expected the blocking call, got:\n" + out);
        assertTrue(out.contains("216.00 USD"), "200.00 EUR at 1.0800 must be 216.00 USD, got:\n" + out);
        assertTrue(out.contains("blocking calls inside a decision: 2"),
                "every decision must pay for its own call, got:\n" + out);
    }

    @Test
    void aSynchronousCallTurnsTheOwnersOutageIntoYourOwn() {
        String out = captureTrace(() -> {
            VisualRateFeed feed = VisualRateFeed.callingOnEveryDecision();
            feed.publishRate("EUR/USD", 10800);
            feed.rateServiceDown();
            boolean priced = feed.price("EUR/USD", 20000);
            assertFalse(priced, "an unreachable owner and no local copy cannot produce a quote");
            feed.report();
        });
        assertTrue(out.contains("SYNC_CALL_FAILED"), "expected the failed call, got:\n" + out);
        assertTrue(out.contains("DECISION_BLOCKED"), "the decision must be refused, got:\n" + out);
    }

    @Test
    void aCachedCopyServesTheDecisionUntilItsTtlExpires() {
        String out = captureTrace(() -> {
            VisualRateFeed feed = VisualRateFeed.cachingLocally(60);
            feed.publishRate("EUR/USD", 10800);
            feed.price("EUR/USD", 20000);
            feed.advanceSeconds(30);
            feed.price("EUR/USD", 20000);
            feed.advanceSeconds(40);
            feed.price("EUR/USD", 20000);
            feed.report();
        });
        assertTrue(out.contains("CACHE_MISS"), "the first decision must miss, got:\n" + out);
        assertTrue(out.contains("CACHE_HIT"), "the second must be served locally, got:\n" + out);
        assertTrue(out.contains("CACHE_EXPIRED"), "the third must find the copy expired, got:\n" + out);
        assertTrue(out.contains("blocking calls inside a decision: 2"),
                "only the miss and the expiry may call, got:\n" + out);
    }

    @Test
    void aCachedCopyKeepsPricingAfterTheOwnerIsGoneAndSaysSo() {
        String out = captureTrace(() -> {
            VisualRateFeed feed = VisualRateFeed.cachingLocally(60);
            feed.publishRate("EUR/USD", 10800);
            feed.price("EUR/USD", 20000);
            feed.rateServiceDown();
            feed.advanceSeconds(90);
            boolean priced = feed.price("EUR/USD", 20000);
            assertTrue(priced, "an expired copy is still served by default");
            feed.report();
        });
        assertTrue(out.contains("SYNC_CALL_FAILED"), "the refresh must fail, got:\n" + out);
        assertTrue(out.contains("STALE_RATE_USED"), "the expired copy must be flagged, got:\n" + out);
        assertTrue(out.contains("on data flagged stale"), "the audit must count it, got:\n" + out);
    }

    @Test
    void aDeclaredLimitTurnsAStaleReadIntoARefusal() {
        String out = captureTrace(() -> {
            VisualRateFeed feed = VisualRateFeed.cachingLocally(60);
            feed.publishRate("EUR/USD", 10800);
            feed.price("EUR/USD", 20000);
            feed.rateServiceDown();
            feed.advanceSeconds(300);
            feed.refuseStaleAfter(120);
            boolean priced = feed.price("EUR/USD", 20000);
            assertFalse(priced, "a value past the declared limit must not price money");
            feed.report();
        });
        assertTrue(out.contains("FRESHNESS_POLICY_SET"), "expected the policy step, got:\n" + out);
        assertTrue(out.contains("DECISION_BLOCKED"), "the decision must fail closed, got:\n" + out);
    }

    @Test
    void eventCarriedStateDecidesWithoutAnyCall() {
        String out = captureTrace(() -> {
            VisualRateFeed feed = VisualRateFeed.withEventCarriedState();
            feed.publishRate("EUR/USD", 10800);
            feed.price("EUR/USD", 20000);
            feed.advanceSeconds(15);
            feed.publishRate("EUR/USD", 10950);
            feed.price("EUR/USD", 20000);
            feed.report();
        });
        assertTrue(out.contains("RATE_APPLIED"), "the event must reach the replica, got:\n" + out);
        assertTrue(out.contains("219.00 USD"), "200.00 EUR at 1.0950 must be 219.00 USD, got:\n" + out);
        assertTrue(out.contains("blocking calls inside a decision: 0"),
                "a local read must make no call, got:\n" + out);
    }

    @Test
    void anOlderEventDeliveredLateIsDroppedAndADuplicateChangesNothing() {
        String out = captureTrace(() -> {
            VisualRateFeed feed = VisualRateFeed.withEventCarriedState();
            feed.publishRate("EUR/USD", 10800);
            feed.advanceSeconds(10);
            feed.publishRate("EUR/USD", 10950);
            feed.redeliver("EUR/USD", 1);
            feed.redeliver("EUR/USD", 2);
            feed.price("EUR/USD", 20000);
            feed.report();
        });
        assertTrue(out.contains("STALE_EVENT_IGNORED"), "expected the version check, got:\n" + out);
        assertTrue(out.contains("219.00 USD"),
                "the newer rate must survive both redeliveries, got:\n" + out);
        assertTrue(out.contains("events ignored: 2"), "both redeliveries are no-ops, got:\n" + out);
    }

    @Test
    void aStoppedFeedKeepsEveryDecisionSucceedingOnAnOldRate() {
        String out = captureTrace(() -> {
            VisualRateFeed feed = VisualRateFeed.withEventCarriedState();
            feed.publishRate("EUR/USD", 10800);
            feed.feedStops();
            feed.publishRate("EUR/USD", 11500);
            feed.advanceSeconds(1800);
            boolean priced = feed.price("EUR/USD", 20000);
            assertTrue(priced, "nothing fails: the replica simply stopped changing");
            feed.report();
        });
        assertTrue(out.contains("FEED_STOPPED"), "expected the stopped feed, got:\n" + out);
        assertTrue(out.contains("PRICE_QUOTED"), "the decision must still succeed, got:\n" + out);
        assertTrue(out.contains("216.00 USD"), "it must price on the old rate, got:\n" + out);
        assertTrue(out.contains("was 1800s old"), "the audit must expose the age, got:\n" + out);
    }

    @Test
    void resumingTheFeedDeliversTheBacklogInOrder() {
        String out = captureTrace(() -> {
            VisualRateFeed feed = VisualRateFeed.withEventCarriedState();
            feed.publishRate("EUR/USD", 10800);
            feed.feedStops();
            feed.publishRate("EUR/USD", 11500);
            feed.advanceSeconds(60);
            feed.feedResumes();
            feed.price("EUR/USD", 20000);
        });
        assertTrue(out.contains("FEED_RESUMED"), "expected the resumed feed, got:\n" + out);
        assertTrue(out.contains("230.00 USD"),
                "the backlog must be applied before the next decision, got:\n" + out);
    }

    @Test
    void anInMemoryReplicaIsEmptyAfterARestartAndIsRebuiltFromTheLog() {
        String out = captureTrace(() -> {
            VisualRateFeed feed = VisualRateFeed.withEventCarriedState();
            feed.publishRate("EUR/USD", 10800);
            feed.price("EUR/USD", 20000);
            feed.restartInstance();
            boolean cold = feed.price("EUR/USD", 20000);
            assertFalse(cold, "a cold replica has nothing to decide with");
            feed.rebuildFromSnapshot();
            boolean warm = feed.price("EUR/USD", 20000);
            assertTrue(warm, "a rebuilt replica prices again");
            feed.report();
        });
        assertTrue(out.contains("COLD_START"), "expected the restart, got:\n" + out);
        assertTrue(out.contains("DECISION_BLOCKED"), "a cold replica must refuse, got:\n" + out);
        assertTrue(out.contains("REPLICA_REBUILT"), "expected the replay, got:\n" + out);
    }

    @Test
    void aRebuildWorksWhileTheOwningServiceIsDown() {
        String out = captureTrace(() -> {
            VisualRateFeed feed = VisualRateFeed.withEventCarriedState();
            feed.publishRate("EUR/USD", 10800);
            feed.restartInstance();
            feed.rateServiceDown();
            feed.rebuildFromSnapshot();
            boolean priced = feed.price("EUR/USD", 20000);
            assertTrue(priced, "the log is readable even when the owner is not");
        });
        assertTrue(out.contains("REPLICA_REBUILT"), "expected the replay, got:\n" + out);
        assertTrue(out.contains("PRICE_QUOTED"), "the decision must succeed, got:\n" + out);
    }

    @Test
    void everyQuotePinsTheRateItWasMadeWith() {
        String out = captureTrace(() -> {
            VisualRateFeed feed = VisualRateFeed.withEventCarriedState();
            feed.publishRate("EUR/USD", 10800);
            feed.price("EUR/USD", 20000);
            feed.publishRate("EUR/USD", 11500);
            feed.price("EUR/USD", 20000);
        });
        assertTrue(out.contains("\"totalText\":\"216.00\""),
                "the first quote must keep the rate it was priced at, got:\n" + out);
        assertTrue(out.contains("\"totalText\":\"230.00\""),
                "the second quote must use the new rate, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualRateFeed feed = VisualRateFeed.withEventCarriedState();
            feed.publishRate("EUR/USD", 10800);
            feed.price("EUR/USD", 20000);
            feed.flagStaleAfter(60);
            feed.feedStops();
            feed.advanceSeconds(120);
            feed.price("EUR/USD", 20000);
            feed.feedResumes();
            feed.redeliver("EUR/USD", 1);
            feed.restartInstance();
            feed.rebuildFromSnapshot();
            feed.rateServiceDown();
            feed.rateServiceUp();
            feed.report();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
