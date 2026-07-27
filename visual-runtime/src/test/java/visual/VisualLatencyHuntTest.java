package visual;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualLatencyHuntTest {

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

    private static VisualLatencyHunt hunt() {
        return VisualLatencyHunt.reported("shop.example.com", "the product page", "the site is slow");
    }

    @Test
    void aComplaintStartsWithNoNumbersAtAll() {
        String out = captureTrace(VisualLatencyHuntTest::hunt);
        assertTrue(out.contains("SLOWNESS_REPORTED"), "expected the opening event, got:\n" + out);
        assertTrue(out.contains("\"stage\":\"reported\""), "the hunt starts unscoped, got:\n" + out);
        assertTrue(out.contains("\"endToEndMs\":0"), "nothing is measured yet, got:\n" + out);
        assertTrue(out.contains("\"hotspot\":null"), "no hotspot before a split, got:\n" + out);
    }

    @Test
    void optimizingBeforeMeasuringIsRecordedAsAMisstepThatCostsTime() {
        String out = captureTrace(() -> {
            VisualLatencyHunt hunt = hunt();
            hunt.guess("rewrite the product mapper with streams");
            hunt.review();
        });
        assertTrue(out.contains("GUESS_MADE"), "expected the guess event, got:\n" + out);
        assertTrue(out.contains("optimized-on-a-hunch"), "it must be recorded as a misstep, got:\n" + out);
        assertTrue(out.contains("T+120m"), "the wasted time must be charged, got:\n" + out);
    }

    @Test
    void scopingTurnsTheComplaintIntoFactsAndABudget() {
        String out = captureTrace(() -> {
            VisualLatencyHunt hunt = hunt();
            hunt.clarify("which page?", "GET /products/{id}");
            hunt.target("the product page at p95", 800);
        });
        assertTrue(out.contains("SCOPE_NARROWED"), "expected the clarifying event, got:\n" + out);
        assertTrue(out.contains("BUDGET_SET"), "expected the budget event, got:\n" + out);
        assertTrue(out.contains("\"budgetMs\":800"), "the budget must be in the state, got:\n" + out);
        assertTrue(out.contains("\"stage\":\"scoped\""), "the hunt must be scoped, got:\n" + out);
    }

    @Test
    void aFatTailIsReportedAsTheAverageDescribingNobody() {
        String wide = captureTrace(() -> hunt().distribution(900, 400, 3100, 7200));
        assertTrue(wide.contains("PERCENTILES_READ"), "expected the distribution event, got:\n" + wide);
        assertTrue(wide.contains("\"p99\":7200"), "the tail must be in the state, got:\n" + wide);
        assertTrue(wide.contains("describing nobody"), "a fat tail must be called out, got:\n" + wide);

        String tight = captureTrace(() -> hunt().distribution(410, 400, 480, 520));
        assertTrue(tight.contains("tight distribution"), "a tight one must not, got:\n" + tight);
    }

    @Test
    void aMissingMeasurementIsItselfTheFinding() {
        String out = captureTrace(() -> hunt().missingSignal("per-request timings on the server"));
        assertTrue(out.contains("SIGNAL_MISSING"), "expected the blind-spot event, got:\n" + out);
        assertTrue(out.contains("no-measurement"), "it must be a misstep, got:\n" + out);
    }

    @Test
    void segmentsAddUpAndTheSplitNamesTheBiggestSlice() {
        String out = captureTrace(() -> {
            VisualLatencyHunt hunt = hunt();
            hunt.measure("DNS + connect + TLS", 90, "the network panel");
            hunt.measure("waiting for the first byte", 2400, "the network panel");
            hunt.measure("downloading the HTML", 110, "the network panel");
            hunt.split();
        });
        assertTrue(out.contains("SEGMENT_MEASURED"), "expected the measurement event, got:\n" + out);
        assertTrue(out.contains("TIME_SPLIT"), "expected the split event, got:\n" + out);
        assertTrue(out.contains("\"endToEndMs\":2600"), "the segments must add up, got:\n" + out);
        assertTrue(out.contains("\"hotspot\":\"waiting for the first byte\""),
                "the biggest slice must be named, got:\n" + out);
        assertTrue(out.contains("holds 2400ms = 92%"), "its share must be computed, got:\n" + out);
        assertTrue(out.contains("\"stage\":\"localized\""), "a named hotspot localizes the hunt, got:\n" + out);
    }

    @Test
    void aClearedSegmentCannotBeTheHotspotAnyMore() {
        String out = captureTrace(() -> {
            VisualLatencyHunt hunt = hunt();
            hunt.measure("third-party analytics script", 700, "the network panel");
            hunt.measure("waiting for the first byte", 400, "the network panel");
            hunt.ruleOut("third-party analytics script", "it loads async, after the page is interactive");
            hunt.split();
        });
        assertTrue(out.contains("SEGMENT_CLEARED"), "expected the elimination event, got:\n" + out);
        assertTrue(out.contains("\"status\":\"cleared\""), "the segment must be closed, got:\n" + out);
        assertTrue(out.contains("\"hotspot\":\"waiting for the first byte\""),
                "a cleared segment must not win the split, got:\n" + out);
    }

    @Test
    void drillingIntoTheHotspotOpensANewLevel() {
        String out = captureTrace(() -> {
            VisualLatencyHunt hunt = hunt();
            hunt.measure("waiting for the first byte", 2400, "the network panel");
            hunt.split();
            hunt.drillInto("waiting for the first byte", "the request trace");
            hunt.measure("the product query", 2100, "the trace");
            hunt.split();
        });
        assertTrue(out.contains("DRILL_DOWN"), "expected the drill-down event, got:\n" + out);
        assertTrue(out.contains("\"status\":\"drilled\""), "the parent slice must be marked, got:\n" + out);
        assertTrue(out.contains("\"name\":\"waiting for the first byte\",\"totalMs\":2100"),
                "a level named after the slice must exist, got:\n" + out);
        assertFalse(out.contains("chased-a-small-slice"), "the hotspot was the one drilled, got:\n" + out);
    }

    @Test
    void drillingIntoASmallSliceIsRecorded() {
        String out = captureTrace(() -> {
            VisualLatencyHunt hunt = hunt();
            hunt.measure("waiting for the first byte", 2400, "the network panel");
            hunt.measure("the JS bundle", 180, "the network panel");
            hunt.drillInto("the JS bundle", "the coverage tab");
            hunt.review();
        });
        assertTrue(out.contains("chased-a-small-slice"), "the wrong drill must be flagged, got:\n" + out);
    }

    @Test
    void drillingIntoSomethingNeverMeasuredIsRecorded() {
        String out = captureTrace(() -> {
            VisualLatencyHunt hunt = hunt();
            hunt.drillInto("the database", "a profiler");
            hunt.review();
        });
        assertTrue(out.contains("drilled-before-measuring"), "the missing measurement must be flagged, got:\n" + out);
    }

    @Test
    void comparingIdleWithPeakSeparatesSlowWorkFromALongQueue() {
        String queue = captureTrace(() -> hunt().underLoad(180, 2600));
        assertTrue(queue.contains("LOAD_COMPARED"), "expected the load event, got:\n" + queue);
        assertTrue(queue.contains("\"queueing\":true"), "peak >> idle means a queue, got:\n" + queue);

        String work = captureTrace(() -> hunt().underLoad(2400, 2600));
        assertTrue(work.contains("\"queueing\":false"), "slow when idle means slow work, got:\n" + work);
    }

    @Test
    void aSaturatedResourceIsMarked() {
        String out = captureTrace(() -> {
            VisualLatencyHunt hunt = hunt();
            hunt.resource("the JDBC connection pool", "10/10 in use, 34 waiting", true);
            hunt.resource("CPU", "31% across 3 instances", false);
        });
        assertTrue(out.contains("RESOURCE_CHECKED"), "expected the resource event, got:\n" + out);
        assertTrue(out.contains("\"saturated\":true"), "the exhausted pool must be marked, got:\n" + out);
        assertTrue(out.contains("\"saturated\":false"), "the idle CPU must not, got:\n" + out);
    }

    @Test
    void theCeilingOfAnOptimizationIsTheSizeOfItsSlice() {
        String out = captureTrace(() -> {
            VisualLatencyHunt hunt = hunt();
            hunt.measure("template rendering", 100, "the trace");
            hunt.measure("the product query", 1900, "the trace");
            hunt.ceiling("rewrite the template engine", "template rendering", 4);
            hunt.ceiling("add the missing index", "the product query", 10);
        });
        assertTrue(out.contains("CEILING_COMPUTED"), "expected the pricing event, got:\n" + out);
        // 100ms 4x faster saves 75ms of a 2000ms request = 3%.
        assertTrue(out.contains("\"savedMs\":75") && out.contains("\"gainPercent\":3"),
                "Amdahl must be computed, got:\n" + out);
        assertTrue(out.contains("\"worthIt\":false"), "3% is invisible, got:\n" + out);
        // 1900ms 10x faster saves 1710ms of 2000ms = 85%.
        assertTrue(out.contains("\"savedMs\":1710") && out.contains("\"worthIt\":true"),
                "the big slice must be worth it, got:\n" + out);
    }

    @Test
    void changingAnythingWithoutAConfirmedCauseIsBlind() {
        String out = captureTrace(() -> {
            VisualLatencyHunt hunt = hunt();
            hunt.fix("add a cache in front of the product service", 500);
            hunt.review();
        });
        assertTrue(out.contains("BLIND_OPTIMIZATION"), "expected the blind-change event, got:\n" + out);
        assertTrue(out.contains("changed-without-a-cause"), "it must be a misstep, got:\n" + out);
        assertFalse(out.contains("\"event\":\"FIX_APPLIED\""), "an unconfirmed change is not a fix, got:\n" + out);
        assertTrue(out.contains("\"blind\":true"), "the state must say so, got:\n" + out);
    }

    @Test
    void aFixOnAConfirmedCauseIsVerifiedByTheSameMeasurement() {
        String out = captureTrace(() -> {
            VisualLatencyHunt hunt = hunt();
            hunt.target("the product page at p95", 800);
            hunt.measure("waiting for the first byte", 2400, "the network panel");
            hunt.measure("everything else", 200, "the network panel");
            hunt.confirm("the product query has no index on category_id",
                    "EXPLAIN shows a sequential scan of 2.1M rows");
            hunt.fix("add an index on category_id", 2000);
            hunt.remeasure(600);
        });
        assertTrue(out.contains("CAUSE_CONFIRMED"), "expected the cause event, got:\n" + out);
        assertTrue(out.contains("\"event\":\"FIX_APPLIED\""), "expected the fix event, got:\n" + out);
        assertTrue(out.contains("IMPROVEMENT_VERIFIED"), "expected the verification event, got:\n" + out);
        assertTrue(out.contains("\"beforeMs\":2600") && out.contains("\"afterMs\":600"),
                "before and after must both be recorded, got:\n" + out);
        assertTrue(out.contains("\"metBudget\":true"), "600ms is inside the 800ms budget, got:\n" + out);
        assertTrue(out.contains("\"stage\":\"verified\""), "a measured win closes the hunt, got:\n" + out);
    }

    @Test
    void aChangeThatMovesNothingIsReportedAsNoise() {
        String out = captureTrace(() -> {
            VisualLatencyHunt hunt = hunt();
            hunt.measure("waiting for the first byte", 2400, "the network panel");
            hunt.measure("the JS bundle", 200, "the network panel");
            hunt.confirm("the JS bundle blocks rendering", "the coverage tab shows 80% unused");
            hunt.fix("split the bundle", 150);
            hunt.remeasure(2520);
        });
        assertTrue(out.contains("NO_IMPROVEMENT"), "expected the no-gain event, got:\n" + out);
        assertTrue(out.contains("no-measurable-gain"), "it must be a misstep, got:\n" + out);
        assertTrue(out.contains("\"stage\":\"unchanged\""), "the stage must say so, got:\n" + out);
        assertFalse(out.contains("IMPROVEMENT_VERIFIED"), "3% is not an improvement, got:\n" + out);
    }

    @Test
    void aFixThatIsNeverRemeasuredIsFlaggedAtReview() {
        String out = captureTrace(() -> {
            VisualLatencyHunt hunt = hunt();
            hunt.confirm("N+1 queries on the reviews list", "the trace shows 213 identical statements");
            hunt.fix("fetch the reviews in one query", 900);
            hunt.review();
        });
        assertTrue(out.contains("never-remeasured"), "an unverified fix must be flagged, got:\n" + out);
    }

    @Test
    void guardsAreThePermanentOutputOfTheHunt() {
        String out = captureTrace(() -> {
            VisualLatencyHunt hunt = hunt();
            hunt.guard("a p95 graph for the product page");
            hunt.guard("an alert when p95 crosses the 800ms budget");
        });
        long added = out.lines().filter(line -> line.contains("MONITORING_ADDED")).count();
        assertEquals(2, added, "both follow-ups must be recorded, got:\n" + out);
    }

    @Test
    void aCleanHuntEndsVerifiedWithNoMissteps() {
        String out = captureTrace(() -> {
            VisualLatencyHunt hunt = hunt();
            hunt.clarify("which page?", "GET /products/{id}");
            hunt.target("the product page at p95", 800);
            hunt.distribution(1400, 900, 3100, 5200);
            hunt.measure("DNS + connect + TLS", 90, "the network panel");
            hunt.measure("waiting for the first byte", 2400, "the network panel");
            hunt.measure("downloading + rendering", 110, "the network panel");
            hunt.split();
            hunt.drillInto("waiting for the first byte", "the request trace");
            hunt.measure("the product query", 2100, "the trace");
            hunt.measure("the rest of the handler", 300, "the trace");
            hunt.split();
            hunt.ceiling("add an index on category_id", "the product query", 20);
            hunt.confirm("a sequential scan on products.category_id",
                    "EXPLAIN scans 2.1M rows for one category");
            hunt.fix("add an index on category_id", 2000);
            hunt.remeasure(560);
            hunt.guard("an alert when p95 crosses the 800ms budget");
            hunt.review();
        });
        assertTrue(out.contains("no missteps"), "a clean hunt has none, got:\n" + out);
        assertTrue(out.contains("\"stage\":\"verified\""), "it must end verified, got:\n" + out);
        assertTrue(out.contains("HUNT_REVIEW"), "expected the review event, got:\n" + out);
    }

    @Test
    void everyTraceLineIsPrefixed() {
        String out = captureTrace(() -> {
            VisualLatencyHunt hunt = hunt();
            hunt.guess("rewrite the mapper");
            hunt.clarify("which page?", "GET /products/{id}");
            hunt.target("the product page at p95", 800);
            hunt.distribution(1400, 900, 3100, 5200);
            hunt.missingSignal("per-request timings on the server");
            hunt.measure("waiting for the first byte", 2400, "the network panel");
            hunt.measure("the JS bundle", 200, "the network panel");
            hunt.ruleOut("the JS bundle", "it is cached and parses in 40ms");
            hunt.split();
            hunt.drillInto("waiting for the first byte", "the request trace");
            hunt.measure("the product query", 2100, "the trace");
            hunt.split();
            hunt.underLoad(2300, 2600);
            hunt.resource("CPU", "31% across 3 instances", false);
            hunt.ceiling("add an index on category_id", "the product query", 20);
            hunt.confirm("a sequential scan on products.category_id", "EXPLAIN scans 2.1M rows");
            hunt.fix("add an index on category_id", 2000);
            hunt.remeasure(560);
            hunt.guard("a p95 graph for the product page");
            hunt.review();
        });
        out.lines().forEach(line -> {
            if (!line.isEmpty()) {
                assertTrue(line.startsWith(Trace.PREFIX), "unexpected non-trace line: " + line);
            }
        });
    }
}
